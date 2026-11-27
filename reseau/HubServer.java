package reseau;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Serveur central du salon et de ses duels.
 *
 * Il tient les trois collections principales : les joueurs actuellement connectés,
 * les défis proposés et les parties acceptées. Une section synchronized protège
 * ces structures contre les opérations concurrentes effectuées par les threads
 * d'écoute. Quand un défi est accepté, un jeton aléatoire devient le nom du canal
 * privé de partie : il évite qu'un joueur ne rejoigne une partie au hasard.
 */
public final class HubServer implements AutoCloseable {
    private final int port;
    // Un verrou unique protege l'etat partage, car chaque socket est lue par son propre thread.
    private final Object verrou = new Object();
    // Cle = pseudo normalise ; valeur = socket et etat de la session connectee.
    private final Map<String, Joueur> joueurs = new HashMap<>();
    // Les defis temporaires bloquent les participants jusqu'a acceptation, refus ou deconnexion.
    private final Map<String, Defi> defis = new HashMap<>();
    // Une partie est indexee par son jeton et contient les deux canaux joueurs.
    private final Map<String, Partie> parties = new HashMap<>();
    private volatile boolean actif = true;
    private ServerSocket serveur;

    public HubServer(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Le port doit être compris entre 1 et 65535.");
        }
        this.port = port;
    }

    public void demarrer() throws IOException {
        serveur = new ServerSocket(port); // Ouvre le port TCP demandé.
        System.out.println("Serveur de salon en écoute sur le port " + port + "."); // Indique que le serveur est prêt.
        /*
         * La boucle accepte uniquement de nouvelles sockets. Toute lecture et tout
         * protocole joueur sont ensuite déplacés dans un thread daemon distinct,
         * pour que plusieurs clients puissent attendre leurs commandes sans bloquer l'accueil.
         */
        while (actif) { // La socket d'ecoute reste ouverte jusqu'a close() ou erreur fatale.
            try {
                Socket socket = serveur.accept(); // Bloque jusqu'à l'arrivée d'un nouveau client.
                Thread worker = new Thread(() -> traiterConnexion(socket), "client-salon"); // Isole cette connexion des suivantes.
                worker.setDaemon(true); // Empêche ce thread d'être le seul à maintenir l'application ouverte.
                worker.start(); // Commence à lire le premier message du client.
            } catch (IOException exception) {
                if (actif) {
                    throw exception;
                }
            }
        }
    }

    private void traiterConnexion(Socket socket) {
        try {
            BufferedReader input = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8)); // Lit les commandes texte en UTF-8.
            PrintWriter output = new PrintWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8), true); // autoFlush envoie immédiatement chaque réponse.
            String premiereLigne = input.readLine(); // Le serveur identifie le type de connexion a partir de sa premiere ligne.
            if (premiereLigne == null) {
                socket.close();
                return;
            }
            if (premiereLigne.startsWith("REGISTER|")) { // Connexion à la liste des joueurs.
                traiterJoueur(socket, input, output, premiereLigne);
            } else if (premiereLigne.startsWith("JOIN|")) { // Connexion directe à un canal de partie existant.
                traiterCanalDePartie(socket, input, output, premiereLigne);
            } else {
                envoyer(output, "ERROR|" + encoder("Commande de connexion inconnue."));
                socket.close();
            }
        } catch (IOException exception) {
            if (actif) {
                System.err.println("Connexion réseau interrompue : " + exception.getMessage());
            }
        }
    }

    private void traiterJoueur(Socket socket, BufferedReader input, PrintWriter output,
                               String inscription) throws IOException {
        Joueur joueur;
        String pseudo;
        try {
            String[] champs = inscription.split("\\|", -1); // -1 conserve les champs vides éventuels.
            pseudo = champs.length == 2 ? decoder(champs[1]).trim() : ""; // Décode le pseudo après Base64.
            if (!pseudo.matches("[\\p{L}\\p{N}_-]{2,18}")) { // Limite la longueur et les caractères autorisés.
                throw new IllegalArgumentException(
                        "Le pseudo doit contenir 2 à 18 lettres, chiffres, tirets ou soulignés.");
            }
        } catch (IllegalArgumentException exception) {
            envoyer(output, "ERROR|" + encoder(exception.getMessage()));
            socket.close();
            return;
        }

        String cle = pseudo.toLowerCase(Locale.ROOT); // Rend l'identification insensible aux majuscules.
        joueur = new Joueur(pseudo, cle, socket, output); // Regroupe les données propres à cette session.
        // Le verrou transforme l'inscription en opération atomique : la même clé
        // ne peut être créée deux fois, et toutes les listes sont diffusées ensemble.
        synchronized (verrou) { // Evite que deux inscriptions concurrentes prennent le meme pseudo.
            if (joueurs.containsKey(cle)) { // Détecte un pseudo déjà inscrit, quelle que soit sa casse.
                envoyer(output, "ERROR|" + encoder("Ce pseudo est déjà utilisé."));
                socket.close();
                return;
            }
            joueurs.put(cle, joueur); // Rend le nouveau joueur visible globalement.
            envoyer(output, "WELCOME|" + encoder(pseudo)); // Confirme l'inscription au client.
            actualiserListes(); // Rafraîchit tous les joueurs après ce changement.
        }

        try {
            String commande;
            while ((commande = input.readLine()) != null) { // Attend une nouvelle commande du même joueur.
                traiterCommande(joueur, commande); // Traduit la commande avec le verrou global.
            }
        } finally {
            deconnecter(joueur);
        }
    }

    private void traiterCommande(Joueur joueur, String commande) {
        String[] champs = commande.split("\\|", -1); // Sépare sans perdre les paramètres présents.
        synchronized (verrou) { // Toutes les modifications d'état passent sous le même verrou.
            if (joueurs.get(joueur.cle) != joueur) { // Ignore un thread ancien si la session a deja ete retiree/remplacee.
                return;
            }
            switch (champs[0]) {
                case "LIST":
                    // La liste est renvoyee a tous pour que chaque client voie le meme etat.
                    envoyerListes();
                    break;
                case "CHALLENGE":
                    if (champs.length == 3) {
                        creerDefi(joueur, champs[1], champs[2]);
                    } else {
                        erreur(joueur, "La demande de défi est invalide.");
                    }
                    break;
                case "ACCEPT":
                    if (champs.length == 2) {
                        accepterDefi(joueur, champs[1]);
                    } else {
                        erreur(joueur, "La réponse au défi est invalide.");
                    }
                    break;
                case "DECLINE":
                    if (champs.length == 2) {
                        refuserDefi(joueur, champs[1]);
                    } else {
                        erreur(joueur, "La réponse au défi est invalide.");
                    }
                    break;
                default:
                    erreur(joueur, "Commande inconnue.");
            }
        }
    }

    private void creerDefi(Joueur expediteur, String pseudoEncode, String jeu) {
        if (!"chess".equals(jeu) && !"checkers".equals(jeu)) {
            erreur(expediteur, "Le jeu pas disponible.");
            return;
        }
        final String pseudoCible;
        try {
            pseudoCible = decoder(pseudoEncode);
        } catch (IllegalArgumentException exception) {
            erreur(expediteur, "Le pseudo du joueur ciblé est invalide.");
            return;
        }
        Joueur cible = joueurs.get(pseudoCible.toLowerCase(Locale.ROOT)); // La casse du pseudo ne change pas l'identite.
        if (cible == null || cible == expediteur) {
            erreur(expediteur, "Ce joueur n’est plus disponible.");
            return;
        }
        // Un joueur ne peut avoir qu'un engagement a la fois ; sinon les reponses
        // a des defis concurrents rendraient l'etat du salon ambigu.
        if (expediteur.partie != null || cible.partie != null
                || expediteur.defi != null || cible.defi != null) {
            erreur(expediteur, "Un des joueurs est déjà engagé dans une partie ou un défi.");
            return;
        }

        String id = UUID.randomUUID().toString(); // Identifiant unique impossible à deviner simplement.
        Defi defi = new Defi(id, expediteur, cible, jeu); // Création de l'objet défi atomique.
        expediteur.defi = id; // Marque l'expéditeur comme engagé.
        cible.defi = id; // Marque aussi la cible comme engagée.
        defis.put(id, defi); // Rend le défi accessible pour ACCEPT ou DECLINE.
        expediteur.envoyer("CHALLENGE_SENT|" + id + "|" + encoder(cible.pseudo));
        cible.envoyer("CHALLENGE|" + id + "|" + encoder(expediteur.pseudo) + "|" + jeu);
        actualiserListes();
    }

    private void accepterDefi(Joueur destinataire, String id) {
        Defi defi = defis.get(id);
        // Seule la cible du defi peut l'accepter, et une seule fois car le defi est ensuite retire.
        if (defi == null || defi.cible != destinataire) {
            erreur(destinataire, "Ce défi n’est plus disponible.");
            return;
        }
        if (joueurs.get(defi.expediteur.cle) != defi.expediteur) {
            // Le defi peut etre accepte juste apres la deconnexion de l'expediteur.
            defis.remove(id);
            libererDefi(defi);
            erreur(destinataire, "L’autre joueur s’est déconnecté.");
            return;
        }

        String token = UUID.randomUUID().toString(); // Jeton privé du canal de partie.
        Partie partie = new Partie(token, defi); // Initialise les deux emplacements de socket.
        // Un UUID empêche qu'un ancien client devine facilement l'identifiant
        // d'une partie encore vivante. Les deux joueurs y accèdent via ce canal privé.
        parties.put(token, partie); // Publie la partie avant que les clients tentent JOIN|token.
        defi.expediteur.partie = partie;
        destinataire.partie = partie;
        defis.remove(id);
        libererDefi(defi);
        defi.expediteur.envoyer("MATCH|" + token + "|" + defi.jeu + "|WHITE|" // L'expéditeur devient Blanc.
                + encoder(destinataire.pseudo));
        destinataire.envoyer("MATCH|" + token + "|" + defi.jeu + "|BLACK|" // La cible devient Noir.
                + encoder(defi.expediteur.pseudo));
        actualiserListes();
    }

    private void refuserDefi(Joueur destinataire, String id) {
        Defi defi = defis.get(id);
        if (defi == null || defi.cible != destinataire) {
            erreur(destinataire, "Ce défi n’est plus disponible.");
            return;
        }
        defis.remove(id);
        libererDefi(defi);
        defi.expediteur.envoyer("DECLINED|" + id + "|" + encoder(destinataire.pseudo));
        actualiserListes();
    }

    private void traiterCanalDePartie(Socket socket, BufferedReader input, PrintWriter output,
                                      String inscription) throws IOException {
        String[] champs = inscription.split("\\|", -1);
        Partie partie;
        synchronized (verrou) {
            partie = champs.length == 2 ? parties.get(champs[1]) : null; // Recherche la partie uniquement via son token.
        }
        // Le token seul ne suffit pas a la connexion : Partie limite aussi le canal a deux sockets.
        if (partie == null || !partie.ajouter(socket, output)) {
            envoyer(output, "ERROR|" + encoder("Cette partie n’est plus disponible."));
            socket.close();
            return;
        }

        boolean deuxJoueursConnectes;
        synchronized (partie) {
            deuxJoueursConnectes = partie.sockets[0] != null && partie.sockets[1] != null;
            if (deuxJoueursConnectes && !partie.demarrageEnvoye) {
                // CountDownLatch évite une course : les deux clients ne passent READY
                // qu'après avoir tous les deux enregistré leur socket dans Partie.
                partie.demarrageEnvoye = true;
                partie.envoyer(0, "READY|");
                partie.envoyer(1, "READY|");
                partie.pret.countDown();
            }
        }
        try {
            // Chaque joueur garde un thread de lecture dedie. Les coups recus sont
            // retransmis tels quels : le serveur ne valide pas les regles du jeu.
            if (!partie.pret.await(30, TimeUnit.SECONDS)) { // N'attend pas indefiniment un adversaire qui n'arrive pas.
                envoyer(output, "ERROR|" + encoder("L’adversaire ne s’est pas connecté à temps."));
                return;
            }
            String ligne;
            while ((ligne = input.readLine()) != null) {
                if (ligne.startsWith("MOVE|")) {
                    partie.retransmettre(socket, ligne); // Seul le protocole MOVE est retransmis à l'adversaire.
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            terminerPartie(partie, socket);
        }
    }

    private void terminerPartie(Partie partie, Socket socketDeconnecte) {
        synchronized (verrou) {
            // AtomicBoolean fait office de garde indépendant du serveur principal :
            // plusieurs déconnexions concurrentes ne peuvent terminer la même partie qu'une fois.
            if (!partie.terminee.compareAndSet(false, true)) {
                return;
            }
            parties.remove(partie.token);
            // Le premier canal ferme la partie pour les deux joueurs : une partie ne
            // reste pas ouverte avec un participant fantome apres une deconnexion.
            Joueur[] participants = {partie.defi.expediteur, partie.defi.cible};
            for (Joueur participant : participants) {
                if (participant.partie == partie) {
                    participant.partie = null;
                    participant.envoyer("MATCH_END|");
                }
            }
            partie.fermerCanaux(socketDeconnecte); // Ferme le canal restant et debloque un eventuel thread en attente.
            actualiserListes();
        }
    }

    private void deconnecter(Joueur joueur) {
        synchronized (verrou) {
            if (joueurs.get(joueur.cle) != joueur) {
                return;
            }
            joueurs.remove(joueur.cle); // Retire d'abord la session pour qu'elle n'apparaisse plus disponible.
            if (joueur.defi != null) { // Une deconnexion annule aussi le defi en attente et libere son adversaire.
                Defi defi = defis.remove(joueur.defi);
                if (defi != null) {
                    libererDefi(defi);
                    Joueur autre = defi.expediteur == joueur ? defi.cible : defi.expediteur;
                    autre.envoyer("CHALLENGE_CANCELLED|" + defi.id + "|" + encoder(joueur.pseudo));
                }
            }
            if (joueur.partie != null) {
                terminerPartie(joueur.partie, null);
            }
            actualiserListes();
        }
    }

    private void libererDefi(Defi defi) {
        // Compare les identifiants avant effacement pour ne pas annuler un autre defi
        // si l'etat du joueur a change entre-temps.
        if (defi.expediteur.defi == defi.id) {
            defi.expediteur.defi = null;
        }
        if (defi.cible.defi == defi.id) {
            defi.cible.defi = null;
        }
    }

    private void actualiserListes() {
        for (Joueur joueur : joueurs.values()) {
            envoyerListe(joueur);
        }
    }

    private void envoyerListes() {
        actualiserListes();
    }

    private void envoyerListe(Joueur destinataire) {
        List<String> entrees = new ArrayList<>();
        for (Joueur joueur : joueurs.values()) {
            if (joueur != destinataire) {
                // "0" signifie libre, "1" signifie deja en defi ou en partie.
                entrees.add(encoder(joueur.pseudo) + ":"
                        + (joueur.partie == null && joueur.defi == null ? "0" : "1"));
            }
        }
        destinataire.envoyer("PLAYERS|" + String.join(",", entrees)); // Un message de ligne contient toutes les entrees, separees par virgules.
    }

    private void erreur(Joueur joueur, String message) {
        joueur.envoyer("ERROR|" + encoder(message));
    }

    private static void envoyer(PrintWriter output, String ligne) {
        // Plusieurs threads peuvent écrire sur un même flux ; le verrou garde chaque ligne entière.
        synchronized (output) {
            output.println(ligne); // Ajoute aussi automatiquement le saut de ligne du protocole.
            output.flush(); // Force l'envoi même si PrintWriter est configuré sans autoFlush ici.
        }
    }

    private static String encoder(String valeur) {
        // L'encodage URL-safe evite le caractere "|" reserve a la structure des messages.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(valeur.getBytes(StandardCharsets.UTF_8));
    }

    private static String decoder(String valeur) {
        return new String(Base64.getUrlDecoder().decode(valeur), StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        actif = false; // Demande a la boucle accept() de s'arreter.
        if (serveur != null) {
            try {
                serveur.close(); // Debloque accept() en interrompant la socket d'ecoute.
            } catch (IOException exception) {
                System.err.println("Impossible d’arrêter le serveur : " + exception.getMessage());
            }
        }
    }

    private static final class Joueur {
        private final String pseudo;
        private final String cle;
        private final PrintWriter output;
        private String defi;
        private Partie partie;

        private Joueur(String pseudo, String cle, Socket socket, PrintWriter output) {
            this.pseudo = pseudo;
            this.cle = cle;
            this.output = output;
        }

        private void envoyer(String message) {
            HubServer.envoyer(output, message);
        }
    }

    private static final class Defi {
        private final String id;
        private final Joueur expediteur;
        private final Joueur cible;
        private final String jeu;

        private Defi(String id, Joueur expediteur, Joueur cible, String jeu) {
            this.id = id;
            this.expediteur = expediteur;
            this.cible = cible;
            this.jeu = jeu;
        }
    }

    private static final class Partie {
        private final String token;
        private final Defi defi;
        private final Socket[] sockets = new Socket[2];
        private final PrintWriter[] sorties = new PrintWriter[2];
        private final CountDownLatch pret = new CountDownLatch(1); // Compte a rebours libere quand les deux joueurs sont connectes.
        private final AtomicBoolean terminee = new AtomicBoolean(); // Garde atomique contre les fins de partie concurrentes.
        private boolean demarrageEnvoye;

        private Partie(String token, Defi defi) {
            this.token = token;
            this.defi = defi;
        }

        // Réserve atomiquement une place pour chacun des deux clients du canal.
        private synchronized boolean ajouter(Socket socket, PrintWriter output) {
            if (sockets[0] == null) {
                sockets[0] = socket; // Première place réservée au premier joueur.
                sorties[0] = output; // Conserve le flux associé à cette même place.
            } else if (sockets[1] == null) {
                sockets[1] = socket; // Deuxième place réservée au joueur adverse.
                sorties[1] = output; // Conserve le flux associé au deuxième joueur.
            } else {
                return false;
            }
            return true;
        }

        private synchronized void envoyer(int index, String message) {
            if (sorties[index] != null) {
                HubServer.envoyer(sorties[index], message);
            }
        }

        private synchronized void retransmettre(Socket source, String ligne) {
            int destinataire = sockets[0] == source ? 1 : 0; // L'expéditeur est exclu : le coup va vers l'autre.
            if (sorties[destinataire] != null) {
                HubServer.envoyer(sorties[destinataire], ligne);
            }
        }

        private synchronized void fermerCanaux(Socket socketDeconnecte) {
            // Ne referme pas la socket deja fermee par le thread qui a detecte la coupure.
            for (Socket socket : sockets) {
                if (socket != null && socket != socketDeconnecte) {
                    try {
                        socket.close();
                    } catch (IOException exception) {
                        System.err.println("Impossible de fermer le canal de partie : "
                                + exception.getMessage());
                    }
                }
            }
            pret.countDown();
        }
    }
}
