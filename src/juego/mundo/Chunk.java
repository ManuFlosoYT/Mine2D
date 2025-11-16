package juego.mundo;

import componentes.GeneradorMundo;
import juego.bloques.BasicBlock;

public class Chunk {
    public static final int CHUNK_SIZE = 16;
    private final BasicBlock[][] blocks = new BasicBlock[CHUNK_SIZE][CHUNK_SIZE];
    public final int chunkX;
    public final int chunkY;
    private boolean needsSaving = false;
    private boolean featuresGenerated = false;

    public Chunk(int chunkX, int chunkY) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
    }

    public void generate(long seed) {
        GeneradorMundo.generarChunk(this, seed);
    }

    public BasicBlock getBlock(int x, int y) {
        if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= CHUNK_SIZE) {
            return null;
        }
        return blocks[y][x];
    }

    public void setBlock(int x, int y, BasicBlock block) {
        if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= CHUNK_SIZE) {
            return;
        }
        blocks[y][x] = block;
        needsSaving = true;
    }

    public void markDirty() {
        needsSaving = true;
    }

    // Non-dirty setter used by world generation/loading to avoid unnecessary saves on unload
    public void setBlockGenerated(int x, int y, BasicBlock block) {
        if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= CHUNK_SIZE) {
            return;
        }
        blocks[y][x] = block;
    }

    public BasicBlock[][] getBlocks() {
        return blocks;
    }

    public boolean needsSaving() {
        return needsSaving;
    }

    public void saved() {
        needsSaving = false;
    }

    public boolean needsFeaturesGeneration() {
        return !featuresGenerated;
    }

    public void markFeaturesGenerated() {
        featuresGenerated = true;
    }
}
