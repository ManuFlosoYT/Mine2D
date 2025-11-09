package componentes;

import juego.Jugador;
import juego.bloques.BasicBlock;

/**
 * Clase que gestiona la posición de la cámara y su seguimiento suave al jugador
 */
public class Camara {
    private double x = 0.0;
    private double y = 0.0;

    private int viewportWidth;
    private int viewportHeight;

    // Ratios zona muerta (tercios)
    private static final double DEADZONE_LEFT_RATIO = 1.0 / 3.0;
    private static final double DEADZONE_RIGHT_RATIO = 2.0 / 3.0;
    private static final double DEADZONE_TOP_RATIO = 1.0 / 3.0;
    private static final double DEADZONE_BOTTOM_RATIO = 2.0 / 3.0;

    // Suavizado (valor alto = sigue más rápido)
    private static final double CAMERA_SMOOTH_SPEED = 10.0;

    public Camara(int viewportWidth, int viewportHeight) {
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
    }

    /**
     * Actualiza la cámara siguiendo al jugador con zona muerta y suavizado.
     * @param jugador jugador a seguir
     * @param mundo rejilla de bloques (puede ser null)
     * @param dt delta tiempo en segundos; si dt <= 0 se hace "snap" instantáneo
     */
    public void update(Jugador jugador, BasicBlock[][] mundo, double dt) {
        if (jugador == null) return;
        double size = BasicBlock.getSize();
        double worldHeightPx = (mundo != null ? mundo.length : 0) * size;
        double worldWidthPx = (mundo != null && mundo.length > 0 ? mundo[0].length : 0) * size;

        // Datos jugador
        double playerX = jugador.getX();
        double playerY = jugador.getY();
        double playerSize = jugador.getAltoPx();
        double playerRight = playerX + playerSize;
        double playerBottom = playerY + playerSize;

        // Umbrales actuales
        double leftThreshold = x + viewportWidth * DEADZONE_LEFT_RATIO;
        double rightThreshold = x + viewportWidth * DEADZONE_RIGHT_RATIO;
        double topThreshold = y + viewportHeight * DEADZONE_TOP_RATIO;
        double bottomThreshold = y + viewportHeight * DEADZONE_BOTTOM_RATIO;

        double desiredX = x;
        double desiredY = y;

        // Ajuste horizontal
        if (playerX < leftThreshold) {
            desiredX = playerX - viewportWidth * DEADZONE_LEFT_RATIO;
        } else if (playerRight > rightThreshold) {
            desiredX = playerRight - viewportWidth * DEADZONE_RIGHT_RATIO;
        }
        // Ajuste vertical
        if (playerY < topThreshold) {
            desiredY = playerY - viewportHeight * DEADZONE_TOP_RATIO;
        } else if (playerBottom > bottomThreshold) {
            desiredY = playerBottom - viewportHeight * DEADZONE_BOTTOM_RATIO;
        }

        // Clamps
        if (desiredX < 0) desiredX = 0;
        double maxCamX = Math.max(0, worldWidthPx - viewportWidth);
        if (desiredX > maxCamX) desiredX = maxCamX;

        if (desiredY < 0) desiredY = 0;
        double maxCamY = Math.max(0, worldHeightPx - viewportHeight);
        if (desiredY > maxCamY) desiredY = maxCamY;

        // Snap si inicial
        if (dt <= 0) {
            x = desiredX;
            y = desiredY;
            return;
        }

        // Interpolación suave
        double alpha = 1.0 - Math.exp(-CAMERA_SMOOTH_SPEED * dt);
        x = x + (desiredX - x) * alpha;
        y = y + (desiredY - y) * alpha;

        if (Math.abs(desiredX - x) < 0.1) x = desiredX;
        if (Math.abs(desiredY - y) < 0.1) y = desiredY;
    }

    public double getX() { return x; }
    public double getY() { return y; }

    public void setViewportSize(int w, int h) {
        this.viewportWidth = w;
        this.viewportHeight = h;
    }
}
