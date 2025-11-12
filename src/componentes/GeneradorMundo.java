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
     * Genera un mundo rectangular de tamaño dado con variaciones suaves de altitud.
     * Primero se calcula, para cada columna, una altura de terreno usando la suma de ondas senoidales
     * (tres octavas) para simular ruido suave. Luego se rellena con piedra hasta esa altura y,
     * finalmente, para cada columna se marca la superficie con grass_block y sus tres bloques
     * inferiores con dirt.
     * @param ancho número de columnas de bloques
     * @param alto número de filas de bloques
     * @param offsetX desplazamiento X en píxeles para posicionar el mundo
     * @param offsetY desplazamiento Y en píxeles para posicionar el mundo
     * @return matriz de bloques [arrayY][x] (arrayY=0 es la fila inferior)
     */
    public static BasicBlock[][] generar(int ancho, int alto, double offsetX, double offsetY) {
        final int WATER_LEVEL = 63; // agua hasta Y=63 inclusive
        if (ancho < 256) ancho = 256;
        if (alto < 128) alto = 128;
        BasicBlock[][] mundo = new BasicBlock[alto][ancho];
        final double size = BasicBlock.getSize();

        // Ajustes: bajar ligeramente la base para permitir que aparezcan zonas por debajo del agua.
        // Ahora la base se sitúa al 0.55 de la altura total (antes 0.60) para que la mayoría del terreno quede por encima del agua.
        double baseFrac = 0.55; // antes 0.60
        double base = alto * baseFrac;

        // Más picos: añadir octava extra y subir amplitudes de altas frecuencias.
        int[] segments = {
                Math.max(4, ancho / 96),    // muy larga (menor impacto)
                Math.max(8, ancho / 48),
                Math.max(16, ancho / 24),
                Math.max(32, ancho / 12),
                Math.max(64, ancho / 6)     // nueva octava de detalle fino
        };
        double[] amplitudes = {
                alto * 0.08, // base
                alto * 0.07,
                alto * 0.06,
                alto * 0.05,
                alto * 0.035 // detalle fino
        };

        java.util.Random rand = new java.util.Random(987654321L); // nueva seed para diferente perfil
        double[][] octaveValues = new double[segments.length][];
        for (int o = 0; o < segments.length; o++) {
            int segCount = segments[o] + 1;
            double[] vals = new double[segCount];
            for (int i = 0; i < segCount; i++) {
                // Amplificar variación vertical usando distribución sesgada
                double r = rand.nextDouble();
                double v = (Math.pow(r, 0.6) * 2.0) - 1.0; // sesgo hacia extremos
                vals[i] = v;
            }
            octaveValues[o] = vals;
        }

        int[] hTop = new int[ancho];
        for (int x = 0; x < ancho; x++) {
            double h = base;
            double xf = (double)x / (ancho - 1);
            for (int o = 0; o < segments.length; o++) {
                int segs = segments[o];
                double pos = xf * segs;
                int i0 = (int)Math.floor(pos);
                int i1 = Math.min(segs, i0 + 1);
                double t = pos - i0;
                double tt = t * t * (3 - 2 * t); // smoothstep
                double v0 = octaveValues[o][i0];
                double v1 = octaveValues[o][i1];
                double v = v0 + (v1 - v0) * tt;
                h += v * amplitudes[o];
            }
            if (h < 4) h = 4;
            if (h > alto - 1) h = alto - 1;
            hTop[x] = (int)Math.round(h);
        }

        // Eliminado: no forzar elevación mínima sobre el nivel del agua para permitir lagos / zonas inundadas.

        // Rellenar piedra hasta hTop[x]
        for (int arrayY = 0; arrayY < alto; arrayY++) {
            for (int x = 0; x < ancho; x++) {
                if (arrayY <= hTop[x]) {
                    Punto p = new Punto(offsetX + x * size, offsetY + (alto - 1 - arrayY) * size);
                    mundo[arrayY][x] = new BasicBlock("stone", p);
                } else {
                    mundo[arrayY][x] = null;
                }
            }
        }

        // Capa superficial: grass top + 3 dirt debajo
        for (int x = 0; x < ancho; x++) {
            int top = -1;
            for (int arrayY = alto - 1; arrayY >= 0; arrayY--) {
                if (mundo[arrayY][x] != null) { top = arrayY; break; }
            }
            if (top < 0) continue;
            Punto pTop = new Punto(offsetX + x * size, offsetY + (alto - 1 - top) * size);
            mundo[top][x] = new BasicBlock("grass_block", pTop);
            for (int i = 1; i <= 3; i++) {
                int yb = top - i;
                if (yb < 0) break;
                Punto pb = new Punto(offsetX + x * size, offsetY + (alto - 1 - yb) * size);
                mundo[yb][x] = new BasicBlock("dirt", pb);
            }
        }

        // Agua: rellenar aire desde el fondo hasta WATER_LEVEL (63) inclusive
        int waterMaxArrayY = Math.min(WATER_LEVEL, alto - 1);
        for (int arrayY = 0; arrayY <= waterMaxArrayY; arrayY++) {
            for (int x = 0; x < ancho; x++) {
                if (mundo[arrayY][x] == null) {
                    Punto p = new Punto(offsetX + x * size, offsetY + (alto - 1 - arrayY) * size);
                    mundo[arrayY][x] = new BasicBlock("water", p);
                }
            }
        }
        return mundo;
    }
}
