package doom;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.geom.Line2D;
import java.util.HashSet;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;

/**
 * Variante simplifiée du rendu pseudo-3D du dossier doom.
 *
 * Elle reprend les notions de rayon, de distance de mur et de carte 16x16, mais
 * allège certaines techniques visuelles comme les tables de couleurs détaillées.
 * La logique doit donc être lue comme une version parallèle plutôt que comme une
 * dépendance obligatoire du premier GamePanel.
 */
public class GamePanel2 extends JPanel {
    private static final int[][] MAP = {
        {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
        {1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1},
        {1, 0, 1, 1, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0, 0, 1},
        {1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1},
        {1, 0, 1, 0, 1, 0, 1, 1, 1, 1, 0, 1, 0, 1, 0, 1},
        {1, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 1, 0, 1},
        {1, 0, 1, 1, 1, 0, 1, 0, 1, 1, 1, 1, 0, 1, 0, 1},
        {1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 1},
        {1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 0, 1, 1, 1, 0, 1},
        {1, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1},
        {1, 0, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 1, 0, 1},
        {1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 1},
        {1, 0, 1, 1, 1, 0, 1, 1, 0, 1, 1, 0, 1, 1, 0, 1},
        {1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1},
        {1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1},
        {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}
    };
    private static final double FIELD_OF_VIEW = Math.toRadians(66);
    private static final double PLAYER_RADIUS = 0.18;

    private final Player player = new Player(1.5, 1.5);
    private final Set<String> pressedKeys = new HashSet<>();
    private final Timer gameTimer;
    private final boolean modeNoel;
    private long previousFrameTime;

    //couleur
    private Color skyTop;
    private Color skyBottom;
    private Color floorTop;
    private Color floorBottom;
    private Color base;

    public GamePanel2() {
        this(false);
    }

    public GamePanel2(boolean modeNoel) {
        this.modeNoel = modeNoel;
        setBackground(Color.BLACK);
        setFocusable(true);
        setPreferredSize(new java.awt.Dimension(1100, 720));
        configureKeys();
        instanciateColor();
        previousFrameTime = System.nanoTime();
        gameTimer = new Timer(16, event -> updateGame());
        gameTimer.start();
    }

    private void configureKeys() {
        bindKey("pressed Z", "forward-on-z", "forward", true);
        bindKey("released Z", "forward-off-z", "forward", false);
        bindKey("pressed S", "backward-on", "backward", true);
        bindKey("released S", "backward-off", "backward", false);
        bindKey("pressed Q", "strafe-left-on-q", "strafe-left", true);
        bindKey("released Q", "strafe-left-off-q", "strafe-left", false);
        bindKey("pressed D", "strafe-right-on", "strafe-right", true);
        bindKey("released D", "strafe-right-off", "strafe-right", false);
        bindKey("pressed LEFT", "turn-left-on", "turn-left", true);
        bindKey("released LEFT", "turn-left-off", "turn-left", false);
        bindKey("pressed RIGHT", "turn-right-on", "turn-right", true);
        bindKey("released RIGHT", "turn-right-off", "turn-right", false);
        bindKey("pressed SHIFT", "sprint-on", "sprint", true);
        bindKey("released SHIFT", "sprint-off", "sprint", false);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("pressed R"), "reset-player");
        getActionMap().put("reset-player", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                player.reset();
            }
        });
    }

    private Color instanciateColor() {
        // instancie toute les couleur pour pas a avoir a les crées 
        skyTop = modeNoel ? new Color(23, 45, 70) : new Color(27, 31, 41);
        skyBottom = modeNoel ? new Color(82, 105, 125) : new Color(82, 78, 72);
        floorTop = modeNoel ? new Color(48, 65, 75) : new Color(68, 61, 55);
        floorBottom = modeNoel ? new Color(13, 23, 31) : new Color(19, 19, 22);
        base = modeNoel
                ? (new Color(139, 179, 194))
                : (new Color(153, 89, 69));
        return base;
        
    }

    private void bindKey(String stroke, String actionName, String key, boolean pressed) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(stroke), actionName);
        getActionMap().put(actionName, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (pressed) {
                    pressedKeys.add(key);
                } else {
                    pressedKeys.remove(key);
                }
            }
        });
    }

    private void updateGame() {
        long now = System.nanoTime(); // Mesure courante utilisée pour calculer le temps écoulé.
        double delta = Math.min((now - previousFrameTime) / 1_000_000_000.0, 0.05);
        previousFrameTime = now;

        double turn = (pressedKeys.contains("turn-right") ? 1 : 0)
                - (pressedKeys.contains("turn-left") ? 1 : 0);
        player.rotate(turn * 2.2 * delta);

        double forward = (pressedKeys.contains("forward") ? 1 : 0)
                - (pressedKeys.contains("backward") ? 1 : 0);
        double strafe = (pressedKeys.contains("strafe-right") ? 1 : 0)
                - (pressedKeys.contains("strafe-left") ? 1 : 0);
        double movementLength = Math.hypot(forward, strafe);
        if (movementLength > 1) {
            forward /= movementLength;
            strafe /= movementLength;
        }
        double speed = 3.0 * (pressedKeys.contains("sprint") ? 1.65 : 1.0);
        player.move(forward * speed * delta, strafe * speed * delta, this::isWalkable);
        repaint();
    }

    private boolean isWalkable(double x, double y) {
        int left = (int) Math.floor(x - PLAYER_RADIUS);
        int right = (int) Math.floor(x + PLAYER_RADIUS);
        int top = (int) Math.floor(y - PLAYER_RADIUS);
        int bottom = (int) Math.floor(y + PLAYER_RADIUS);
        return isFloor(left, top) && isFloor(right, top)
                && isFloor(left, bottom) && isFloor(right, bottom);
    }

    private boolean isFloor(int x, int y) {
        return y >= 0 && y < MAP.length && x >= 0 && x < MAP[0].length && MAP[y][x] == 0;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawWorld(g);
        drawHud(g);
        drawMiniMap(g);
        g.dispose();
    }

    private void drawWorld(Graphics2D g) {
        int width = getWidth();
        int height = getHeight();
        int horizon = height / 2;
        g.setPaint(new GradientPaint(0, 0, skyTop, 0, horizon, skyBottom));
        g.fillRect(0, 0, width, horizon);
        g.setPaint(new GradientPaint(0, horizon, floorTop, 0, height, floorBottom));
        g.fillRect(0, horizon, width, height - horizon);

        double planeLength = Math.tan(FIELD_OF_VIEW / 2);
        double planeX = -Math.sin(player.angle) * planeLength;
        double planeY = Math.cos(player.angle) * planeLength;
        for (int x = 0; x < width; x++) {
            double cameraX = 2.0 * x / width - 1.0;
            double rayX = Math.cos(player.angle) + planeX * cameraX;
            double rayY = Math.sin(player.angle) + planeY * cameraX;
            RayHit hit = castRay(rayX, rayY);
            if (hit == null) {
                continue;
            }

            int wallHeight = (int) (height / Math.max(hit.distance, 0.001));
            int top = Math.max(0, horizon - wallHeight / 2);
            int bottom = Math.min(height, horizon + wallHeight / 2);
            double textureU = hit.side == 0
                    ? player.y + hit.distance * rayY
                    : player.x + hit.distance * rayX;
            textureU -= Math.floor(textureU);
            float brightness = (float) Math.max(0.20, 1.0 - hit.distance / 13.0);
            if (hit.side == 1) {
                brightness *= 0.72f;
            }
            if (textureU < 0.035 || textureU > 0.965) {
                brightness *= 0.68f;
            }
            if ((int) (textureU * 8) == 0) {
                brightness *= 0.82f;
            }

            Color base = modeNoel
                    ? (hit.tile == 1 ? new Color(139, 179, 194) : new Color(202, 222, 222))
                    : (hit.tile == 1 ? new Color(153, 89, 69) : new Color(177, 143, 101));
            g.setColor(scaleColor(base, brightness));
            g.drawLine(x, top, x, bottom);
            if (wallHeight > 18) {
                int brickLine = top + Math.max(3, wallHeight / 4);
                if (brickLine < bottom) {
                    g.setColor(scaleColor(base, brightness * 0.76f));
                    g.drawLine(x, brickLine, x, Math.min(bottom, brickLine + 1));
                }
            }
        }

        g.setColor(new Color(255, 255, 255, 100));
        g.setStroke(new BasicStroke(2f));
        int centerX = width / 2;
        g.drawLine(centerX - 7, horizon, centerX - 2, horizon);
        g.drawLine(centerX + 2, horizon, centerX + 7, horizon);
        g.drawLine(centerX, horizon - 7, centerX, horizon - 2);
        g.drawLine(centerX, horizon + 2, centerX, horizon + 7);
    }

    private RayHit castRay(double rayX, double rayY) {
        int mapX = (int) player.x;
        int mapY = (int) player.y;
        double deltaX = rayX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / rayX);
        double deltaY = rayY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / rayY);
        int stepX = rayX < 0 ? -1 : 1;
        int stepY = rayY < 0 ? -1 : 1;
        double sideX = rayX < 0
                ? (player.x - mapX) * deltaX : (mapX + 1.0 - player.x) * deltaX;
        double sideY = rayY < 0
                ? (player.y - mapY) * deltaY : (mapY + 1.0 - player.y) * deltaY;
        int side = 0;

        for (int steps = 0; steps < 64; steps++) {
            if (sideX < sideY) {
                sideX += deltaX;
                mapX += stepX;
                side = 0;
            } else {
                sideY += deltaY;
                mapY += stepY;
                side = 1;
            }
            if (mapY < 0 || mapY >= MAP.length || mapX < 0 || mapX >= MAP[0].length) {
                return null;
            }
            if (MAP[mapY][mapX] != 0) {
                double distance = side == 0
                        ? sideX - deltaX : sideY - deltaY;
                return new RayHit(Math.max(distance, 0.001), side, MAP[mapY][mapX]);
            }
        }
        return null;
    }

    private void drawHud(Graphics2D g) {
        g.setColor(new Color(8, 10, 14, 190));
        g.fillRoundRect(18, 18, 255, 70, 14, 14);
        g.setColor(modeNoel ? new Color(180, 226, 243) : new Color(239, 177, 130));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        g.drawString("RAYCAST // SECTEUR 09", 32, 46);
        g.setColor(new Color(228, 230, 233));
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.drawString("POSITION  " + (int) (player.x * 10) + " / " + (int) (player.y * 10), 33, 70);

        String controls = "ZQSD / WASD : bouger    <- -> : regarder    SHIFT : courir    R : recommencer";
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        FontMetrics metrics = g.getFontMetrics();
        int boxWidth = Math.min(getWidth() - 32, metrics.stringWidth(controls) + 24);
        int boxX = Math.max(16, (getWidth() - boxWidth) / 2);
        int boxY = getHeight() - 42;
        g.setColor(new Color(8, 10, 14, 190));
        g.fillRoundRect(boxX, boxY, boxWidth, 26, 10, 10);
        g.setColor(new Color(220, 224, 228));
        g.drawString(controls, boxX + 12, boxY + 17);
    }

    private void drawMiniMap(Graphics2D g) {
        int cell = Math.max(7, Math.min(12, Math.min((getWidth() - 48) / MAP[0].length,
                (getHeight() - 160) / MAP.length)));
        int mapWidth = MAP[0].length * cell;
        int mapHeight = MAP.length * cell;
        int originX = getWidth() - mapWidth - 22;
        int originY = getHeight() - mapHeight - 80;

        g.setColor(new Color(5, 7, 10, 205));
        g.fillRoundRect(originX - 9, originY - 30, mapWidth + 18, mapHeight + 40, 12, 12);
        g.setColor(new Color(235, 239, 241));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.drawString("RADAR  //  RAYONS", originX, originY - 12);

        for (int y = 0; y < MAP.length; y++) {
            for (int x = 0; x < MAP[y].length; x++) {
                if (MAP[y][x] == 0) {
                    g.setColor(new Color(33, 38, 43));
                } else {
                    g.setColor(modeNoel ? new Color(99, 143, 158) : new Color(130, 77, 62));
                }
                g.fillRect(originX + x * cell, originY + y * cell, cell - 1, cell - 1);
            }
        }

        g.setColor(new Color(76, 222, 213, 95));
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i <= 28; i++) {
            double angle = player.angle - FIELD_OF_VIEW / 2 + FIELD_OF_VIEW * i / 28.0;
            RayHit hit = castRay(Math.cos(angle), Math.sin(angle));
            if (hit != null) {
                double endX = player.x + Math.cos(angle) * hit.distance;
                double endY = player.y + Math.sin(angle) * hit.distance;
                g.draw(new Line2D.Double(originX + player.x * cell, originY + player.y * cell,
                        originX + endX * cell, originY + endY * cell));
            }
        }

        int playerX = originX + (int) (player.x * cell);
        int playerY = originY + (int) (player.y * cell);
        g.setColor(new Color(255, 224, 132));
        g.fillOval(playerX - 4, playerY - 4, 8, 8);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(playerX, playerY, playerX + (int) (Math.cos(player.angle) * cell * 0.75),
                playerY + (int) (Math.sin(player.angle) * cell * 0.75));
    }

    private Color scaleColor(Color color, float factor) {
        return new Color(Math.min(255, (int) (color.getRed() * factor)),
                Math.min(255, (int) (color.getGreen() * factor)),
                Math.min(255, (int) (color.getBlue() * factor)));
    }

    public void stopGame() {
        gameTimer.stop();
    }

    private static final class RayHit {
        private final double distance;
        private final int side;
        private final int tile;

        private RayHit(double distance, int side, int tile) {
            this.distance = distance;
            this.side = side;
            this.tile = tile;
        }
    }
}
