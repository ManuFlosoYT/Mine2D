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
                         int[][] lightGrid,
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
                b.draw(g);
                if (debugLight && lightGrid != null && lightGrid.length > 0) {
                    // Calcular índice del tile del bloque
                    Rectangle r = new Rectangle((int)b.getBounds().getX(), (int)b.getBounds().getY(), (int)size, (int)size);
                    int tileX = (int)Math.floor(r.x / size);
                    int tileYTop = (int)Math.floor(r.y / size);
                    int arrY = lightGrid.length - 1 - tileYTop;
                    if (arrY >= 0 && arrY < lightGrid.length && tileX >= 0 && tileX < lightGrid[0].length) {
                        int val = lightGrid[arrY][tileX];
                        // Dibujar número centrado
                        String s = Integer.toString(val);
                        Font prev = g.getFont();
                        Font f = prev.deriveFont(Font.BOLD, (float)(size * 0.4));
                        g.setFont(f);
                        FontMetrics fm = g.getFontMetrics();
                        int tx = r.x + (r.width - fm.stringWidth(s)) / 2;
                        int ty = r.y + (r.height + fm.getAscent() - fm.getDescent()) / 2;
                        // Contorno para legibilidad
                        g.setColor(new Color(0,0,0,170));
                        g.drawString(s, tx+1, ty+1);
                        g.setColor(Color.WHITE);
                        g.drawString(s, tx, ty);
                        g.setFont(prev);
                    }
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
}
