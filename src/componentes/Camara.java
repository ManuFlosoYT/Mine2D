package componentes;

import juego.Jugador;
import juego.bloques.BasicBlock;

/**
 * Gestiona la posición de la cámara y su seguimiento suave del jugador.
 *
 * <p>Incluye una zona muerta basada en tercios de la pantalla para evitar movimientos
 * bruscos y una interpolación exponencial para suavizar el desplazamiento.</p>
 */
public class Camara {
    private double x = 0.0;
    private double y = 0.0;

    private final int viewportWidth;
    private final int viewportHeight;

    // Ratios zona muerta (tercios)
    private static final double TERCIO_IZQUIERDO = 1.0 / 3.0;
    private static final double TERCIO_DERECHO = 2.0 / 3.0;
    private static final double TERCIO_SUPERIOR = 1.0 / 3.0;
    private static final double TERCIO_INFERIOR = 2.0 / 3.0;

    // Suavizado (valor alto = sigue más rápido)
    private static final double CAMERA_SMOOTH_SPEED = 10.0;

    /**
     * Crea una cámara con el tamaño del viewport especificado.
     * @param viewportWidth ancho del viewport en píxeles
     * @param viewportHeight alto del viewport en píxeles
     */
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
        double leftThreshold = x + viewportWidth * TERCIO_IZQUIERDO;
        double rightThreshold = x + viewportWidth * TERCIO_DERECHO;
        double topThreshold = y + viewportHeight * TERCIO_SUPERIOR;
        double bottomThreshold = y + viewportHeight * TERCIO_INFERIOR;

        double desiredX = x;
        double desiredY = y;

        // Ajuste horizontal
        if (playerX < leftThreshold) {
            desiredX = playerX - viewportWidth * TERCIO_IZQUIERDO;
        } else if (playerRight > rightThreshold) {
            desiredX = playerRight - viewportWidth * TERCIO_DERECHO;
        }
        // Ajuste vertical
        if (playerY < topThreshold) {
            desiredY = playerY - viewportHeight * TERCIO_SUPERIOR;
        } else if (playerBottom > bottomThreshold) {
            desiredY = playerBottom - viewportHeight * TERCIO_INFERIOR;
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

    /** Posición X de la cámara en píxeles del mundo. */
    public double getX() { return x; }
    /** Posición Y de la cámara en píxeles del mundo. */
    public double getY() { return y; }
}
