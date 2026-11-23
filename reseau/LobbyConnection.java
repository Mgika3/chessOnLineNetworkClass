package reseau;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;

public final class LobbyConnection implements AutoCloseable {
    private final Socket socket;
    private final BufferedReader input;
    private final PrintWriter output;
    private volatile Consumer<String> listener = message -> { };

    private LobbyConnection(Socket socket, BufferedReader input, PrintWriter output) {
        this.socket = socket;
        this.input = input;
        this.output = output;
        Thread reader = new Thread(this::lireMessages, "reception-salon");
        reader.setDaemon(true);
        reader.start();
    }

    public static LobbyConnection connect(String address, int port, String nickname) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(address, port), 5000);
            BufferedReader input = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter output = new PrintWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8), true);
            output.println("REGISTER|" + encoder(nickname));
            if (output.checkError()) {
                throw new IOException("Impossible d’envoyer le pseudo au serveur.");
            }
            String response = input.readLine();
            if (response == null || !response.startsWith("WELCOME|")) {
                String reason = response != null && response.startsWith("ERROR|")
                        ? decoder(response.substring("ERROR|".length()))
                        : "Le serveur a fermé la connexion avant de confirmer l’inscription.";
                throw new IOException(reason);
            }
            return new LobbyConnection(socket, input, output);
        } catch (IOException | RuntimeException exception) {
            try {
                socket.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    public synchronized void envoyer(String commande) throws IOException {
        if (socket.isClosed() || !socket.isConnected()) {
            throw new IOException("La connexion au salon est fermée.");
        }
        output.println(commande);
        if (output.checkError()) {
            throw new IOException("Impossible d’envoyer la commande au salon.");
        }
    }

    public void definirEcouteur(Consumer<String> nouveauListener) {
        listener = nouveauListener == null ? message -> { } : nouveauListener;
    }

    private void lireMessages() {
        try {
            String message;
            while ((message = input.readLine()) != null) {
                listener.accept(message);
            }
            if (!socket.isClosed()) {
                listener.accept("DISCONNECTED|Le serveur a fermé la connexion.");
            }
        } catch (IOException exception) {
            if (!socket.isClosed()) {
                listener.accept("DISCONNECTED|" + (exception.getMessage() == null
                        ? "La connexion au serveur a été interrompue." : exception.getMessage()));
            }
        }
    }

    public static String encoder(String valeur) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(valeur.getBytes(StandardCharsets.UTF_8));
    }

    public static String decoder(String valeur) throws IOException {
        try {
            return new String(Base64.getUrlDecoder().decode(valeur), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Le serveur a envoyé un texte invalide.", exception);
        }
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException exception) {
            System.err.println("Impossible de fermer la connexion au salon : " + exception.getMessage());
        }
    }
}
