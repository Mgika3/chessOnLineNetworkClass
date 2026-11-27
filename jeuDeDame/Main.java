package jeuDeDame;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import reseau.GameConnection;

/**
 * Fenêtre autonome qui contient le plateau de dames.
 * L'appel retourAuHub permet au salon de reprendre la main lors de la fermeture.
 */
public class Main extends JFrame {
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
        this.setTitle("Jeu de dame"); // Titre affiché dans la barre.
        jeu = new JeuDeDame(modeNoel, connexion, joueurBlanc); // Crée le plateau avec le réseau éventuel.
        this.setContentPane(jeu); // Place le plateau comme contenu principal.
        this.pack(); // Calcule la taille naturelle de la fenêtre.
        this.setDefaultCloseOperation(retourAuHub == null ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE); // Choisit le comportement de fermeture.
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
