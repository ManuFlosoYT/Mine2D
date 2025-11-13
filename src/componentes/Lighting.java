package componentes;

import juego.bloques.BasicBlock;
import juego.bloques.BlockType;

/**
 * Calcula niveles de iluminación básica por columna (skylight):
 * - El primer bloque con vista al cielo en cada columna recibe nivel 15.
 * - Cada celda por debajo reduce el nivel en 2; si la celda es agua, reduce en 4.
 * - Una vez a 0, permanece 0 hacia abajo.
 *
 * La salida es una matriz int[][] con el mismo tamaño que el mundo [arrayY][x].
 * Los niveles se almacenan para todas las celdas; en debug se mostrarán solo en bloques.
 */
public final class Lighting {
    private Lighting() {}

    public static int[][] computeSkyLight(BasicBlock[][] mundo) {
        if (mundo == null || mundo.length == 0) return new int[0][0];
        int alto = mundo.length;
        int ancho = mundo[0].length;
        int[][] light = new int[alto][ancho];
        for (int x = 0; x < ancho; x++) {
            int level = 0;
            boolean encounteredAny = false; // se pondrá true al encontrar el primer bloque no-nulo
            for (int y = alto - 1; y >= 0; y--) { // de arriba hacia abajo (arrayY top-based)
                BasicBlock cell = mundo[y][x];
                if (!encounteredAny) {
                    if (cell != null) {
                        // Primer bloque bajo el cielo
                        level = 15;
                        encounteredAny = true;
                        light[y][x] = level;
                    } else {
                        light[y][x] = 0; // aire superior sin bloque
                    }
                } else {
                    // Propagar hacia abajo: restar 2 por celda; si la celda es agua, restar 4
                    int drop = (cell != null && cell.getType() == BlockType.WATER) ? 4 : 2;
                    level = Math.max(0, level - drop);
                    light[y][x] = level;
                }
            }
        }
        return light;
    }
}
