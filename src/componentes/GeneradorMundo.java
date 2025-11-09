package componentes;

import juego.bloques.BasicBlock;
import tipos.Punto;

public class GeneradorMundo {

    public static BasicBlock[][] generar(int ancho, int alto) {
        return generar(ancho, alto, 0, 0);
    }

    public static BasicBlock[][] generar(int ancho, int alto, double offsetX, double offsetY) {
        BasicBlock[][] mundo = new BasicBlock[alto][ancho]; // [fila(y)][col(x)]
        final double size = BasicBlock.getSize();

        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                if (y >= 64) { // parte inferior sólida
                    mundo[y][x] = new BasicBlock("stone", new Punto(offsetX + x * size, offsetY + y * size));
                } else { // a partir de 64 hacia arriba: aire
                    mundo[y][x] = null;
                }
            }
        }
        return mundo;
    }
}
