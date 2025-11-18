package juego.bloques;

import tipos.Punto;

/** Bloque indestructible usado en la capa base del mundo. */
public class BedrockBlock extends BasicBlock {
    public BedrockBlock(Punto p) {
        super(BlockType.BEDROCK, p);
    }

    @Override
    public double getDureza() {
        return Double.POSITIVE_INFINITY;
    }

    @Override
    public boolean isBreakable() {
        return false;
    }
}

