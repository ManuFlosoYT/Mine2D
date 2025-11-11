package componentes;

import juego.bloques.BasicBlock;
import tipos.Punto;

/**
 * Contenedor simple para devolver mundo y posición del jugador al cargar.
 */
public class WorldData {
    private final BasicBlock[][] mundo;
    private final Punto jugadorPos;

    public WorldData(BasicBlock[][] mundo, Punto jugadorPos) {
        this.mundo = mundo;
        this.jugadorPos = jugadorPos;
    }

    public BasicBlock[][] mundo() { return mundo; }
    public Punto jugadorPos() { return jugadorPos; }
}

