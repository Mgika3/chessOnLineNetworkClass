package jeuDeDame;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import reseau.GameConnection;

public class Main extends JFrame{
    private final JeuDeDame jeu;

    //Fenetre principale du jeu de dame
    public Main(){
        this(false, null);
    }

    public Main(boolean modeNoel, Runnable retourAuHub) {
        this(modeNoel, retourAuHub, null, true);
    }

    public Main(boolean modeNoel, Runnable retourAuHub,
                GameConnection connexion, boolean joueurBlanc) {
        this.setTitle("Jeu de dame");
        jeu = new JeuDeDame(modeNoel, connexion, joueurBlanc);
        this.setContentPane(jeu);
        this.pack();
        this.setDefaultCloseOperation(retourAuHub == null ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                jeu.arreterHorloge();
                if (connexion != null) {
                    connexion.close();
                }
                if (retourAuHub != null) {
                    retourAuHub.run();
                }
            }
        });
        this.setLocationRelativeTo(null);
        this.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::new);
    }
}
