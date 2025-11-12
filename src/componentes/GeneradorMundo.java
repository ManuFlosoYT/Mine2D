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
        // Clamp mínimo requerido
        if (ancho < 256) ancho = 256;
        if (alto < 128) alto = 128;
        BasicBlock[][] mundo = new BasicBlock[alto][ancho]; // [arrayY][x] con arrayY=0 abajo
        final double size = BasicBlock.getSize();

        // --- Perfil de terreno suave por columna (tres octavas, variación moderada) ---
        double base = Math.max(8, alto * 0.5); // centro del terreno
        // Amplitudes moderadas
        double amp1 = Math.max(2, alto * 0.05);
        double amp2 = Math.max(1, alto * 0.015);
        double amp3 = Math.max(1, alto * 0.0075);
        // Periodos más cortos que antes (más variación), pero aún largos
        double period1 = Math.max(48.0, ancho / 3.0);
        double period2 = Math.max(96.0, ancho / 1.5);
        double period3 = Math.max(192.0, ancho);
        // Fases fijas para reproducibilidad
        double phi1 = 0.0;
        double phi2 = 1.3;
        double phi3 = 2.7;

        int[] hTop = new int[ancho]; // altura superior por columna en coordenada arrayY
        for (int x = 0; x < ancho; x++) {
            double t = 2 * Math.PI;
            double h = base
                    + amp1 * Math.sin(t * (x / period1) + phi1)
                    + amp2 * Math.sin(t * (x / period2) + phi2)
                    + amp3 * Math.sin(t * (x / period3) + phi3);
            int hi = (int) Math.round(h);
            if (hi < 4) hi = 4; // evitar terreno demasiado bajo
            if (hi > alto - 1) hi = alto - 1; // no sobrepasar el mundo
            hTop[x] = hi;
        }

        // Pasada de suavizado (kernel 1-2-1) para eliminar cambios bruscos
        if (ancho >= 3) {
            int[] smooth = new int[ancho];
            for (int x = 0; x < ancho; x++) {
                int left = hTop[Math.max(0, x - 1)];
                int mid = hTop[x];
                int right = hTop[Math.min(ancho - 1, x + 1)];
                smooth[x] = Math.round((left + 2 * mid + right) / 4.0f);
            }
            hTop = smooth;
        }

        // 1) Rellenar con piedra hasta la altura hTop[x] incluida; aire por encima
        for (int arrayY = 0; arrayY < alto; arrayY++) {
            for (int x = 0; x < ancho; x++) {
                if (arrayY <= hTop[x]) {
                    Punto p = new Punto(offsetX + x * size, offsetY + (alto - 1 - arrayY) * size);
                    mundo[arrayY][x] = new BasicBlock("stone", p);
                } else {
                    mundo[arrayY][x] = null; // aire
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
