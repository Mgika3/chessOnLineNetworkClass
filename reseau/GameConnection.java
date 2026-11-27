package reseau;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Canal réseau dédié aux coups d'une partie.
 *
 * Cette classe possède deux modes. En réseau directe, host() accepte une connexion
 * locale tandis que join() se connecte à cette adresse. En mode salon, joinHub()
 * utilise d'abord un jeton transmis par HubServer, puis attend le signal READY avant
 * de rendre le canal utilisable. Dans tous les cas, les coups sont échangés sous la
 * forme texte "MOVE|..."; la logique métier reste dans JeuEchec ou JeuDeDame.
 */
public final class GameConnection implements AutoCloseable {
    private final Socket socket;
    private final BufferedReader input;
    private final PrintWriter output;
    private final boolean host;
    private final String game;
    private final boolean modeNoel;
    // L'écouteur peut être remplacé par le thread Swing et appelé par le thread réseau.
    private volatile Consumer<String> moveListener = move -> { };

    private GameConnection(Socket socket, BufferedReader input, boolean host, String game, boolean modeNoel) throws IOException {
        this.socket = socket;
        this.host = host;
        this.game = game;
        this.modeNoel = modeNoel;
        this.input = input;
        // Le flux sortant est cree ici meme si l'appelant a deja prepare le flux entrant.
        // Chaque objet GameConnection gere ainsi les deux sens du canal TCP.
        output = new PrintWriter(new OutputStreamWriter(
                socket.getOutputStream(), StandardCharsets.UTF_8), true);
        Thread reader = new Thread(this::lireCoups, "reception-coups");
        reader.setDaemon(true);
        reader.start();
    }

    public static GameConnection host(int port, String game, boolean modeNoel) throws IOException {
        if (!"chess".equals(game) && !"checkers".equals(game)) { // Validation du nom de jeu avant toute ouverture réseau.
            throw new IllegalArgumentException("Jeu inconnu : " + game);
        }
        try (ServerSocket server = new ServerSocket(port)) {
            // Dans ce mode pair-a-pair, ce joueur ouvre temporairement un port local,
            // puis attend la connexion de l'autre joueur avant de retourner le canal.
            Socket socket = server.accept();
            // La fermeture du serveur d'écoute ne ferme pas la socket déjà acceptée.
            PrintWriter output = new PrintWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8), true);
            output.println("START|" + game + "|" + modeNoel);
            // PrintWriter masque les erreurs d'écriture ; checkError permet de les signaler.
            if (output.checkError()) {
                socket.close();
                throw new IOException("Impossible d’envoyer les paramètres de la partie.");
            }
            return new GameConnection(socket,
                    new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)),
                    true, game, modeNoel);
        }
    }

    public static GameConnection join(String address, int port) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(address, port), 5000); // Ici le joueur est client TCP et contacte l'hote.
            BufferedReader input = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            String ligne = input.readLine();
            String[] configuration = ligne == null ? new String[0] : ligne.split("\\|", -1);
            if (configuration.length != 3 || !"START".equals(configuration[0])
                    || (!"chess".equals(configuration[1]) && !"checkers".equals(configuration[1]))
                    || (!"true".equals(configuration[2]) && !"false".equals(configuration[2]))) {
                throw new IOException("Le serveur a envoyé une configuration de partie invalide.");
            }
            return new GameConnection(socket, input, false, configuration[1],
                    Boolean.parseBoolean(configuration[2]));
        } catch (IOException | RuntimeException exception) {
            socket.close();
            throw exception;
        }
    }

    public static GameConnection joinHub(String address, int port, String token, String game, boolean modeNoel, boolean joueurBlanc) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(address, port), 5000);
            BufferedReader input = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter output = new PrintWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8), true);
            // Mode salon : le client rejoint le serveur central, qui retrouve le match
            // avec ce token puis relaie les lignes MOVE vers l'autre socket.
            output.println("JOIN|" + token);
            if (output.checkError()) {
                throw new IOException("Impossible de rejoindre le canal de partie.");
            }
            String reponse = input.readLine(); // Le serveur n'autorise le jeu qu'une fois les deux clients connectes.
            if (!"READY|".equals(reponse)) {
                String raison = reponse != null && reponse.startsWith("ERROR|")
                        ? LobbyConnection.decoder(reponse.substring("ERROR|".length()))
                        : "Le serveur n'''  a pas préparé le canal de partie.";
                throw new IOException(raison);
            }
            return new GameConnection(socket, input, joueurBlanc, game, modeNoel);
        } catch (IOException | RuntimeException exception) {
            // Évite de laisser une socket ouverte si l'établissement ou la négociation échoue.
            try {
                socket.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    private void lireCoups() {
        try {
            String ligne;
            // Le thread reste en lecture pendant toute la partie ; les deplacements recus
            // sont remis au moteur via moveListener plutot que traites dans ce thread.
            while ((ligne = input.readLine()) != null) {
                if (ligne.startsWith("MOVE|")) {
                    moveListener.accept(ligne.substring("MOVE|".length())); // Retire le préfixe pour ne transmettre que le coup.
                }
            }
        } catch (IOException exception) {
            if (!socket.isClosed()) {
                System.err.println("Connexion de jeu interrompue : " + exception.getMessage());
            }
        }
    }

    public synchronized void envoyerCoup(String coup) throws IOException {
        if (socket.isClosed() || !socket.isConnected()) { // Interdit l'envoi si le canal est coupé.
            throw new IOException("La connexion à l’adversaire est fermée.");
        }
        output.println("MOVE|" + coup); // Enveloppe le coup dans le type de message compris par le relais serveur.
        // PrintWriter ne propage pas directement IOException lors d'une écriture.
        if (output.checkError()) {
            throw new IOException("Impossible d’envoyer le coup à l’adversaire.");
        }
    }

    public void definirEcouteurCoups(Consumer<String> listener) {
        // La référence volatile rend le nouvel écouteur visible au thread de lecture.
        moveListener = listener == null ? move -> { } : listener;
    }

    public boolean estHote() {
        return host;
    }

    public String getJeu() {
        return game;
    }

    public boolean isModeNoel() {
        return modeNoel;
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException exception) {
            System.err.println("Impossible de fermer la connexion : " + exception.getMessage());
        }
    }
}
