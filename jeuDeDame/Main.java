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
    private final JeuDeDame jeu; // Le moteur de dames gere son plateau et son horloge.

    //Fenetre principale du jeu de dame
    public Main() { // Lance une partie autonome avec les reglages par defaut.
        this(false, null); // Delegue au constructeur complet au lieu de refaire l'initialisation.
    }

    public Main(boolean modeNoel, Runnable retourAuHub) { // Constructeur utilise par l'interface du salon.
        this(modeNoel, retourAuHub, null, true); // Une connexion nulle indique une partie locale.
    }

    public Main(boolean modeNoel, Runnable retourAuHub,
                GameConnection connexion, boolean joueurBlanc) {
        this.setTitle("Jeu de dame"); // Titre affiché dans la barre.
        jeu = new JeuDeDame(modeNoel, connexion, joueurBlanc); // Crée le plateau avec le réseau éventuel.
        this.setContentPane(jeu); // Place le plateau comme contenu principal.
        this.pack(); // Calcule la taille naturelle de la fenêtre.
        this.setDefaultCloseOperation(retourAuHub == null ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE); // Choisit le comportement de fermeture.
        addWindowListener(new WindowAdapter() { // Observe la fermeture pour liberer les ressources du jeu et du reseau.
            @Override
            public void windowClosed(WindowEvent event) {
                // Arrete l'horloge de la partie pour liberer son timer Swing.
                jeu.arreterHorloge();
                if (connexion != null) {
                    connexion.close(); // La fermeture du canal avertit indirectement le serveur du depart du client.
                }
                if (retourAuHub != null) {
                    retourAuHub.run(); // Retour a l'interface du salon apres fermeture du plateau.
                }
            }
        });
        this.setLocationRelativeTo(null);
        this.setVisible(true);
    }

    public static void main(String[] args) { // Lance directement une fenetre de dames sans passer par Hub.
        SwingUtilities.invokeLater(Main::new); // Construit l'interface sur le thread Swing.
    }
}
