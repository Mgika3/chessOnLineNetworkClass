public class Client {
    public static void main(String[] args) {
        String hote = args.length > 0 ? args[0] : "localhost";
        String port = args.length > 1 ? args[1] : "4444";
        Hub.demarrerReseau(hote, port);
    }
}
