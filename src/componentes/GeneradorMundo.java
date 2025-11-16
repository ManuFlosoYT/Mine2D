package componentes;

import juego.bloques.BasicBlock;
import juego.bloques.WaterBlock;
import juego.mundo.Chunk;
import tipos.Punto;

import java.util.HashMap;
import java.util.Map;

/**
 * Utilidad para rellenar un chunk con el terreno del mundo.
 */
public class GeneradorMundo {

    private static final int ALTURA_GEN_MEDIA = 55;

    private static final int[] SEGMENTS = {
            Math.max(2, 1024 / 192), // Longer, smoother base segments
            Math.max(4, 1024 / 128),
            Math.max(8, 1024 / 64),
            Math.max(16, 1024 / 32),
            Math.max(32, 1024 / 16)
    };
    private static final double[] AMPLITUDES_BASE = {
            256 * 0.04,  // Reduced amplitudes for flatter terrain
            256 * 0.03,
            256 * 0.02,
            256 * 0.01,
            256 * 0.005
    };

    private static final Map<Long, double[][]> OCTAVES_BY_SEED = new HashMap<>();

    private static double[][] getOctaves(long worldSeed) {
        double[][] cached = OCTAVES_BY_SEED.get(worldSeed);
        if (cached != null) return cached;
        java.util.Random rand = new java.util.Random(worldSeed);
        double[][] octaveValues = new double[SEGMENTS.length][];
        for (int o = 0; o < SEGMENTS.length; o++) {
            int segCount = SEGMENTS[o] + 1;
            double[] vals = new double[segCount];
            for (int i = 0; i < segCount; i++) {
                double r = rand.nextDouble();
                double v = (Math.pow(r, 0.6) * 2.0) - 1.0;
                vals[i] = v;
            }
            octaveValues[o] = vals;
        }
        OCTAVES_BY_SEED.put(worldSeed, octaveValues);
        return octaveValues;
    }

