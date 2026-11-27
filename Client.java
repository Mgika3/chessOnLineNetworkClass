/**
 * Lanceur minimal de l'interface graphique du salon.
 * Les deux arguments sont l'adresse et le port du serveur central.
 */
public class Client {
    public static void main(String[] args) {
        String hote = args.length > 0 ? args[0] : "localhost"; // Adresse locale si aucun serveur n'est fourni.
        String port = args.length > 1 ? args[1] : "4444"; // Port utilisé par défaut par le serveur.
        Hub.demarrerReseau(hote, port); // Initialise Swing et ouvre l'interface du salon.
    }
}
