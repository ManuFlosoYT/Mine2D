package juego.mundo;

import componentes.GeneradorMundo;
import juego.bloques.BasicBlock;
import tipos.Punto;

import java.util.HashMap;
import java.util.Map;

public class Mundo {
    public static final int WORLD_HEIGHT_BLOCKS = 256; // altura lógica total
    private static final int VERTICAL_CHUNKS = (int)Math.ceil(WORLD_HEIGHT_BLOCKS / (double)Chunk.CHUNK_SIZE);

    private final Map<String, Chunk> loadedChunks = new HashMap<>();
    private final long seed;
    private final ChunkIOManager chunkIOManager;

    // Cache del último chunk del jugador para evitar trabajo redundante por frame
    private int lastCenterChunkX = Integer.MIN_VALUE;
    private int lastCenterChunkY = Integer.MIN_VALUE;

    public Mundo(long seed) {
        this.seed = seed;
        this.chunkIOManager = new ChunkIOManager();
    }

    private String key(int x, int y) { return x + "_" + y; }

    public Chunk getChunk(int chunkX, int chunkY) {
        return loadedChunks.get(key(chunkX, chunkY));
    }

    public long getSeed(){ return seed; }

    private int floorDiv(int a, int b){ int r = a / b; if ((a ^ b) < 0 && (r * b != a)) r--; return r; }
    private int floorMod(int a, int b){ int m = a % b; return (m < 0) ? m + Math.abs(b) : m; }

    public BasicBlock getBlockAt(double worldX, double worldY) {
        int blockX = (int) Math.floor(worldX / BasicBlock.getSize());
        int blockY = (int) Math.floor(worldY / BasicBlock.getSize());
        int chunkX = floorDiv(blockX, Chunk.CHUNK_SIZE);
        int chunkY = floorDiv(blockY, Chunk.CHUNK_SIZE);
        int localX = floorMod(blockX, Chunk.CHUNK_SIZE);
        int localY = floorMod(blockY, Chunk.CHUNK_SIZE);
        Chunk chunk = getChunk(chunkX, chunkY);
        if (chunk == null) return null;
        return chunk.getBlock(localX, localY);
    }

    public BasicBlock getBlockAtTile(int blockX, int blockY) {
        int chunkX = floorDiv(blockX, Chunk.CHUNK_SIZE);
        int chunkY = floorDiv(blockY, Chunk.CHUNK_SIZE);
        int localX = floorMod(blockX, Chunk.CHUNK_SIZE);
        int localY = floorMod(blockY, Chunk.CHUNK_SIZE);
        Chunk chunk = getChunk(chunkX, chunkY);
        if (chunk == null) return null;
        return chunk.getBlock(localX, localY);
    }

    public void setBlockAt(double worldX, double worldY, BasicBlock block) {
        int blockX = (int) Math.floor(worldX / BasicBlock.getSize());
        int blockY = (int) Math.floor(worldY / BasicBlock.getSize());
        int chunkX = floorDiv(blockX, Chunk.CHUNK_SIZE);
        int chunkY = floorDiv(blockY, Chunk.CHUNK_SIZE);
        int localX = floorMod(blockX, Chunk.CHUNK_SIZE);
        int localY = floorMod(blockY, Chunk.CHUNK_SIZE);
        Chunk chunk = getChunk(chunkX, chunkY);
        if (chunk == null) return;
        chunk.setBlock(localX, localY, block);
    }

    public void setBlockAtTile(int blockX, int blockY, BasicBlock block) {
        int chunkX = floorDiv(blockX, Chunk.CHUNK_SIZE);
        int chunkY = floorDiv(blockY, Chunk.CHUNK_SIZE);
        int localX = floorMod(blockX, Chunk.CHUNK_SIZE);
        int localY = floorMod(blockY, Chunk.CHUNK_SIZE);
        Chunk chunk = getChunk(chunkX, chunkY);
        if (chunk == null) return;
        chunk.setBlock(localX, localY, block);
    }

    public void markChunkDirty(int blockX, int blockY) {
        int chunkX = floorDiv(blockX, Chunk.CHUNK_SIZE);
        int chunkY = floorDiv(blockY, Chunk.CHUNK_SIZE);
        Chunk chunk = getChunk(chunkX, chunkY);
        if (chunk != null) {
            // This is a placeholder for a more complex system.
            // For now, we don't need to do anything here as the block change itself dirties the chunk.
            // If lighting were managed outside, we would trigger a recalculation here.
        }
    }

