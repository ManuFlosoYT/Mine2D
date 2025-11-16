package componentes;

import juego.Jugador;
import juego.bloques.BasicBlock;
import juego.mundo.Mundo;

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
     * @param mundo El objeto Mundo que gestiona los chunks.
     * @param jugador entidad jugador con sus bounds en píxeles
     * @param margenTiles margen adicional en tiles alrededor de los bounds
     * @return lista de bloques próximos potencialmente colisionables
     */
    public static List<BasicBlock> obtenerBloquesCercanosJugador(Mundo mundo, Jugador jugador, int margenTiles){
        List<BasicBlock> lista = new ArrayList<>();
        if (mundo == null || jugador == null) return lista;

        Rectangle2D pb = jugador.getBounds();
        double size = BasicBlock.getSize();

        // Calcular el rango de baldosas del jugador (coordenadas top-based en pantalla)
        int minTileX = (int)Math.floor(pb.getX() / size) - margenTiles;
        int maxTileX = (int)Math.ceil((pb.getX() + pb.getWidth()) / size) + margenTiles;
        int minTileYTop = (int)Math.floor(pb.getY() / size) - margenTiles;
        int maxTileYTop = (int)Math.ceil((pb.getY() + pb.getHeight()) / size) + margenTiles;

        // Iterar sobre el rango y convertir Y-top a Y-mundo (0 abajo)
        for (int tyTop = minTileYTop; tyTop <= maxTileYTop; tyTop++) {
            int tyWorld = (Mundo.WORLD_HEIGHT_BLOCKS - 1) - tyTop;
            for (int tx = minTileX; tx <= maxTileX; tx++) {
                BasicBlock block = mundo.getBlockAtTile(tx, tyWorld);
                if (block != null) lista.add(block);
            }
        }
        return lista;
    }

    /**
     * Recalcula el conjunto de bloques visibles en base a la cámara y dimensiones del viewport.
     *
     * @param destino lista donde se almacenarán los bloques visibles (se limpia al inicio)
     * @param mundo El objeto Mundo que gestiona los chunks.
     * @param camara cámara con posición en píxeles del mundo
     * @param anchoPx ancho del viewport en píxeles
     * @param altoPx alto del viewport en píxeles
     */
    public static void actualizarBloquesVisibles(List<BasicBlock> destino, Mundo mundo, Camara camara, int anchoPx, int altoPx){
        destino.clear();
        if (mundo == null || camara == null) return;

        double size = BasicBlock.getSize();
        // Calcular el rango de baldosas (tiles) visibles en la pantalla
        int startXTile = (int)Math.floor(camara.getX() / size);
        int startYTile = (int)Math.floor(camara.getY() / size);
        int endXTile = startXTile + (int)Math.ceil(anchoPx / size) + 1;
        int endYTile = startYTile + (int)Math.ceil(altoPx / size) + 1;

        // Iterar sobre cada baldosa visible y pedir el bloque al mundo
        for (int ty = startYTile; ty <= endYTile; ty++) {
            for (int tx = startXTile; tx <= endXTile; tx++) {
                // Invertir la coordenada Y de pantalla a Y de mundo
                double worldY = (Mundo.WORLD_HEIGHT_BLOCKS - 1 - ty) * size;
                BasicBlock block = mundo.getBlockAt(tx * size, worldY);
                if (block != null) {
                    destino.add(block);
                }
            }
        }
    }
}