    /**
     * Rellena un chunk dado con terreno generado proceduralmente.
     * La generación es determinista basada en la semilla del mundo y las coordenadas del chunk.
     *
     * Sistema de coordenadas: Y = 0 está en el fondo del mundo y crece hacia ARRIBA.
     */
    public static void generarChunk(Chunk chunk, long worldSeed) {
        final int WATER_LEVEL = 63;          // altura (en bloques) del agua por encima del fondo
        final int ancho = Chunk.CHUNK_SIZE;  // ancho del chunk en bloques
        final int alto = Chunk.CHUNK_SIZE;   // alto del chunk en bloques
        // Altura "global" usada solo para escalar el ruido vertical (no recorta el mundo)
        final int worldHeight = 256;

        final double size = BasicBlock.getSize();
        // Offset en coordenadas de mundo (bloques) para este chunk
        double offsetX = chunk.chunkX * ancho * size;
        double offsetY = chunk.chunkY * alto * size;

        // Perfil base de terreno (altura medida desde el fondo, Y=0 abajo)
        double baseFrac = (double) ALTURA_GEN_MEDIA / worldHeight;
        double base = worldHeight * baseFrac; // altura base en bloques desde el fondo

        double[][] octaveValues = getOctaves(worldSeed);

        // hTop[x] = altura del terreno en bloques desde el fondo (0 abajo, worldHeight arriba)
        int[] hTop = new int[ancho];
        for (int x = 0; x < ancho; x++) {
            double worldX = chunk.chunkX * ancho + x; // coordenada X global en bloques (puede ser negativa)
            double h = base;
            // Normalizamos sobre un ancho base, pero luego aplicamos modulo para hacerlo infinito y evitar índices negativos
            for (int o = 0; o < SEGMENTS.length; o++) {
                int segs = SEGMENTS[o];
                // pos original (sin modulo) usando un ancho base 1024.0
                double pos = (worldX / 1024.0) * segs;
                // Evitar índices fuera de rango: hacer wrap cíclico
                pos = pos % segs;
                if (pos < 0) pos += segs; // garantizar [0,segs)
                int i0 = (int) Math.floor(pos);           // 0 .. segs-1
                int i1 = (i0 + 1);                        // 1 .. segs
                if (i1 >= segs) i1 = 0;                   // Wrap around for seamless terrain
                double t = pos - i0;                      // fracción local
                double tt = t * t * (3 - 2 * t);          // suavizado hermite
                double v0 = octaveValues[o][i0];
                double v1 = octaveValues[o][i1]; // i1 is now wrapped, safe
                double v = v0 + (v1 - v0) * tt;
                double amp = AMPLITUDES_BASE[o];
                h += v * amp;
            }
            if (h < 4) h = 4;
            if (h > worldHeight - 1) h = worldHeight - 1;
            hTop[x] = (int) Math.round(h);
        }

        // hTop calculado
        // Limpiar primero el chunk (aire)
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                chunk.setBlockGenerated(x, y, null);
            }
        }

        // Piedra profunda hasta top-4
        for (int x = 0; x < ancho; x++) {
            int topWorldY = hTop[x];
            int stoneMax = topWorldY - 4; // deja espacio para 1 grass + 3 dirt
            if (stoneMax < 0) stoneMax = -1; // si top <4 no hay piedra superficial
            for (int y = 0; y < alto; y++) {
                int worldY = chunk.chunkY * alto + y;
                if (worldY <= stoneMax) {
                    double screenY = (worldHeight - 1 - worldY) * size;
                    Punto p = new Punto(offsetX + x * size, screenY);
                    chunk.setBlockGenerated(x, y, new BasicBlock("stone", p));
                }
            }
        }

        // Capa superficial: 3 dirt y 1 grass
        for (int x = 0; x < ancho; x++) {
            int topWorldY = hTop[x];
            int topChunkY = topWorldY - chunk.chunkY * alto;
            if (topChunkY >= 0 && topChunkY < alto) {
                // grass
                double screenYTop = (worldHeight - 1 - topWorldY) * size;
                Punto pTop = new Punto(offsetX + x * size, screenYTop);
                chunk.setBlockGenerated(x, topChunkY, new BasicBlock("grass_block", pTop));
            }
            // dirt debajo
            for (int i = 1; i <= 3; i++) {
                int dirtWorldY = topWorldY - i;
                int dirtChunkY = dirtWorldY - chunk.chunkY * alto;
                if (dirtChunkY >= 0 && dirtChunkY < alto && dirtWorldY >= 0) {
                    double screenYDirt = (worldHeight - 1 - dirtWorldY) * size;
                    Punto pDirt = new Punto(offsetX + x * size, screenYDirt);
                    chunk.setBlockGenerated(x, dirtChunkY, new BasicBlock("dirt", pDirt));
                }
            }
        }

        // Agua: rellenar huecos hasta WATER_LEVEL (solo si está vacío)
        for (int y = 0; y < alto; y++) {
            int worldY = chunk.chunkY * alto + y;
            if (worldY <= WATER_LEVEL) {
                for (int x = 0; x < ancho; x++) {
                    if (chunk.getBlock(x, y) == null) {
                        double screenY = (worldHeight - 1 - worldY) * size;
                        Punto p = new Punto(offsetX + x * size, screenY);
                        chunk.setBlockGenerated(x, y, new WaterBlock(p));
                    }
                }
            }
        }
    }

    public static void generarFeaturesAdicionales(Chunk chunk, juego.mundo.Mundo mundo) {
        final int ancho = Chunk.CHUNK_SIZE;
        final int alto = Chunk.CHUNK_SIZE;
        final int worldHeight = 256;
        final double size = BasicBlock.getSize();

        // Orillas de arena
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                int worldX = chunk.chunkX * ancho + x;
                int worldY = chunk.chunkY * alto + y;

                BasicBlock b = mundo.getBlockAtTile(worldX, worldY);
                if (b == null || "sand".equals(b.getId())) continue;

                boolean nearWater = false;
                for (int dy = -1; dy <= 1 && !nearWater; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        BasicBlock nb = mundo.getBlockAtTile(worldX + dx, worldY + dy);
                        if (nb != null && "water".equals(nb.getId())) {
                            nearWater = true;
                        }
                    }
                }

                if (nearWater && ("dirt".equals(b.getId()) || "grass_block".equals(b.getId()))) {
                    double screenY = (worldHeight - 1 - worldY) * size;
                    double screenX = worldX * size;
                    Punto p = new Punto(screenX, screenY);
                    chunk.setBlockGenerated(x, y, new BasicBlock("sand", p));
                }
            }
        }
    }
}
