package juego.bloques;

import tipos.Punto;

/**
 * Bloque de agua: no se puede destruir con la herramienta de rotura, pero puede ser reemplazado al construir.
 */
public class WaterBlock extends BasicBlock {
    public WaterBlock(Punto p) {
        super(BlockType.WATER, p);
    }

    /** Agua no es rompible mediante la lógica estándar. */
    @Override
    public double getDureza() {
        return Double.POSITIVE_INFINITY; // evita que se alcance la dureza
    }

    /** Indica que no es breakable. */
    @Override
    public boolean isBreakable() { return false; }
}

