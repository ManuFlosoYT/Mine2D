package juego.mundo;

import componentes.GeneradorMundo;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import juego.bloques.BasicBlock;
import tipos.Punto;

public class Mundo {
    public static final int WORLD_HEIGHT_BLOCKS = 256; // altura lógica total
    private static final int VERTICAL_CHUNKS = (int)Math.ceil(WORLD_HEIGHT_BLOCKS / (double)Chunk.CHUNK_SIZE);
    private static final int LOAD_RADIUS = 3;

    private final ChunkManager chunkManager;
    private final long seed;

    // Cache del último chunk del jugador para evitar trabajo redundante por frame
    private int lastCenterChunkX = Integer.MIN_VALUE;
    private int lastCenterChunkY = Integer.MIN_VALUE;

    public Mundo(long seed) {
        this.seed = seed;
        this.chunkManager = new ChunkManager(seed);
    }

    private String key(int x, int y) { return x + "_" + y; }

    public Chunk getChunk(int chunkX, int chunkY) {
        return chunkManager.getChunk(chunkX, chunkY);
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
        // Placeholder
    }

    public int getWorldHeightBlocks(){ return WORLD_HEIGHT_BLOCKS; }
    public double getWorldPixelHeight(){ return WORLD_HEIGHT_BLOCKS * BasicBlock.getSize(); }

    private void ensureVerticalColumnLoadedSync(int chunkX){
        for(int cy=0; cy<VERTICAL_CHUNKS; cy++){
            chunkManager.ensureChunkLoadedSync(chunkX, cy);
        }
    }

    public void update(Punto playerPosition) {
        Set<Chunk> newlyReadyChunks = chunkManager.processCompletedChunkLoads();

        // playerPosition.y() está en píxeles top-based de pantalla. Convertir a índice de bloque bottom-based.
        int playerTileYTop = (int) Math.floor(playerPosition.y() / BasicBlock.getSize());
        int playerBlockY = (WORLD_HEIGHT_BLOCKS - 1) - playerTileYTop;
        int playerBlockX = (int) Math.floor(playerPosition.x() / BasicBlock.getSize());

        int playerChunkX = floorDiv(playerBlockX, Chunk.CHUNK_SIZE);
        int playerChunkY = floorDiv(playerBlockY, Chunk.CHUNK_SIZE);

        if (playerChunkX == lastCenterChunkX && playerChunkY == lastCenterChunkY) {
            if (!newlyReadyChunks.isEmpty()) {
                regenerateChunkFeatures(newlyReadyChunks);
            }
            return;
        }

        updateChunksAround(playerChunkX, playerChunkY, newlyReadyChunks);
    }

    public void forzarActualizacionInicial(Punto posicionJugador) {
        if (posicionJugador == null) return;
        int blockX = (int)Math.floor(posicionJugador.x() / BasicBlock.getSize());
        int tileYTop = (int)Math.floor(posicionJugador.y() / BasicBlock.getSize());
        int blockY = (WORLD_HEIGHT_BLOCKS - 1) - tileYTop;
        int chunkX = floorDiv(blockX, Chunk.CHUNK_SIZE);
        int chunkY = floorDiv(blockY, Chunk.CHUNK_SIZE);
        Set<Chunk> ready = chunkManager.processCompletedChunkLoads();
        updateChunksAround(chunkX, chunkY, ready);
        recargarChunksIniciales(chunkX, chunkY);
    }

    private void recargarChunksIniciales(int centerChunkX, int centerChunkY) {
        boolean removed = false;
        Map<String, Chunk> loaded = chunkManager.getLoadedChunks();
        java.util.List<String> toRemove = new java.util.ArrayList<>();
        
        for (int cx = centerChunkX - 1; cx <= centerChunkX + 1; cx++) {
            for (int cy = centerChunkY - 1; cy <= centerChunkY + 1; cy++) {
                String k = key(cx, cy);
                if (loaded.containsKey(k)) {
                    Chunk existing = loaded.get(k);
                    chunkManager.saveChunk(existing);
                    toRemove.add(k);
                    removed = true;
                }
            }
        }
        chunkManager.unloadChunks(toRemove);
        
        if (removed) {
            lastCenterChunkX = Integer.MIN_VALUE;
            lastCenterChunkY = Integer.MIN_VALUE;
            updateChunksAround(centerChunkX, centerChunkY, new HashSet<>());
        }
    }

