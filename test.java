import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
/**
 * Programme expérimental sans lien avec le cycle normal du salon.
 * main2() teste la joignabilité d'une adresse et main() affiche un exemple de hash.
 * On peut conserver ces essais, mais ils ne doivent pas être utilisés comme lanceur.
 */
public class test {
    public static void main2(String[] args) {
        InetAddress addr;
        String hote = "10.157.3.8"; // Adresse utilisée uniquement pour cet essai manuel.
        try{
            addr = InetAddress.getByName(hote); // Résolution DNS ou analyse de l'adresse IP.
            if(addr.isReachable(1000)) { // Teste une réponse dans une fenêtre de 1000 ms.
                System.out.println("Node "+hote+" is reachable!!");
            }
        }
        catch(UnknownHostException e){
            e.printStackTrace();
        }
        catch(IOException i){
            i.printStackTrace();
        }
    }

    public static void main(String[] args) {
        System.out.println("Diane Bourdette".hashCode()); // Affiche un exemple de calcul Java basé sur le hash.
    }
}

