package componentes;

import java.awt.*;
import java.awt.geom.AffineTransform;

/**
 * HUD de depuración que muestra FPS, duración del frame y posición del jugador.
 */
public class HudDebug {
    private long fpsWindowFrames = 0;
    private long fpsWindowNanos = 0;
    private static final long HUD_FPS_UPDATE_NS = 250_000_000L; // 250 ms
    private int hudFPS = 0; // valor mostrado
    private double hudFrameTimeMs = 0.0; // último frame en ms

    // Posición del jugador
    private double playerX = 0.0;
    private double playerY = 0.0;
    private int playerChunkX = 0;
    private int playerChunkY = 0;

    /** Actualiza la posición a mostrar en el HUD. */
    public void setPlayerPosition(double x, double y) {
        this.playerX = x;
        this.playerY = y;
    }

    public void setPlayerChunk(int cx, int cy) {
        this.playerChunkX = cx;
        this.playerChunkY = cy;
    }

    /**
     * Registra un frame completado y acumula tiempos para cálculo de FPS.
     * @param frameNs duración del frame en nanosegundos
     */
    public void updateFrame(long frameNs) {
        hudFrameTimeMs = frameNs / 1_000_000.0;
        fpsWindowFrames++;
        fpsWindowNanos += frameNs;
        if (fpsWindowNanos >= HUD_FPS_UPDATE_NS) {
            double secs = fpsWindowNanos / 1_000_000_000.0;
            hudFPS = (int) Math.round(fpsWindowFrames / secs);
            fpsWindowFrames = 0;
            fpsWindowNanos = 0;
        }
    }

    /**
     * Dibuja el HUD en la esquina superior izquierda.
     * Ignora transformaciones previas para asegurar posición fija.
     * @param g contexto gráfico donde dibujar
     */
    public void draw(Graphics2D g) {
        if (g == null) return;
        AffineTransform old = g.getTransform();
        g.setTransform(new AffineTransform());
        g.setFont(new Font("Consolas", Font.PLAIN, 14));

        // Fondo semitransparente más alto para 4 líneas
        g.setColor(new Color(0,0,0,140));
        g.fillRoundRect(8, 8, 200, 120, 8, 8);

        g.setColor(Color.WHITE);
        int x = 16;
        int y = 28;
        int dy = 18;
        String fpsTxt = "FPS: " + hudFPS;
        String ftTxt = String.format("Frame: %.2f ms", hudFrameTimeMs);
        String pxTxt = "X(blocks): " + (int)playerX;
        String pyTxt = "Y(blocks): " + (int)playerY;
        String chunkTxt = "Chunk: " + playerChunkX + ", " + playerChunkY;
        g.drawString(fpsTxt, x, y); y += dy;
        g.drawString(ftTxt, x, y); y += dy;
        g.drawString(pxTxt, x, y); y += dy;
        g.drawString(pyTxt, x, y); y += dy;
        g.drawString(chunkTxt, x, y);

        g.setTransform(old);
    }
}
