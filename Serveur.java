import reseau.HubServer;

import java.io.IOException;

/**
 * Point d'entrée du serveur central.
 *
 * Cette classe ne contient pas la logique réseau elle-même : elle valide l'argument
 * optionnel de port puis délègue tout le fonctionnement au HubServer. Séparer ainsi
 * le lancement du mécanisme évite de mélanger l'interface en ligne de commande,
 * la gestion du salon et l'état des parties.
 */

public class Serveur {
    private static final int PORT = 4444;

    public static void main(String[] args) {
        final int port;
        try {
            port = args.length > 0 ? Integer.parseInt(args[0]) : PORT; // Argument facultatif ou port 4444 par défaut.
            if (port < 1 || port > 65535) { // Les ports TCP valides commencent à 1.
                throw new NumberFormatException(); // Réutilise le même traitement d'erreur que Integer.parseInt().
            }
        } catch (NumberFormatException exception) {
            System.err.println("Port invalide : indiquez une valeur entre 1 et 65535."); // Erreur affichée sur la sortie standard d'erreurs.
            return;
        }
        try {
            // HubServer prend en charge les connexions apres validation de ce port.
            new HubServer(port).demarrer(); // Construit puis lance le serveur sans garder de référence locale.
        } catch (IOException exception) {
            System.err.println("Impossible de démarrer le serveur de salon : " + exception.getMessage());
        }
    }
}
