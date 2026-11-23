package reseau;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class HubServer implements AutoCloseable {
    private final int port;
    private final Object verrou = new Object();
    private final Map<String, Joueur> joueurs = new HashMap<>();
    private final Map<String, Defi> defis = new HashMap<>();
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
        serveur = new ServerSocket(port);
        System.out.println("Serveur de salon en écoute sur le port " + port + ".");
        while (actif) {
            try {
                Socket socket = serveur.accept();
                Thread worker = new Thread(() -> traiterConnexion(socket), "client-salon");
                worker.setDaemon(true);
                worker.start();
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
                    socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter output = new PrintWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8), true);
            String premiereLigne = input.readLine();
            if (premiereLigne == null) {
                socket.close();
                return;
            }
            if (premiereLigne.startsWith("REGISTER|")) {
                traiterJoueur(socket, input, output, premiereLigne);
            } else if (premiereLigne.startsWith("JOIN|")) {
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
            String[] champs = inscription.split("\\|", -1);
            pseudo = champs.length == 2 ? decoder(champs[1]).trim() : "";
            if (!pseudo.matches("[\\p{L}\\p{N}_-]{2,18}")) {
                throw new IllegalArgumentException(
                        "Le pseudo doit contenir 2 à 18 lettres, chiffres, tirets ou soulignés.");
            }
        } catch (IllegalArgumentException exception) {
            envoyer(output, "ERROR|" + encoder(exception.getMessage()));
            socket.close();
            return;
        }

        String cle = pseudo.toLowerCase(Locale.ROOT);
        joueur = new Joueur(pseudo, cle, socket, output);
        synchronized (verrou) {
            if (joueurs.containsKey(cle)) {
                envoyer(output, "ERROR|" + encoder("Ce pseudo est déjà utilisé."));
                socket.close();
                return;
            }
            joueurs.put(cle, joueur);
            envoyer(output, "WELCOME|" + encoder(pseudo));
            actualiserListes();
        }

        try {
            String commande;
            while ((commande = input.readLine()) != null) {
                traiterCommande(joueur, commande);
            }
        } finally {
            deconnecter(joueur);
        }
    }

    private void traiterCommande(Joueur joueur, String commande) {
        String[] champs = commande.split("\\|", -1);
        synchronized (verrou) {
            if (joueurs.get(joueur.cle) != joueur) {
                return;
            }
            switch (champs[0]) {
                case "LIST":
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
        Joueur cible = joueurs.get(pseudoCible.toLowerCase(Locale.ROOT));
        if (cible == null || cible == expediteur) {
            erreur(expediteur, "Ce joueur n’est plus disponible.");
            return;
        }
        if (expediteur.partie != null || cible.partie != null
                || expediteur.defi != null || cible.defi != null) {
            erreur(expediteur, "Un des joueurs est déjà engagé dans une partie ou un défi.");
            return;
        }

        String id = UUID.randomUUID().toString();
        Defi defi = new Defi(id, expediteur, cible, jeu);
        expediteur.defi = id;
        cible.defi = id;
        defis.put(id, defi);
        expediteur.envoyer("CHALLENGE_SENT|" + id + "|" + encoder(cible.pseudo));
        cible.envoyer("CHALLENGE|" + id + "|" + encoder(expediteur.pseudo) + "|" + jeu);
        actualiserListes();
    }

    private void accepterDefi(Joueur destinataire, String id) {
        Defi defi = defis.get(id);
        if (defi == null || defi.cible != destinataire) {
            erreur(destinataire, "Ce défi n’est plus disponible.");
            return;
        }
        if (joueurs.get(defi.expediteur.cle) != defi.expediteur) {
            defis.remove(id);
            libererDefi(defi);
            erreur(destinataire, "L’autre joueur s’est déconnecté.");
            return;
        }

        String token = UUID.randomUUID().toString();
        Partie partie = new Partie(token, defi);
        parties.put(token, partie);
        defi.expediteur.partie = partie;
        destinataire.partie = partie;
        defis.remove(id);
        libererDefi(defi);
        defi.expediteur.envoyer("MATCH|" + token + "|" + defi.jeu + "|WHITE|"
                + encoder(destinataire.pseudo));
        destinataire.envoyer("MATCH|" + token + "|" + defi.jeu + "|BLACK|"
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
            partie = champs.length == 2 ? parties.get(champs[1]) : null;
        }
        if (partie == null || !partie.ajouter(socket, output)) {
            envoyer(output, "ERROR|" + encoder("Cette partie n’est plus disponible."));
            socket.close();
            return;
        }

        boolean deuxJoueursConnectes;
        synchronized (partie) {
            deuxJoueursConnectes = partie.sockets[0] != null && partie.sockets[1] != null;
            if (deuxJoueursConnectes && !partie.demarrageEnvoye) {
                partie.demarrageEnvoye = true;
                partie.envoyer(0, "READY|");
                partie.envoyer(1, "READY|");
                partie.pret.countDown();
            }
        }
        try {
            if (!partie.pret.await(30, TimeUnit.SECONDS)) {
                envoyer(output, "ERROR|" + encoder("L’adversaire ne s’est pas connecté à temps."));
                return;
            }
            String ligne;
            while ((ligne = input.readLine()) != null) {
                if (ligne.startsWith("MOVE|")) {
                    partie.retransmettre(socket, ligne);
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
            if (!partie.terminee.compareAndSet(false, true)) {
                return;
            }
            parties.remove(partie.token);
            Joueur[] participants = {partie.defi.expediteur, partie.defi.cible};
            for (Joueur participant : participants) {
                if (participant.partie == partie) {
                    participant.partie = null;
                    participant.envoyer("MATCH_END|");
                }
            }
            partie.fermerCanaux(socketDeconnecte);
            actualiserListes();
        }
    }

    private void deconnecter(Joueur joueur) {
        synchronized (verrou) {
            if (joueurs.get(joueur.cle) != joueur) {
                return;
            }
            joueurs.remove(joueur.cle);
            if (joueur.defi != null) {
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
                entrees.add(encoder(joueur.pseudo) + ":"
                        + (joueur.partie == null && joueur.defi == null ? "0" : "1"));
            }
        }
        destinataire.envoyer("PLAYERS|" + String.join(",", entrees));
    }

    private void erreur(Joueur joueur, String message) {
        joueur.envoyer("ERROR|" + encoder(message));
    }

    private static void envoyer(PrintWriter output, String ligne) {
        synchronized (output) {
            output.println(ligne);
            output.flush();
        }
    }

    private static String encoder(String valeur) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(valeur.getBytes(StandardCharsets.UTF_8));
    }

    private static String decoder(String valeur) {
        return new String(Base64.getUrlDecoder().decode(valeur), StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        actif = false;
        if (serveur != null) {
            try {
                serveur.close();
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
        private final CountDownLatch pret = new CountDownLatch(1);
        private final AtomicBoolean terminee = new AtomicBoolean();
        private boolean demarrageEnvoye;

        private Partie(String token, Defi defi) {
            this.token = token;
            this.defi = defi;
        }

        private synchronized boolean ajouter(Socket socket, PrintWriter output) {
            if (sockets[0] == null) {
                sockets[0] = socket;
                sorties[0] = output;
            } else if (sockets[1] == null) {
                sockets[1] = socket;
                sorties[1] = output;
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
            int destinataire = sockets[0] == source ? 1 : 0;
            if (sorties[destinataire] != null) {
                HubServer.envoyer(sorties[destinataire], ligne);
            }
        }

        private synchronized void fermerCanaux(Socket socketDeconnecte) {
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