    public int getWorldHeightBlocks(){ return WORLD_HEIGHT_BLOCKS; }
    public double getWorldPixelHeight(){ return WORLD_HEIGHT_BLOCKS * BasicBlock.getSize(); }

    private void ensureVerticalColumnLoadedSync(int chunkX){
        for(int cy=0; cy<VERTICAL_CHUNKS; cy++){
            ensureChunkLoadedSync(chunkX, cy);
        }
    }

    public void update(Punto playerPosition) {
        // playerPosition.y() está en píxeles top-based de pantalla. Convertir a índice de bloque bottom-based.
        int playerTileYTop = (int) Math.floor(playerPosition.y() / BasicBlock.getSize());
        int playerBlockY = (WORLD_HEIGHT_BLOCKS - 1) - playerTileYTop;
        int playerBlockX = (int) Math.floor(playerPosition.x() / BasicBlock.getSize());

        int playerChunkX = floorDiv(playerBlockX, Chunk.CHUNK_SIZE);
        int playerChunkY = floorDiv(playerBlockY, Chunk.CHUNK_SIZE);

        // Si seguimos en el mismo chunk central, no hagamos nada este frame
        if (playerChunkX == lastCenterChunkX && playerChunkY == lastCenterChunkY) {
            return;
        }
        lastCenterChunkX = playerChunkX;
        lastCenterChunkY = playerChunkY;

        // Cargar un área 5x5 (radio 2) de chunks alrededor del jugador
        for (int cx = playerChunkX - 2; cx <= playerChunkX + 2; cx++) {
            for (int cy = playerChunkY - 2; cy <= playerChunkY + 2; cy++) {
                ensureChunkLoadedSync(cx, cy);
            }
        }

        // Descargar chunks alejados fuera de radio 2
        loadedChunks.entrySet().removeIf(entry -> {
            String[] parts = entry.getKey().split("_");
            int chunkX = Integer.parseInt(parts[0]);
            int chunkY = Integer.parseInt(parts[1]);
            boolean shouldUnload = Math.abs(chunkX - playerChunkX) > 2 || Math.abs(chunkY - playerChunkY) > 2;
            if (shouldUnload && entry.getValue().needsSaving()) {
                chunkIOManager.saveChunk(entry.getValue());
            }
            return shouldUnload;
        });
    }

    /**
     * Carga o genera un chunk concreto de forma sincrónica en este hilo.
     */
    public void ensureChunkLoadedSync(int chunkX, int chunkY) {
        String k = key(chunkX, chunkY);
        if (loadedChunks.containsKey(k)) return;

        Chunk loaded = chunkIOManager.loadChunk(chunkX, chunkY);
        boolean isNew = false;
        if (loaded == null) {
            loaded = new Chunk(chunkX, chunkY);
            GeneradorMundo.generarChunk(loaded, seed);
            isNew = true;
        }
        loadedChunks.put(k, loaded);

        if (isNew) {
            // Post-generación de features que dependen de vecinos
            GeneradorMundo.generarFeaturesAdicionales(loaded, this);
        }
    }

    public void saveAll() {
        for (Chunk chunk : loadedChunks.values()) {
            if (chunk.needsSaving()) {
                chunkIOManager.saveChunk(chunk);
            }
        }
    }

    public void close() {
        saveAll();
        // ya no hay executor que cerrar
    }

    public Map<String, Chunk> getChunksInRange(int minTileX, int minTileY, int maxTileX, int maxTileY) {
        Map<String, Chunk> result = new HashMap<>();
        int minChunkX = floorDiv(minTileX, Chunk.CHUNK_SIZE);
        int maxChunkX = floorDiv(maxTileX, Chunk.CHUNK_SIZE);
        int minChunkY = floorDiv(minTileY, Chunk.CHUNK_SIZE);
        int maxChunkY = floorDiv(maxTileY, Chunk.CHUNK_SIZE);

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cy = minChunkY; cy <= maxChunkY; cy++) {
                Chunk chunk = getChunk(cx, cy);
                if (chunk != null) {
                    result.put(key(cx, cy), chunk);
                }
            }
        }
        return result;
    }

    public Map<String, Chunk> getLoadedChunks() {
        return loadedChunks;
    }
}
