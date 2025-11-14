package componentes;

import juego.Jugador;
import juego.bloques.BasicBlock;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.List;

/**
 * Encapsula todo el dibujado de elementos del mundo y overlays de interacción.
 *
 * <p>Se aplica primero un fondo, luego bloques visibles, jugador y finalmente indicadores
 * de interacción (hover y progreso de rotura).</p>
 */
public class Renderer {
    private static final Color COLOR_CIELO = new Color(135, 206, 235);

    /** Pinta el fondo del nivel. */
    public void drawBackground(Graphics2D g, int ancho, int alto) {
        g.setColor(COLOR_CIELO);
        g.fillRect(0, 0, ancho, alto);
    }

    /**
     * Dibuja la escena principal (bloques visibles y jugador) y overlays del editor.
     * @param g contexto gráfico (ya apunta al buffer offscreen)
     * @param bloquesVisibles lista de bloques dentro del viewport actual
     * @param jugador entidad jugador
     * @param camara cámara para calcular desplazamiento
     * @param editorMundo estado de interacción del editor (hover, rotura)
     * @param scale factor de escala para el mundo
     * @param lightGrid cuadrícula de valores de luz para el debug
     * @param debugLight flag para activar/desactivar el modo debug de luz
     */
    public void drawGame(Graphics2D g,
                         List<BasicBlock> bloquesVisibles,
                         Jugador jugador,
                         Camara camara,
                         EditorMundo editorMundo,
                         double scale,
                         Lighting.LightGrid lightGrid,
                         boolean debugLight) {
        if (g == null || camara == null) return;
        AffineTransform old = g.getTransform();
        // Escalar mundo para mantener tamaño aparente del bloque
        g.scale(scale, scale);
        // Tras la escala, las traducciones están en píxeles de mundo
        g.translate(-camara.getX(), -camara.getY());
        double size = BasicBlock.getSize();
        if (bloquesVisibles != null) {
            for (BasicBlock b : bloquesVisibles) {
                Rectangle r = new Rectangle((int)b.getBounds().getX(), (int)b.getBounds().getY(), (int)size, (int)size);
                int tileX = (int)Math.floor(r.x / size);
                int tileYTop = (int)Math.floor(r.y / size);
                int arrY = (lightGrid != null) ? lightGrid.getHeight() - 1 - tileYTop : -1;
                double brightness = 1.0;
                int sky = 0, block = 0, eff = 15;
                if (lightGrid != null && !lightGrid.isEmpty() &&
                        arrY >= 0 && arrY < lightGrid.getHeight() &&
                        tileX >= 0 && tileX < lightGrid.getWidth()) {
                    sky = lightGrid.getSkylight(arrY, tileX);
                    block = lightGrid.getBlockLight(arrY, tileX);
                    eff = lightGrid.getEffectiveLight(arrY, tileX);
                    brightness = getVisualBrightness(lightGrid, tileX, tileYTop);
                }
                b.drawTinted(g, brightness);
                if (debugLight && lightGrid != null && !lightGrid.isEmpty() &&
                        arrY >= 0 && arrY < lightGrid.getHeight() &&
                        tileX >= 0 && tileX < lightGrid.getWidth()) {
                    String s = sky + "/" + block + "(" + eff + ")";
                    Font prev = g.getFont();
                    Font f = prev.deriveFont(Font.BOLD, (float)(size * 0.4));
                    g.setFont(f);
                    FontMetrics fm = g.getFontMetrics();
                    int tx = r.x + (r.width - fm.stringWidth(s)) / 2;
                    int ty = r.y + (r.height + fm.getAscent() - fm.getDescent()) / 2;
                    g.setColor(new Color(0,0,0,170));
                    g.drawString(s, tx+1, ty+1);
                    g.setColor(Color.WHITE);
                    g.drawString(s, tx, ty);
                    g.setFont(prev);
                }
            }
        }
        if (jugador != null) jugador.draw(g);
        // Borde por hover (negro si hay bloque, gris claro y fino si es aire)
        if (editorMundo != null && editorMundo.isHoveringInteractable()) {
            int htx = editorMundo.getHoverTileX();
            int hty = editorMundo.getHoverTileY();
            if (htx != Integer.MIN_VALUE && hty != Integer.MIN_VALUE) {
                double bx = htx * size;
                double by = hty * size;
                boolean hasBlock = editorMundo.hoverHasBlock();
                if (hasBlock) {
                    g.setColor(Color.BLACK);
                    g.setStroke(new BasicStroke(2.5f));
                } else {
                    g.setColor(new Color(220, 220, 220));
                    g.setStroke(new BasicStroke(1.2f));
                }
                g.drawRect((int)bx, (int)by, (int)size, (int)size);
            }
        }
        // Feedback de rotura (borde más grueso + barra de progreso)
        if (editorMundo != null && editorMundo.isBreaking()) {
            int tx = editorMundo.getTargetTileX();
            int ty = editorMundo.getTargetTileY();
            if (tx != Integer.MIN_VALUE && ty != Integer.MIN_VALUE) {
                double bx = tx * size;
                double by = ty * size;
                g.setColor(Color.BLACK);
                g.setStroke(new BasicStroke(3f));
                g.drawRect((int)bx, (int)by, (int)size, (int)size);
                double progress = editorMundo.getBreakProgress();
                int barPadding = 4;
                int barHeight = 8;
                int barWidth = (int)size - barPadding * 2;
                int barX = (int)bx + barPadding;
                int barY = (int)by - barHeight - 4;
                g.setColor(new Color(0,0,0,150));
                g.fillRect(barX, barY, barWidth, barHeight);
                int filled = (int)(barWidth * progress);
                g.setColor(new Color(255, 215, 0, 220));
                g.fillRect(barX, barY, filled, barHeight);
                g.setColor(Color.BLACK);
                g.setStroke(new BasicStroke(2f));
                g.drawRect(barX, barY, barWidth, barHeight);
            }
        }
        g.setTransform(old);
    }

    /**
     * Calcula el brillo visual suavizado (promedio 3x3) sin modificar la cuadrícula de luz.
     * Solo se usa para tintar sprites; otros cálculos (debug, lógica) siguen usando valores brutos.
     */
    private double getVisualBrightness(Lighting.LightGrid grid, int tileX, int tileYTop) {
        if (grid == null || grid.isEmpty()) return 1.0;
        int centerArrY = grid.getHeight() - 1 - tileYTop;
        if (tileX < 0 || tileX >= grid.getWidth() || centerArrY < 0 || centerArrY >= grid.getHeight()) return 1.0;
        int centerLight = grid.getEffectiveLight(centerArrY, tileX);
        int sum = 0; int count = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = tileX + dx;
                int nyTop = tileYTop + dy;
                int nArrY = grid.getHeight() - 1 - nyTop;
                if (nx < 0 || nx >= grid.getWidth()) continue;
                if (nArrY < 0 || nArrY >= grid.getHeight()) continue;
                sum += grid.getEffectiveLight(nArrY, nx);
                count++;
            }
        }
        double avg = (count == 0) ? centerLight / 15.0 : (sum / (double)count) / 15.0;
        // Nunca oscurecer: usar el máximo entre el brillo original del bloque y el suavizado
        double base = centerLight / 15.0;
        double result = Math.max(base, avg);
        if (result < 0) result = 0; else if (result > 1) result = 1;
        return result;
    }
}
