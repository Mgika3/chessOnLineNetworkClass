package doom; // Range cette classe dans le package doom avec le moteur de jeu pseudo-3D.

import javax.swing.JFrame; // Fenetre de haut niveau qui contient le panneau du jeu.
import javax.swing.SwingUtilities; // Permet de lancer l'interface sur le thread graphique Swing.
import java.awt.event.WindowAdapter; // Ecouteur pratique pour reagir a la fermeture de la fenetre.
import java.awt.event.WindowEvent; // Type d'evenement emis par la fenetre.
import reseau.GameConnection; // Canal reseau partage avec les jeux qui utilisent le serveur central.

/**
 * Fenetre qui affiche la demonstration pseudo-3D du package doom.
 *
 * Important pour comprendre l'architecture client/serveur : contrairement aux echecs
 * et aux dames, GamePanel ne lit ni n'envoie de coups reseau. Ce jeu Doom fonctionne
 * localement. Le parametre GameConnection n'ajoute pas le multijoueur : s'il est fourni,
 * cette fenetre le ferme seulement a sa fermeture. Le parametre joueurBlanc n'est pas
 * utilise par ce moteur et est conserve uniquement pour correspondre aux constructeurs
 * appeles depuis les autres fenetres de jeu.
 */
public class Main extends JFrame {
    private final GamePanel jeu; // Panneau qui dessine la vue 3D, gere les touches et fait avancer le joueur.

    public Main() { // Point d'entree simple pour lancer le jeu avec les reglages par defaut.
        this(false, null); // Delegue au constructeur complet : theme classique et aucun rappel vers le salon.
    }

    public Main(boolean modeNoel, Runnable retourAuHub) { // Constructeur pour choisir le theme et le comportement de retour.
        this(modeNoel, retourAuHub, null, true); // Pas de canal reseau ; true est une valeur sans effet dans ce jeu.
    }

    public Main(boolean modeNoel, Runnable retourAuHub,
                GameConnection connexion, boolean joueurBlanc) { // Compatibilite de signature avec les fenetres d'echecs et de dames.
        super("Raycast // Secteur 09"); // Donne a la fenetre son titre thématique.
        jeu = new GamePanel(modeNoel); // Cree le moteur graphique ; aucune connexion reseau ne lui est transmise.
        setContentPane(jeu); // Affiche GamePanel au centre de la fenetre Swing.
        pack(); // Calcule la taille initiale depuis la taille preferee declaree par GamePanel.
        setMinimumSize(new java.awt.Dimension(760, 520)); // Empeche de reduire la fenetre sous une taille utilisable.
        setDefaultCloseOperation(retourAuHub == null ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE); // Ferme l'application seule ou seulement cette fenetre.
        addWindowListener(new WindowAdapter() { // Ajoute un ecouteur pour nettoyer les ressources a la fermeture.
            @Override
            public void windowClosed(WindowEvent event) { // Methode appelee apres la fermeture effective de cette fenetre.
                jeu.stopGame(); // Arrete le timer Swing pour que le moteur ne continue pas en arriere-plan.
                if (connexion != null) { // Une connexion a pu etre fournie par l'appelant, meme si Doom ne l'utilise pas pour jouer.
                    connexion.close(); // Libere ce canal reseau ; cette fermeture ne transmet aucun resultat de partie.
                }
                if (retourAuHub != null) { // Une partie ouverte depuis le salon fournit un rappel de retour.
                    retourAuHub.run(); // Reaffiche le salon ; null indique un lancement autonome sans salon.
                }
            }
        });
        setLocationRelativeTo(null); // Centre la fenetre sur l'ecran, car elle n'a pas de fenetre parente.
        setVisible(true); // Rend la fenetre et son panneau visibles.
    }

    public static void main(String[] args) { // Lanceur Java standard pour demarrer Doom directement.
        SwingUtilities.invokeLater(Main::new); // Construit l'interface sur l'Event Dispatch Thread requis par Swing.
    }
}
