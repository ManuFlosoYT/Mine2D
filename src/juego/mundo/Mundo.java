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

        updateChunksAround(playerChunkX, playerChunkY);
    }

    public void forzarActualizacionInicial(Punto posicionJugador) {
        if (posicionJugador == null) return;
        int blockX = (int)Math.floor(posicionJugador.x() / BasicBlock.getSize());
        int tileYTop = (int)Math.floor(posicionJugador.y() / BasicBlock.getSize());
        int blockY = (WORLD_HEIGHT_BLOCKS - 1) - tileYTop;
        int chunkX = floorDiv(blockX, Chunk.CHUNK_SIZE);
        int chunkY = floorDiv(blockY, Chunk.CHUNK_SIZE);
        updateChunksAround(chunkX, chunkY);
        recargarChunksIniciales(chunkX, chunkY);
    }

    private void recargarChunksIniciales(int centerChunkX, int centerChunkY) {
        boolean removed = false;
        for (int cx = centerChunkX - 1; cx <= centerChunkX + 1; cx++) {
            for (int cy = centerChunkY - 1; cy <= centerChunkY + 1; cy++) {
                String k = key(cx, cy);
                Chunk existing = loadedChunks.remove(k);
                if (existing != null && existing.needsSaving()) {
                    chunkIOManager.saveChunk(existing);
                }
                if (existing != null) {
                    removed = true;
                }
            }
        }
        if (removed) {
            lastCenterChunkX = Integer.MIN_VALUE;
            lastCenterChunkY = Integer.MIN_VALUE;
            updateChunksAround(centerChunkX, centerChunkY);
        }
    }

    private void updateChunksAround(int playerChunkX, int playerChunkY) {
        lastCenterChunkX = playerChunkX;
        lastCenterChunkY = playerChunkY;

        java.util.Set<Chunk> chunksToUpdateFeatures = new java.util.HashSet<>();

        // Phase 1: Ensure all chunks in the 5x5 area exist and collect new ones.
        for (int cx = playerChunkX - 2; cx <= playerChunkX + 2; cx++) {
            for (int cy = playerChunkY - 2; cy <= playerChunkY + 2; cy++) {
                ensureChunkExists(cx, cy, chunksToUpdateFeatures);
            }
        }

        // Phase 2: For each new chunk, run feature generation on it and its neighbors.
        if (!chunksToUpdateFeatures.isEmpty()) {
            java.util.Set<Chunk> finalUpdateSet = new java.util.HashSet<>();
            for (Chunk newChunk : chunksToUpdateFeatures) {
                // Add the new chunk itself
                finalUpdateSet.add(newChunk);
                // Add all 8 of its neighbors
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0) continue;
                        Chunk neighbor = getChunk(newChunk.chunkX + dx, newChunk.chunkY + dy);
                        if (neighbor != null) {
                            finalUpdateSet.add(neighbor);
                        }
                    }
                }
            }

            for (Chunk chunkToUpdate : finalUpdateSet) {
                GeneradorMundo.generarFeaturesAdicionales(chunkToUpdate, this);
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
    private void ensureChunkExists(int chunkX, int chunkY, java.util.Set<Chunk> newChunks) {
        String k = key(chunkX, chunkY);
        if (loadedChunks.containsKey(k)) return;

        Chunk loaded = chunkIOManager.loadChunk(chunkX, chunkY);
        if (loaded == null) {
            loaded = new Chunk(chunkX, chunkY);
            GeneradorMundo.generarChunk(loaded, seed);
            loaded.markDirty();
            if (newChunks != null) {
                newChunks.add(loaded);
            }
        }
        loadedChunks.put(k, loaded);
    }

    public void ensureChunkLoadedSync(int chunkX, int chunkY) {
        ensureChunkExists(chunkX, chunkY, null);
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

    public Punto encontrarSpawnSeguro(double initialWorldX) {
        int searchRadius = 10; // Search up to 10 chunks away
        for (int chunkOffset = 0; chunkOffset < searchRadius; chunkOffset++) {
            // Check chunk to the right
            Punto spawnPoint = findSafeColumnInChunk(initialWorldX, chunkOffset);
            if (spawnPoint != null) {
                // Found a safe spot, now fully generate the area around it before returning.
                int blockX = (int) Math.floor(spawnPoint.x() / BasicBlock.getSize());
                int blockY = (WORLD_HEIGHT_BLOCKS - 1) - (int) Math.floor(spawnPoint.y() / BasicBlock.getSize());
                int chunkX = floorDiv(blockX, Chunk.CHUNK_SIZE);
                int chunkY = floorDiv(blockY, Chunk.CHUNK_SIZE);
                updateChunksAround(chunkX, chunkY);
                return spawnPoint;
            }

            // Check chunk to the left (if not the center chunk)
            if (chunkOffset > 0) {
                spawnPoint = findSafeColumnInChunk(initialWorldX, -chunkOffset);
                if (spawnPoint != null) {
                    // Found a safe spot, now fully generate the area around it before returning.
                    int blockX = (int) Math.floor(spawnPoint.x() / BasicBlock.getSize());
                    int blockY = (WORLD_HEIGHT_BLOCKS - 1) - (int) Math.floor(spawnPoint.y() / BasicBlock.getSize());
                    int chunkX = floorDiv(blockX, Chunk.CHUNK_SIZE);
                    int chunkY = floorDiv(blockY, Chunk.CHUNK_SIZE);
                    updateChunksAround(chunkX, chunkY);
                    return spawnPoint;
                }
            }
        }

        // Absolute fallback if no land is found within the search radius
        double fallbackScreenY = (WORLD_HEIGHT_BLOCKS / 2.0) * BasicBlock.getSize();
        Punto fallbackPoint = new Punto(initialWorldX, fallbackScreenY);
        int blockX = (int) Math.floor(fallbackPoint.x() / BasicBlock.getSize());
        int blockY = (WORLD_HEIGHT_BLOCKS - 1) - (int) Math.floor(fallbackPoint.y() / BasicBlock.getSize());
        int chunkX = floorDiv(blockX, Chunk.CHUNK_SIZE);
        int chunkY = floorDiv(blockY, Chunk.CHUNK_SIZE);
        updateChunksAround(chunkX, chunkY);
        return fallbackPoint;
    }

    private Punto findSafeColumnInChunk(double initialWorldX, int chunkOffsetX) {
        double worldX = initialWorldX + (chunkOffsetX * Chunk.CHUNK_SIZE * BasicBlock.getSize());
        int blockX = (int) Math.floor(worldX / BasicBlock.getSize());

        // Ensure the vertical column of chunks is loaded before scanning
        int chunkX = floorDiv(blockX, Chunk.CHUNK_SIZE);
        ensureVerticalColumnLoadedSync(chunkX);

        // Scan from top to bottom to find the first solid ground
        for (int blockY = WORLD_HEIGHT_BLOCKS - 1; blockY >= 0; blockY--) {
            BasicBlock block = getBlockAtTile(blockX, blockY);
            if (block != null && !"water".equals(block.getId())) {
                // Found solid ground. Check if the block above is water.
                BasicBlock blockAbove = getBlockAtTile(blockX, blockY + 1);
                if (blockAbove != null && "water".equals(blockAbove.getId())) {
                    return null; // This column is submerged, invalid spawn
                }

                // This is a valid spawn point.
                double blockTopScreenY = (WORLD_HEIGHT_BLOCKS - 1 - blockY) * BasicBlock.getSize();
                double playerY = blockTopScreenY - (2 * BasicBlock.getSize());
                return new Punto(worldX, playerY);
            }
        }
        return null; // No solid ground found in this column
    }
}
