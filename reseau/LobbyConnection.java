package reseau;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;

/**
 * Représente une session persistante entre un client graphique et le serveur de salon.
 *
 * L'objet tient la socket TCP, les flux texte et un écouteur dans lequel chaque
 * message reçu est transmis. Le protocole est volontairement simple : une commande
 * par ligne, avec un préfixe indiquant son type. Les valeurs humaines sont encodées
 * en Base64 URL afin qu'elles puissent contenir des caractères sans casser le
 * séparateur vertical "|" du protocole.
 */
public final class LobbyConnection implements AutoCloseable {
    private final Socket socket;
    private final BufferedReader input;
    private final PrintWriter output;
    private volatile Consumer<String> listener = message -> { };

    private LobbyConnection(Socket socket, BufferedReader input, PrintWriter output) {
        this.socket = socket;
        this.input = input;
        this.output = output;
        // Le thread est daemon afin qu'il n'empêche jamais la JVM de s'arrêter.
        // Il ne gère pas l'état de l'interface : il délègue les lignes reçues au Consumer.
        Thread reader = new Thread(this::lireMessages, "reception-salon"); // Thread chargé d'attendre les lignes réseau.
        reader.setDaemon(true); // Ne bloque pas l'arrêt de la JVM.
        reader.start(); // Lance la réception en arrière-plan.
    }

    public static LobbyConnection connect(String address, int port, String nickname) throws IOException {
        Socket socket = new Socket(); // Socket créée puis configurée pour pouvoir être fermée sur erreur.
        try {
            socket.connect(new InetSocketAddress(address, port), 5000); // Timeout de connexion de cinq secondes.
            // Les flux sont en UTF-8 pour transporter correctement les pseudos accentues.
            // Le protocole est textuel et chaque commande occupe une ligne TCP complete.
            BufferedReader input = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter output = new PrintWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8), true);
            output.println("REGISTER|" + encoder(nickname)); // Premiere commande : demande au serveur d'enregistrer ce client.
            // PrintWriter absorbe les erreurs d'écriture ; il faut les vérifier explicitement.
            if (output.checkError()) {
                throw new IOException("Impossible d'envoyer le pseudo au serveur.");
            }
            String response = input.readLine(); // L'inscription est synchrone jusqu'a la confirmation WELCOME.
            if (response == null || !response.startsWith("WELCOME|")) {
                String reason = response != null && response.startsWith("ERROR|")
                        ? decoder(response.substring("ERROR|".length()))
                        : "Le serveur a fermé la connexion avant de confirmer l'inscription.";
                throw new IOException(reason);
            }
            return new LobbyConnection(socket, input, output);
        } catch (IOException | RuntimeException exception) {
            // La connexion n'est pas remise à l'appelant si l'inscription échoue.
            try {
                socket.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    public synchronized void envoyer(String commande) throws IOException {
        if (socket.isClosed() || !socket.isConnected()) { // Évite d'écrire sur une connexion inexistante.
            throw new IOException("La connexion au salon est fermée.");
        }
        output.println(commande); // println ajoute le delimiteur de ligne attendu par readLine() cote serveur.
        if (output.checkError()) {
            throw new IOException("Impossible d’envoyer la commande au salon.");
        }
    }

    public void definirEcouteur(Consumer<String> nouveauListener) {
        // Le listener est volatile car le thread de lecture peut le consulter pendant son remplacement.
        listener = nouveauListener == null ? message -> { } : nouveauListener;
    }

    private void lireMessages() {
        try {
            String message;
            // Cette boucle attend les evenements du serveur sans bloquer le thread Swing.
            while ((message = input.readLine()) != null) {
                listener.accept(message); // Diffuse chaque ligne reçue à l'interface.
            }
            if (!socket.isClosed()) {
                listener.accept("DISCONNECTED|Le serveur a fermé la connexion.");
            }
        } catch (IOException exception) {
            // Une fermeture demandée localement n'est pas signalée comme une panne réseau.
            if (!socket.isClosed()) {
                listener.accept("DISCONNECTED|" + (exception.getMessage() == null
                        ? "La connexion au serveur a été interrompue." : exception.getMessage()));
            }
        }
    }

    public static String encoder(String valeur) {
        // URL-safe sans "=" rend la valeur sûre dans les champs séparés par "|".
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(valeur.getBytes(StandardCharsets.UTF_8));
    }

    public static String decoder(String valeur) throws IOException {
        try {
            return new String(Base64.getUrlDecoder().decode(valeur), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            // Une donnee reseau mal encodee devient une erreur explicite de protocole.
            throw new IOException("Le serveur a envoyé un texte invalide.", exception);
        }
    }

    @Override
    public void close() {
        try {
            // Fermer la socket debloque aussi le readLine() du thread de reception.
            socket.close();
        } catch (IOException exception) {
            System.err.println("Impossible de fermer la connexion au salon : " + exception.getMessage());
        }
    }
}
