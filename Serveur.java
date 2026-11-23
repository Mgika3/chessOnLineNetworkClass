import reseau.HubServer;

import java.io.IOException;

public class Serveur {
    private static final int PORT = 4444;

    public static void main(String[] args) {
        final int port;
        try {
            port = args.length > 0 ? Integer.parseInt(args[0]) : PORT;
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            System.err.println("Port invalide : indiquez une valeur entre 1 et 65535.");
            return;
        }
        try {
            new HubServer(port).demarrer();
        } catch (IOException exception) {
            System.err.println("Impossible de démarrer le serveur de salon : " + exception.getMessage());
        }
    }
}
