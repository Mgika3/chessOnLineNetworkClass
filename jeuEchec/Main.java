package jeuEchec;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import reseau.GameConnection;

/**
 * Fenêtre autonome qui contient le plateau d'échecs.
 * Elle ferme aussi proprement la connexion réseau et arrête les timers associés.
 */
public class Main extends JFrame {
    private final JeuEchec jeu;

    public Main() {
        this(false, null);
    }

    public Main(boolean modeNoel, Runnable retourAuHub) {
        this(modeNoel, retourAuHub, null, true);
    }

    public Main(boolean modeNoel, Runnable retourAuHub,
                GameConnection connexion, boolean joueurBlanc) {
        setTitle("Jeu d'échecs"); // Titre affiché dans la barre de la fenêtre.
        jeu = new JeuEchec(modeNoel, connexion, joueurBlanc); // Construit le plateau avec ses options réseau.
        setContentPane(jeu); // Remplace le panneau Swing central par le plateau.
        pack(); // Calcule la taille depuis les preferredSize des composants.
        setDefaultCloseOperation(retourAuHub == null ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE); // Ferme le processus seul ou revient au salon.
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
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::new);
    }
}
