package componentes;

import java.awt.*;
import java.awt.geom.AffineTransform;

/**
 * HUD de depuración que muestra FPS y duración del frame.
 * Encapsula el cálculo de FPS en una ventana temporal.
 */
public class HudDebug {
    private long fpsWindowFrames = 0;
    private long fpsWindowNanos = 0;
    private static final long HUD_FPS_UPDATE_NS = 250_000_000L; // 250 ms
    private int hudFPS = 0; // valor mostrado
    private double hudFrameTimeMs = 0.0; // último frame en ms

    /** Llamar cada frame con la duración total del frame en nanosegundos */
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

    /** Dibuja el HUD en la esquina superior izquierda (ignora transformaciones previas) */
    public void draw(Graphics2D g) {
        if (g == null) return;
        AffineTransform old = g.getTransform();
        g.setTransform(new AffineTransform());
        g.setFont(new Font("Consolas", Font.PLAIN, 14));
        g.setColor(new Color(0,0,0,140));
        g.fillRoundRect(8, 8, 160, 52, 8, 8);
        g.setColor(Color.WHITE);
        String fpsTxt = "FPS: " + hudFPS;
        String ftTxt = String.format("Frame: %.2f ms", hudFrameTimeMs);
        g.drawString(fpsTxt, 16, 28);
        g.drawString(ftTxt, 16, 46);
        g.setTransform(old);
    }

    public int getHudFPS() { return hudFPS; }
    public double getHudFrameTimeMs() { return hudFrameTimeMs; }
}

