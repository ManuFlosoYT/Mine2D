package juego.bloques;

import tipos.Punto;

import java.awt.geom.Rectangle2D;

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

    /**
     * Reducimos la hitbox vertical un poco (shrinkTop) para evitar poder "levitar" sobre el borde superior del agua.
     */
    @Override
    public Rectangle2D getBounds() {
        double size = BasicBlock.getSize();
        double shrinkTop = Math.max(2, size * 0.10); // recorta ~10% (mínimo 2px)
        return new Rectangle2D.Double(getX(), getY() + shrinkTop, size, size - shrinkTop);
    }
}