    private void updateChunksAround(int playerChunkX, int playerChunkY, Set<Chunk> preLoadedChunks) {
        lastCenterChunkX = playerChunkX;
        lastCenterChunkY = playerChunkY;

        java.util.Set<Chunk> chunksToUpdateFeatures = new java.util.HashSet<>(preLoadedChunks);

        // Phase 1: Ensure all chunks in the configurable area exist and collect new ones.
        for (int cx = playerChunkX - LOAD_RADIUS; cx <= playerChunkX + LOAD_RADIUS; cx++) {
            for (int cy = playerChunkY - LOAD_RADIUS; cy <= playerChunkY + LOAD_RADIUS; cy++) {
                ensureChunkExists(cx, cy, chunksToUpdateFeatures);
            }
        }

        // Phase 2: For each new chunk, run feature generation on it and its neighbors.
        if (!chunksToUpdateFeatures.isEmpty()) {
            regenerateChunkFeatures(chunksToUpdateFeatures);
        } else {
            // Even previously loaded chunks may still need their first feature pass.
            java.util.Set<Chunk> pending = new java.util.HashSet<>();
            for (int cx = playerChunkX - LOAD_RADIUS; cx <= playerChunkX + LOAD_RADIUS; cx++) {
                for (int cy = playerChunkY - LOAD_RADIUS; cy <= playerChunkY + LOAD_RADIUS; cy++) {
                    Chunk chunk = getChunk(cx, cy);
                    if (chunk != null && chunk.needsFeaturesGeneration()) {
                        pending.add(chunk);
                    }
                }
            }
            if (!pending.isEmpty()) {
                regenerateChunkFeatures(pending);
            }
        }

        // Descargar / guardar chunks según distancia configurable
        autoSaveAndCleanupChunks(playerChunkX, playerChunkY);
    }

    private void autoSaveAndCleanupChunks(int playerChunkX, int playerChunkY) {
        final int autoSaveRadius = LOAD_RADIUS;
        final int unloadRadius = LOAD_RADIUS + 1;
        java.util.List<String> chunksToRemove = new java.util.ArrayList<>();
        
        for (Map.Entry<String, Chunk> entry : chunkManager.getLoadedChunks().entrySet()) {
            Chunk chunk = entry.getValue();
            int dx = Math.abs(chunk.chunkX - playerChunkX);
            int dy = Math.abs(chunk.chunkY - playerChunkY);

            if ((dx > autoSaveRadius || dy > autoSaveRadius) && chunk.needsSaving()) {
                chunkManager.saveChunk(chunk);
            }

            if (dx > unloadRadius || dy > unloadRadius) {
                chunksToRemove.add(entry.getKey());
            }
        }
        chunkManager.unloadChunks(chunksToRemove);
    }

    private void regenerateChunkFeatures(java.util.Set<Chunk> targetChunks) {
        java.util.Set<Chunk> finalUpdateSet = new java.util.HashSet<>();
        for (Chunk newChunk : targetChunks) {
            finalUpdateSet.add(newChunk);
            // Add neighbors to ensure beach transitions line up.
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
            chunkToUpdate.markFeaturesGenerated();
        }
    }

    /**
     * Carga o genera un chunk concreto de forma sincrónica en este hilo.
     */
    private void ensureChunkExists(int chunkX, int chunkY, java.util.Set<Chunk> newChunks) {
        Chunk existing = getChunk(chunkX, chunkY);
        if (existing != null) {
            if (existing.needsFeaturesGeneration() && newChunks != null) {
                newChunks.add(existing);
            }
            return;
        }
        chunkManager.requestChunkLoad(chunkX, chunkY);
    }

    public void ensureChunkLoadedSync(int chunkX, int chunkY) {
        chunkManager.ensureChunkLoadedSync(chunkX, chunkY);
    }

    public void saveAll() {
        chunkManager.saveAll();
    }

    public void close() {
        chunkManager.close();
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
        return chunkManager.getLoadedChunks();
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

    private void updateChunksAround(int playerChunkX, int playerChunkY) {
        updateChunksAround(playerChunkX, playerChunkY, java.util.Collections.emptySet());
    }
}
