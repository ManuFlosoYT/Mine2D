package componentes;

import juego.bloques.BasicBlock;
import tipos.Punto;

/**
 * Utilidad para crear una rejilla de bloques inicial del mundo.
 *
 * <p>La matriz resultante se indexa como [arrayY][x], donde arrayY=0 corresponde a la fila inferior.
 * Cada celda contiene un {@link BasicBlock} o null (aire).</p>
 */
public class GeneradorMundo {

    /**
     * Genera un mundo rectangular de tamaño dado. Primero rellena con piedra hasta Y=64 (inclusive) y después,
     * para cada columna, toma el bloque con "vista al cielo" (el más alto no-nulo) y lo convierte a grass_block,
     * y sus 3 bloques inferiores a dirt. El resto permanece como stone.
     * @param ancho número de columnas de bloques
     * @param alto número de filas de bloques
     * @param offsetX desplazamiento X en píxeles para posicionar el mundo
     * @param offsetY desplazamiento Y en píxeles para posicionar el mundo
     * @return matriz de bloques [arrayY][x] (arrayY=0 es la fila inferior)
     */
    public static BasicBlock[][] generar(int ancho, int alto, double offsetX, double offsetY) {
        // Clamp mínimo requerido
        if (ancho < 256) ancho = 256;
        if (alto < 128) alto = 128;
        BasicBlock[][] mundo = new BasicBlock[alto][ancho]; // [arrayY][x] con arrayY=0 abajo
        final double size = BasicBlock.getSize();

        // 1) Rellenar con piedra hasta Y=64 inclusive (clamp al tamaño disponible)
        int toY = Math.min(64, alto - 1);
        for (int arrayY = 0; arrayY < alto; arrayY++) {
            for (int x = 0; x < ancho; x++) {
                if (arrayY <= toY) {
                    Punto p = new Punto(offsetX + x * size, offsetY + (alto - 1 - arrayY) * size);
                    mundo[arrayY][x] = new BasicBlock("stone", p);
                } else {
                    mundo[arrayY][x] = null; // aire por encima
                }
            }
        }

        // 2) Para cada columna, localizar la "superficie" (primer bloque no-nulo desde arriba)
        for (int x = 0; x < ancho; x++) {
            int top = -1;
            for (int arrayY = alto - 1; arrayY >= 0; arrayY--) {
                if (mundo[arrayY][x] != null) { top = arrayY; break; }
            }
            if (top < 0) continue; // columna vacía

            // Sustituir el bloque superior por grass_block
            Punto pTop = new Punto(offsetX + x * size, offsetY + (alto - 1 - top) * size);
            mundo[top][x] = new BasicBlock("grass_block", pTop);

            // Y los 3 inferiores por dirt (si existen)
            for (int i = 1; i <= 3; i++) {
                int yb = top - i;
                if (yb < 0) break;
                Punto pb = new Punto(offsetX + x * size, offsetY + (alto - 1 - yb) * size);
                mundo[yb][x] = new BasicBlock("dirt", pb);
            }
        }

        return mundo;
    }
}
