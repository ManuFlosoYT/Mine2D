package componentes;

import juego.Jugador;
import juego.bloques.BasicBlock;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilidades para consultas espaciales sobre la rejilla del mundo.
 *
 * <p>Convierte entre coordenadas de mundo (píxeles) y coordenadas de tile (enteros top-based)
 * y ofrece métodos para obtener subconjuntos relevantes de bloques.</p>
 */
public final class MundoHelper {
    private MundoHelper() {}

    /**
     * Obtiene los bloques cercanos al rectángulo del jugador para cálculos de colisión.
     *
     * <p>Calcula un bounding box en tiles alrededor del jugador con un margen configurable y
     * extrae bloques no nulos de la rejilla, realizando la conversión de índices top-based
     * (arrayY=0 en la parte inferior) a coordenadas de tile con origen en la parte superior.</p>
     *
     * @param mundo matriz de bloques [arrayY][x] (arrayY=0 es la fila inferior)
     * @param jugador entidad jugador con sus bounds en píxeles
     * @param margenTiles margen adicional en tiles alrededor de los bounds
     * @return lista de bloques próximos potencialmente colisionables
     */
    public static List<BasicBlock> obtenerBloquesCercanosJugador(BasicBlock[][] mundo, Jugador jugador, int margenTiles){
        List<BasicBlock> lista = new ArrayList<>();
        if (mundo == null || jugador == null) return lista;
        Rectangle2D pb = jugador.getBounds();
        double size = BasicBlock.getSize();
        int minTileX = Math.max(0, (int)Math.floor(pb.getX() / size) - margenTiles);
        int maxTileX = (int)Math.floor((pb.getX() + pb.getWidth()) / size) + margenTiles;
        int minTileY = Math.max(0, (int)Math.floor(pb.getY() / size) - margenTiles);
        int maxTileY = (int)Math.floor((pb.getY() + pb.getHeight()) / size) + margenTiles;
        if (mundo.length == 0) return lista;
        int maxYTopBased = mundo.length - 1; // número de tiles en Y - 1 (coordenada top-based)
        int maxX = mundo[0].length - 1;
        if (maxTileX > maxX) maxTileX = maxX;
        if (maxTileY > maxYTopBased) maxTileY = maxYTopBased;
        for (int yTop = minTileY; yTop <= maxTileY; yTop++) {
            int arrY = mundo.length - 1 - yTop; // convertir a índice de array (0 abajo)
            if (arrY < 0 || arrY >= mundo.length) continue;
            BasicBlock[] fila = mundo[arrY];
            for (int x = minTileX; x <= maxTileX; x++) {
                BasicBlock b = fila[x];
                if (b != null) lista.add(b);
            }
        }
        return lista;
    }

    /**
     * Recalcula el conjunto de bloques visibles en base a la cámara y dimensiones del viewport.
     *
     * @param destino lista donde se almacenarán los bloques visibles (se limpia al inicio)
     * @param mundo rejilla de bloques [arrayY][x]
     * @param camara cámara con posición en píxeles del mundo
     * @param anchoPx ancho del viewport en píxeles
     * @param altoPx alto del viewport en píxeles
     */
    public static void actualizarBloquesVisibles(List<BasicBlock> destino, BasicBlock[][] mundo, Camara camara, int anchoPx, int altoPx){
        destino.clear();
        if (mundo == null || camara == null || mundo.length == 0) return;
        double size = BasicBlock.getSize();
        int colsVisible = (int) Math.ceil(anchoPx / size) + 2;
        int rowsVisible = (int) Math.ceil(altoPx / size) + 2;
        int startX = (int)Math.floor(camara.getX() / size);
        int startYTop = (int)Math.floor(camara.getY() / size); // 0 arriba
        int endX = Math.min(mundo[0].length, startX + colsVisible);
        int endYTop = Math.min(mundo.length, startYTop + rowsVisible);
        for (int yTop = startYTop; yTop < endYTop; yTop++) {
            int arrY = mundo.length - 1 - yTop; // índice de array (0 abajo)
            if (arrY < 0 || arrY >= mundo.length) continue;
            BasicBlock[] fila = mundo[arrY];
            for (int x = startX; x < endX; x++) {
                if (x < 0 || x >= fila.length) continue;
                BasicBlock b = fila[x];
                if (b != null) destino.add(b);
            }
        }
    }
}
