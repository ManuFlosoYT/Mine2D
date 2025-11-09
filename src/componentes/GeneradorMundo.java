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
                BasicBlock bloque = null;
                if (y == 64) {
                    bloque = new BasicBlock("grass_block", new Punto(offsetX + x * size, offsetY + y * size));
                } else if (y >= 65 && y <= 67) {
                    bloque = new BasicBlock("dirt", new Punto(offsetX + x * size, offsetY + y * size));
                } else if (y >= 68) {
                    bloque = new BasicBlock("stone", new Punto(offsetX + x * size, offsetY + y * size));
                } else {
                    bloque = null; // aire
                }
                mundo[y][x] = bloque;
            }
        }
        return mundo;
    }
}
