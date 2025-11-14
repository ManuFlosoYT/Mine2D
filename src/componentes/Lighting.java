package componentes;

import juego.bloques.BasicBlock;
import juego.bloques.BlockType;

import java.util.ArrayDeque;

/**
 * Calcula y almacena dos canales de iluminación (skylight y block light).
 * La skylight parte con nivel 15 en las columnas con visión directa al cielo
 * y se propaga en las cuatro direcciones ortogonales reduciendo 1 por bloque
 * (equivalente a la distancia Manhattan a cualquier columna con luz de cielo).
 * La luz de bloque queda en 0 por ahora, pero se expone para futuros emisores.
 */
public final class Lighting {
    private static final int SKY_MAX = 15;
    private static final int[][] DIRS = { {1,0}, {-1,0}, {0,1}, {0,-1} };

    private Lighting() {}

    /** Calcula ambas luces y devuelve una cuadrícula con los resultados. */
    public static LightGrid computeLightGrid(BasicBlock[][] mundo) {
        if (mundo == null || mundo.length == 0 || mundo[0] == null || mundo[0].length == 0) {
            return LightGrid.empty();
        }
        int alto = mundo.length;
        int ancho = mundo[0].length;
        int[][] skylight = new int[alto][ancho];
        int[][] blockLight = new int[alto][ancho]; // permanece en 0 por ahora
        ArrayDeque<Node> queue = new ArrayDeque<>();

        // Inicializar fuentes (columnas con vista directa al cielo)
        for (int x = 0; x < ancho; x++) {
            boolean blocked = false;
            for (int y = alto - 1; y >= 0; y--) {
                if (blocked) break;
                skylight[y][x] = SKY_MAX;
                queue.add(new Node(x, y, SKY_MAX));
                if (mundo[y][x] != null) {
                    blocked = true; // primer bloque corta la vista para el resto de la columna
                }
            }
        }

        // Propagación BFS reduciendo 1 por bloque ortogonal
        while (!queue.isEmpty()) {
            Node current = queue.poll();
            BasicBlock currentBlock = mundo[current.y][current.x];
            if (isSolid(currentBlock)) {
                continue; // no propagamos a través de bloques sólidos
            }
            for (int[] dir : DIRS) {
                int nx = current.x + dir[0];
                int ny = current.y + dir[1];
                if (nx < 0 || nx >= ancho || ny < 0 || ny >= alto) continue;
                int nextLevel = current.level - 1;
                if (nextLevel <= 0) continue;
                if (nextLevel <= skylight[ny][nx]) continue;
                skylight[ny][nx] = nextLevel;
                if (!isSolid(mundo[ny][nx])) {
                    queue.add(new Node(nx, ny, nextLevel));
                }
            }
        }

        return new LightGrid(skylight, blockLight);
    }

    private record Node(int x, int y, int level) {}

    /** Contenedor inmutable de los valores de luz por celda. */
    public static final class LightGrid {
        private final int[][] skylight;
        private final int[][] blockLight;

        private LightGrid(int[][] skylight, int[][] blockLight) {
            this.skylight = skylight;
            this.blockLight = blockLight;
        }

        public static LightGrid empty() {
            return new LightGrid(new int[0][0], new int[0][0]);
        }

        public int getHeight() { return skylight.length; }
        public int getWidth() { return skylight.length == 0 ? 0 : skylight[0].length; }

        public int[][] getSkylightArray() { return skylight; }
        public int[][] getBlockLightArray() { return blockLight; }

        public int getSkylight(int arrayY, int x) {
            if (arrayY < 0 || arrayY >= skylight.length) return 0;
            if (skylight.length == 0 || x < 0 || x >= skylight[0].length) return 0;
            return skylight[arrayY][x];
        }

        public int getBlockLight(int arrayY, int x) {
            if (arrayY < 0 || arrayY >= blockLight.length) return 0;
            if (blockLight.length == 0 || x < 0 || x >= blockLight[0].length) return 0;
            return blockLight[arrayY][x];
        }

        /** Devuelve el nivel final usado para iluminar (máximo entre ambos canales). */
        public int getEffectiveLight(int arrayY, int x) {
            return Math.max(getSkylight(arrayY, x), getBlockLight(arrayY, x));
        }

        public boolean isEmpty() { return skylight.length == 0 || skylight[0].length == 0; }
    }

    private static boolean isSolid(BasicBlock block) {
        if (block == null) return false;
        BlockType type = block.getType();
        return type != BlockType.WATER;
    }
}
