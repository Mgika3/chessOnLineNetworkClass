package doom;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import reseau.GameConnection;

/**
 * Fenêtre Swing contenant le moteur de rendu du jeu Doom.
 * Elle coordonne la fermeture du panneau, la connexion réseau et le retour au salon.
 */
public class Main extends JFrame {
    private final GamePanel jeu;

    public Main(){
        this(false, null);
    }

    public Main(boolean modeNoel, Runnable retourAuHub) {
        this(modeNoel, retourAuHub, null, true);
    }

    public Main(boolean modeNoel, Runnable retourAuHub,
                GameConnection connexion, boolean joueurBlanc) {
        super("Raycast // Secteur 09"); // Titre thématique de la fenêtre.
        jeu = new GamePanel(modeNoel); // Crée le panneau pseudo-3D.
        setContentPane(jeu); // Utilise ce panneau comme vue principale.
        pack(); // Applique la taille préférée du panneau.
        setMinimumSize(new java.awt.Dimension(760, 520));
        setDefaultCloseOperation(retourAuHub == null ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                jeu.stopGame();
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
