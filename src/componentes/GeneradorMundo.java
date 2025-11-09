package componentes;

import juego.bloques.BasicBlock;
import tipos.Punto;

public class GeneradorMundo {

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
