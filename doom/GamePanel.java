package doom;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.HashSet;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;

public class GamePanel extends JPanel {
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
    private static final long STEP_NANOS = 1_000_000_000L / 60L;
    private static final int SHADE_LEVELS = 1024;
    private static final Font HUD_TITLE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 20);
    private static final Font HUD_BODY_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final Font HUD_MONO_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final BasicStroke CROSSHAIR_STROKE = new BasicStroke(2f);
    private static final BasicStroke RADAR_STROKE = new BasicStroke(1f);
    private static final BasicStroke PLAYER_STROKE = new BasicStroke(2f);
    private static final Color HUD_PANEL = new Color(8, 10, 14, 190);
    private static final Color HUD_POSITION = new Color(228, 230, 233);
    private static final Color HUD_CONTROLS = new Color(220, 224, 228);
    private static final Color RADAR_PANEL = new Color(5, 7, 10, 205);
    private static final Color RADAR_LABEL = new Color(235, 239, 241);
    private static final Color MAP_FLOOR = new Color(33, 38, 43);
    private static final Color RADAR_RAYS = new Color(76, 222, 213, 95);
    private static final Color PLAYER_MARKER = new Color(255, 224, 132);
    private static final Color CROSSHAIR = new Color(255, 255, 255, 100);
    private static final Color HUD_NOEL = new Color(180, 226, 243);
    private static final Color HUD_NORMAL = new Color(239, 177, 130);
    private static final Color MAP_WALL_NOEL = new Color(99, 143, 158);
    private static final Color MAP_WALL_NORMAL = new Color(130, 77, 62);

    private final Player player = new Player(1.5, 1.5);
    private final Set<String> pressedKeys = new HashSet<>();
    private final Timer gameTimer;
    private final boolean modeNoel;
    private long previousFrameTime;
    private long accumulatorNanos;
    private final RayHit reusableHit = new RayHit();
    private final Line2D.Double[] radarLines = new Line2D.Double[29];
    private final Color[] wallShades;
    private final Color[] wallBrickShades;
    private final Color[] alternateWallShades;
    private final Color[] alternateBrickShades;
    private GradientPaint skyGradient;
    private GradientPaint floorGradient;
    private int gradientWidth = -1;
    private int gradientHeight = -1;

    //couleur
    private Color skyTop;
    private Color skyBottom;
    private Color floorTop;
    private Color floorBottom;
    private Color base;

    public GamePanel() {
        this(false);
    }

    public GamePanel(boolean modeNoel) {
        this.modeNoel = modeNoel;
        setBackground(Color.BLACK);
        setFocusable(true);
        setPreferredSize(new java.awt.Dimension(1100, 720));
        configureKeys();
        instanciateColor();
        Color wall = modeNoel ? new Color(139, 179, 194) : new Color(153, 89, 69);
        Color alternateWall = modeNoel ? new Color(202, 222, 222) : new Color(177, 143, 101);
        wallShades = makeShadeTable(wall);
        wallBrickShades = wallShades;
        alternateWallShades = makeShadeTable(alternateWall);
        alternateBrickShades = alternateWallShades;
        for (int i = 0; i < radarLines.length; i++) {
            radarLines[i] = new Line2D.Double();
        }
        setDoubleBuffered(true);
        previousFrameTime = System.nanoTime();
        // Timer plus accumulateur: simulation à pas fixe même si les événements Swing dérivent.
        gameTimer = new Timer(16, event -> updateGame());
        gameTimer.setCoalesce(true);
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
        long now = System.nanoTime();
        long elapsed = Math.min(now - previousFrameTime, 50_000_000L);
        previousFrameTime = now;
        accumulatorNanos += elapsed;
        boolean updated = false;
        while (accumulatorNanos >= STEP_NANOS) {
            stepGame(STEP_NANOS / 1_000_000_000.0);
            accumulatorNanos -= STEP_NANOS;
            updated = true;
        }
        if (updated) {
            repaint();
        }
    }

    private void stepGame(double delta) {
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
        if (width != gradientWidth || height != gradientHeight) {
            int safeHorizon = Math.max(1, horizon);
            int safeHeight = Math.max(safeHorizon + 1, height);
            skyGradient = new GradientPaint(0, 0, skyTop, 0, safeHorizon, skyBottom);
            floorGradient = new GradientPaint(0, safeHorizon, floorTop, 0, safeHeight, floorBottom);
            gradientWidth = width;
            gradientHeight = height;
        }
        g.setPaint(skyGradient);
        g.fillRect(0, 0, width, horizon);
        g.setPaint(floorGradient);
        g.fillRect(0, horizon, width, height - horizon);

        double planeLength = Math.tan(FIELD_OF_VIEW / 2);
        double directionX = Math.cos(player.angle);
        double directionY = Math.sin(player.angle);
        double planeX = -directionY * planeLength;
        double planeY = directionX * planeLength;
        for (int x = 0; x < width; x++) {
            double cameraX = 2.0 * x / width - 1.0;
            double rayX = directionX + planeX * cameraX;
            double rayY = directionY + planeY * cameraX;
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

            boolean standardWall = hit.tile == 1;
            g.setColor(shade(standardWall ? wallShades : alternateWallShades, brightness));
            g.drawLine(x, top, x, bottom);
            if (wallHeight > 18) {
                int brickLine = top + Math.max(3, wallHeight / 4);
                if (brickLine < bottom) {
                    g.setColor(shade(standardWall ? wallBrickShades : alternateBrickShades,
                            brightness * 0.76f));
                    g.drawLine(x, brickLine, x, Math.min(bottom, brickLine + 1));
                }
            }
        }

        g.setColor(CROSSHAIR);
        g.setStroke(CROSSHAIR_STROKE);
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
                reusableHit.set(Math.max(distance, 0.001), side, MAP[mapY][mapX]);
                return reusableHit;
            }
        }
        return null;
    }

    private void drawHud(Graphics2D g) {
        g.setColor(HUD_PANEL);
        g.fillRoundRect(18, 18, 255, 70, 14, 14);
        g.setColor(modeNoel ? HUD_NOEL : HUD_NORMAL);
        g.setFont(HUD_TITLE_FONT);
        g.setColor(HUD_POSITION);
        g.setFont(HUD_MONO_FONT);
        g.drawString("POSITION  " + (int) (player.x * 10) + " / " + (int) (player.y * 10), 33, 70);

        String controls = "ZQSD : bouger    <- -> : regarder    SHIFT : courir    R : recommencer";
        g.setFont(HUD_BODY_FONT);
        FontMetrics metrics = g.getFontMetrics();
        int boxWidth = Math.min(getWidth() - 32, metrics.stringWidth(controls) + 24);
        int boxX = Math.max(16, (getWidth() - boxWidth) / 2);
        int boxY = getHeight() - 42;
        g.setColor(HUD_PANEL);
        g.fillRoundRect(boxX, boxY, boxWidth, 26, 10, 10);
        g.setColor(HUD_CONTROLS);
        g.drawString(controls, boxX + 12, boxY + 17);
    }

    private void drawMiniMap(Graphics2D g) {
        int cell = Math.max(7, Math.min(12, Math.min((getWidth() - 48) / MAP[0].length,
                (getHeight() - 160) / MAP.length)));
        int mapWidth = MAP[0].length * cell;
        int mapHeight = MAP.length * cell;
        int originX = getWidth() - mapWidth - 22;
        int originY = getHeight() - mapHeight - 80;

        g.setColor(RADAR_PANEL);
        g.fillRoundRect(originX - 9, originY - 30, mapWidth + 18, mapHeight + 40, 12, 12);
        g.setColor(RADAR_LABEL);
        g.setFont(HUD_BODY_FONT);
        g.drawString("RADAR  //  RAYONS", originX, originY - 12);

        for (int y = 0; y < MAP.length; y++) {
            for (int x = 0; x < MAP[y].length; x++) {
                if (MAP[y][x] == 0) {
                    g.setColor(MAP_FLOOR);
                } else {
                    g.setColor(modeNoel ? MAP_WALL_NOEL : MAP_WALL_NORMAL);
                }
                g.fillRect(originX + x * cell, originY + y * cell, cell - 1, cell - 1);
            }
        }

        g.setColor(RADAR_RAYS);
        g.setStroke(RADAR_STROKE);
        for (int i = 0; i <= 28; i++) {
            double angle = player.angle - FIELD_OF_VIEW / 2 + FIELD_OF_VIEW * i / 28.0;
            RayHit hit = castRay(Math.cos(angle), Math.sin(angle));
            if (hit != null) {
                double endX = player.x + Math.cos(angle) * hit.distance;
                double endY = player.y + Math.sin(angle) * hit.distance;
                radarLines[i].setLine(originX + player.x * cell, originY + player.y * cell,
                        originX + endX * cell, originY + endY * cell);
                g.draw(radarLines[i]);
            }
        }

        int playerX = originX + (int) (player.x * cell);
        int playerY = originY + (int) (player.y * cell);
        g.setColor(PLAYER_MARKER);
        g.fillOval(playerX - 4, playerY - 4, 8, 8);
        g.setStroke(PLAYER_STROKE);
        g.drawLine(playerX, playerY, playerX + (int) (Math.cos(player.angle) * cell * 0.75),
                playerY + (int) (Math.sin(player.angle) * cell * 0.75));
    }

    // Tables de nuances pré-calculées: plus aucune Color créée dans la boucle de raycasting.
    private static Color[] makeShadeTable(Color source) {
        Color[] shades = new Color[SHADE_LEVELS + 1];
        for (int i = 0; i <= SHADE_LEVELS; i++) {
            float factor = i / (float) SHADE_LEVELS;
            shades[i] = new Color((int) (source.getRed() * factor),
                    (int) (source.getGreen() * factor), (int) (source.getBlue() * factor));
        }
        return shades;
    }

    private static Color shade(Color[] shades, float factor) {
        int index = Math.max(0, Math.min(SHADE_LEVELS, (int) (factor * SHADE_LEVELS)));
        return shades[index];
    }

    public void stopGame() {
        gameTimer.stop();
    }

    private static final class RayHit {
        private double distance;
        private int side;
        private int tile;

        private void set(double distance, int side, int tile) {
            this.distance = distance;
            this.side = side;
            this.tile = tile;
        }
    }
}
