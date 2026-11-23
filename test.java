import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
public class test {
    public static void main2(String[] args) {
        InetAddress addr;
        String hote = "10.157.3.8" ; 
        try{
            addr = InetAddress.getByName(hote);
            if(addr.isReachable(1000)) {
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
        System.out.println("Diane Bourdette".hashCode());
    }
}

