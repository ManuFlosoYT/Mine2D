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
     * Genera un mundo rectangular de tamaño dado, con capas simples de piedra/tierra/césped.
     * @param ancho número de columnas de bloques
     * @param alto número de filas de bloques
     * @param offsetX desplazamiento X en píxeles para posicionar el mundo
     * @param offsetY desplazamiento Y en píxeles para posicionar el mundo
     * @return matriz de bloques [arrayY][x] (arrayY=0 es la fila inferior)
     */
    public static BasicBlock[][] generar(int ancho, int alto, double offsetX, double offsetY) {
        BasicBlock[][] mundo = new BasicBlock[alto][ancho]; // [arrayY][x] con arrayY=0 abajo
        final double size = BasicBlock.getSize();

        for (int arrayY = 0; arrayY < alto; arrayY++) {
            for (int x = 0; x < ancho; x++) {
                BasicBlock bloque;
                Punto p = new Punto(offsetX + x * size, offsetY + (alto - 1 - arrayY) * size);
                if (arrayY <= 60) {
                    bloque = new BasicBlock("stone", p);
                } else if (arrayY <= 63) { // 61..63
                    bloque = new BasicBlock("dirt", p);
                } else if (arrayY == 64) {
                    bloque = new BasicBlock("grass_block", p);
                } else {
                    bloque = null; // aire
                }
                mundo[arrayY][x] = bloque;
            }
        }
        return mundo;
    }
}
