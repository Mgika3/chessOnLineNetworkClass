package doom;

import java.util.function.BiPredicate;

/**
 * Position et orientation d'un joueur dans le moteur pseudo-3D.
 *
 * Les coordonnées sont continues (x/y en cases). move() calcule d'abord un
 * déplacement relatif à l'angle de vue, puis vérifie séparément les axes X et Y.
 * Cette séparation permet de glisser le long d'un mur au lieu de bloquer totalement.
 */
public class Player {
    public double x;
    public double y;
    public double angle = -Math.PI / 2;
    private final double startX;
    private final double startY;

    public Player(double x, double y) {
        this.x = x;
        this.y = y;
        startX = x;
        startY = y;
    }

    public void rotate(double amount) {
        angle += amount; // Modifie l'orientation en radians.
        if (angle > Math.PI) {
            angle -= Math.PI * 2;
        } else if (angle < -Math.PI) {
            angle += Math.PI * 2;
        }
    }

    public void move(double forward, double strafe, BiPredicate<Double, Double> canOccupy) {
        double movementX = Math.cos(angle) * forward - Math.sin(angle) * strafe; // Composante horizontale dans le repère de vue.
        double movementY = Math.sin(angle) * forward + Math.cos(angle) * strafe; // Composante verticale dans le repère de vue.

        double nextX = x + movementX;
        double nextY = y + movementY;
        if (canOccupy.test(nextX, y)) {
            x = nextX; // Teste et applique l'axe horizontal séparément.
        }
        if (canOccupy.test(x, nextY)) {
            y = nextY; // Teste et applique l'axe vertical séparément.
        }
    }

    public void reset() {
        x = startX;
        y = startY;
        angle = -Math.PI / 2;
    }
}
