package juego.bloques;

import java.awt.geom.Rectangle2D;
import tipos.Punto;

/**
 * Bloque básico del mundo.
 *
 * <p>Cada bloque tiene un identificador (ID) que determina su tipo y su dureza.
 * Su posición se almacena en píxeles del mundo.</p>
 */
public class BasicBlock {

    private static final double SIZE = 64; // tamaño del sprite en píxeles
    private final Punto p;
    private final String blockID;
    private final double dureza; // segundos necesarios de mantener click para romper
    private final BlockType type;

    /**
     * Crea un bloque de tipo dado en la posición indicada.
     * @param blockID identificador (p.ej. "stone", "dirt")
     * @param p posición en píxeles del mundo (esquina superior izquierda del bloque)
     */
    public BasicBlock(String blockID, Punto p) {
        this(BlockType.fromId(blockID), p);
    }

    /**
     * Crea un bloque desde su tipo enum.
     */
    public BasicBlock(BlockType type, Punto p) {
        this.type = (type != null) ? type : BlockType.UNKNOWN;
        this.blockID = this.type.getId();
        this.p = p;
        this.dureza = this.type.getHardness();
    }

    /** Identificador de tipo de bloque (p.ej. "stone", "dirt"). */
    public String getId() { return blockID; }

    /** Tipo del bloque. */
    public BlockType getType() { return type; }

    /** Tamaño del bloque en píxeles de lado. */
    public static double getSize() { return SIZE; }

    /** Dureza en segundos necesarios de "minado" continuo para romper el bloque. */
    public double getDureza() { return dureza; }

    /** Por defecto los bloques son rompibles. */
    public boolean isBreakable() { return true; }

    // --- Nuevos helpers de colisión ---
    /** Rectángulo de colisión del bloque en píxeles del mundo. */
    public Rectangle2D getBounds() { return new Rectangle2D.Double(p.x(), p.y(), SIZE, SIZE); }

    // --- Soporte para subclases: acceso protegido a la posición ---
    public double getX() { return p.x(); }
    public double getY() { return p.y(); }
}
