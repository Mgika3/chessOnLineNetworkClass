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
    private final JeuEchec jeu; // Le moteur contient le plateau, les regles et les timers de cette fenetre.

    public Main() { // Constructeur du lancement autonome sans theme festif ni salon parent.
        this(false, null); // Reutilise le constructeur general pour eviter de dupliquer l'initialisation.
    }

    public Main(boolean modeNoel, Runnable retourAuHub) { // Variante avec choix visuel et rappel de retour au salon.
        this(modeNoel, retourAuHub, null, true); // Pas de socket signifie une partie locale ; les Blancs commencent.
    }

    public Main(boolean modeNoel, Runnable retourAuHub,
                GameConnection connexion, boolean joueurBlanc) {
        setTitle("Jeu d'échecs"); // Titre affiché dans la barre de la fenêtre.
        jeu = new JeuEchec(modeNoel, connexion, joueurBlanc); // Construit le plateau avec ses options réseau.
        setContentPane(jeu); // Remplace le panneau Swing central par le plateau.
        pack(); // Calcule la taille depuis les preferredSize des composants.
        setDefaultCloseOperation(retourAuHub == null ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE); // Ferme le processus seul ou revient au salon.
        addWindowListener(new WindowAdapter() { // Lie le nettoyage des ressources au cycle de vie de la fenetre.
            @Override
            public void windowClosed(WindowEvent event) {
                // Un timer Swing continue a produire des evenements tant qu'il n'est pas arrete.
                jeu.arreterHorloge(); // Evite de laisser tourner les timers apres la fermeture.
                if (connexion != null) {
                    connexion.close(); // Fermer la socket notifie aussi le serveur de la fin du canal client.
                }
                if (retourAuHub != null) {
                    retourAuHub.run(); // Le salon est reaffiche sans redemarrer l'application.
                }
            }
        });
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public static void main(String[] args) { // Point d'entree si cette classe est lancee directement.
        SwingUtilities.invokeLater(Main::new); // Swing exige que la creation de fenetres se fasse sur son thread graphique.
    }
}
