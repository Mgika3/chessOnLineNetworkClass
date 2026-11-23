package doom;

import java.util.function.BiPredicate;

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
        angle += amount;
        if (angle > Math.PI) {
            angle -= Math.PI * 2;
        } else if (angle < -Math.PI) {
            angle += Math.PI * 2;
        }
    }

    public void move(double forward, double strafe, BiPredicate<Double, Double> canOccupy) {
        double movementX = Math.cos(angle) * forward - Math.sin(angle) * strafe;
        double movementY = Math.sin(angle) * forward + Math.cos(angle) * strafe;

        double nextX = x + movementX;
        double nextY = y + movementY;
        if (canOccupy.test(nextX, y)) {
            x = nextX;
        }
        if (canOccupy.test(x, nextY)) {
            y = nextY;
        }
    }

    public void reset() {
        x = startX;
        y = startY;
        angle = -Math.PI / 2;
    }
}
