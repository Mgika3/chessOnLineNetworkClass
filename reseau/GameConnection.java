package reseau;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public final class GameConnection implements AutoCloseable {
    private final Socket socket;
    private final BufferedReader input;
    private final PrintWriter output;
    private final boolean host;
    private final String game;
    private final boolean modeNoel;
    private volatile Consumer<String> moveListener = move -> { };

    private GameConnection(Socket socket, BufferedReader input, boolean host, String game, boolean modeNoel) throws IOException {
        this.socket = socket;
        this.host = host;
        this.game = game;
        this.modeNoel = modeNoel;
        this.input = input;
        output = new PrintWriter(new OutputStreamWriter(
                socket.getOutputStream(), StandardCharsets.UTF_8), true);
        Thread reader = new Thread(this::lireCoups, "reception-coups");
        reader.setDaemon(true);
        reader.start();
    }

    public static GameConnection host(int port, String game, boolean modeNoel) throws IOException {
        if (!"chess".equals(game) && !"checkers".equals(game)) {
            throw new IllegalArgumentException("Jeu inconnu : " + game);
        }
        try (ServerSocket server = new ServerSocket(port)) {
            Socket socket = server.accept();
            PrintWriter output = new PrintWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8), true);
            output.println("START|" + game + "|" + modeNoel);
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
            socket.connect(new InetSocketAddress(address, port), 5000);
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
            output.println("JOIN|" + token);
            if (output.checkError()) {
                throw new IOException("Impossible de rejoindre le canal de partie.");
            }
            String reponse = input.readLine();
            if (!"READY|".equals(reponse)) {
                String raison = reponse != null && reponse.startsWith("ERROR|")
                        ? LobbyConnection.decoder(reponse.substring("ERROR|".length()))
                        : "Le serveur n’a pas préparé le canal de partie.";
                throw new IOException(raison);
            }
            return new GameConnection(socket, input, joueurBlanc, game, modeNoel);
        } catch (IOException | RuntimeException exception) {
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
            while ((ligne = input.readLine()) != null) {
                if (ligne.startsWith("MOVE|")) {
                    moveListener.accept(ligne.substring("MOVE|".length()));
                }
            }
        } catch (IOException exception) {
            if (!socket.isClosed()) {
                System.err.println("Connexion de jeu interrompue : " + exception.getMessage());
            }
        }
    }

    public synchronized void envoyerCoup(String coup) throws IOException {
        if (socket.isClosed() || !socket.isConnected()) {
            throw new IOException("La connexion à l’adversaire est fermée.");
        }
        output.println("MOVE|" + coup);
        if (output.checkError()) {
            throw new IOException("Impossible d’envoyer le coup à l’adversaire.");
        }
    }

    public void definirEcouteurCoups(Consumer<String> listener) {
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
