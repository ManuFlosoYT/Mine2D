package juego.bloques;

/**
 * Enum que define los tipos de bloque disponibles y su dureza (segundos para romper).
 */
public enum BlockType {
    STONE("stone", 1.5),
    DIRT("dirt", 0.8),
    SAND("sand", 0.4),
    GRASS_BLOCK("grass_block", 0.6),
    WATER("water", 0.2),
    UNKNOWN("unknown", 1.0);

    private final String id;
    private final double hardness;

    BlockType(String id, double hardness) {
        this.id = id;
        this.hardness = hardness;
    }

    /** Identificador textual usado para cargar el sprite. */
    public String getId() { return id; }
    /** Dureza (segundos de minado continuo). */
    public double getHardness() { return hardness; }

    /**
     * Obtiene el tipo a partir de su id. Si no existe, devuelve UNKNOWN.
     */
    public static BlockType fromId(String id) {
        if (id == null) return UNKNOWN;
        String norm = id.toLowerCase();
        for (BlockType t : values()) {
            if (t.id.equals(norm)) return t;
        }
        return UNKNOWN;
    }
}
