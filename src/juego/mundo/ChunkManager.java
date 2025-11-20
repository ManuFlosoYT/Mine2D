package juego.mundo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Manages the lifecycle of chunks: loading, unloading, saving, and generation.
 */
public class ChunkManager {
    
    private final Map<String, Chunk> loadedChunks = new HashMap<>();
    private final Map<String, CompletableFuture<Chunk>> pendingChunkLoads = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<ChunkLoadResult> completedChunkLoads = new ConcurrentLinkedQueue<>();
    private final ChunkIOManager chunkIOManager;
    private final long seed;

    public ChunkManager(long seed) {
        this.seed = seed;
        this.chunkIOManager = new ChunkIOManager();
    }

    public Chunk getChunk(int chunkX, int chunkY) {
        return loadedChunks.get(key(chunkX, chunkY));
    }

    public void ensureChunkLoadedSync(int chunkX, int chunkY) {
        String k = key(chunkX, chunkY);
        if (loadedChunks.containsKey(k)) return;

        CompletableFuture<Chunk> pending = pendingChunkLoads.remove(k);
        Chunk chunk = null;
        if (pending != null) {
            try {
                chunk = pending.join();
            } catch (Exception e) {
                System.err.println("[LOAD] Error completando chunk pendiente (" + chunkX + "," + chunkY + "): " + e.getMessage());
            }
        }
        
        if (chunk == null) {
            chunk = chunkIOManager.loadChunk(chunkX, chunkY);
        }
        
        if (chunk == null) {
            chunk = createGeneratedChunk(chunkX, chunkY);
        }
        
        loadedChunks.put(k, chunk);
    }

    public void requestChunkLoad(int chunkX, int chunkY) {
        String k = key(chunkX, chunkY);
        if (loadedChunks.containsKey(k) || pendingChunkLoads.containsKey(k)) return;

        CompletableFuture<Chunk> future = chunkIOManager.loadChunkAsync(chunkX, chunkY);
        future.whenComplete((chunk, throwable) -> {
            if (throwable != null) {
                System.err.println("[LOAD] Error asíncrono chunk (" + chunkX + "," + chunkY + "): " + throwable.getMessage());
            }
            completedChunkLoads.add(new ChunkLoadResult(k, chunkX, chunkY, (throwable == null) ? chunk : null));
        });
        pendingChunkLoads.put(k, future);
    }

    public Set<Chunk> processCompletedChunkLoads() {
        Set<Chunk> ready = new HashSet<>();
        ChunkLoadResult result;
        while ((result = completedChunkLoads.poll()) != null) {
            pendingChunkLoads.remove(result.key);
            if (loadedChunks.containsKey(result.key)) {
                continue;
            }
            Chunk chunk = result.chunk;
            if (chunk == null) {
                chunk = createGeneratedChunk(result.chunkX, result.chunkY);
            }
            loadedChunks.put(result.key, chunk);
            ready.add(chunk);
        }
        return ready;
    }

    public void unloadChunks(List<String> keysToRemove) {
        for (String key : keysToRemove) {
            loadedChunks.remove(key);
        }
    }

    public void saveChunk(Chunk chunk) {
        if (chunk.needsSaving()) {
            chunkIOManager.saveChunk(chunk);
        }
    }

    public void saveAll() {
        for (Chunk chunk : loadedChunks.values()) {
            saveChunk(chunk);
        }
        chunkIOManager.flush();
    }

    public void close() {
        saveAll();
        chunkIOManager.shutdown();
    }

    public Map<String, Chunk> getLoadedChunks() {
        return loadedChunks;
    }

    private Chunk createGeneratedChunk(int chunkX, int chunkY) {
        Chunk chunk = new Chunk(chunkX, chunkY);
        componentes.GeneradorMundo.generarChunk(chunk, seed);
        chunk.markDirty();
        return chunk;
    }

    private String key(int x, int y) { return x + "_" + y; }

    // Record for internal use
    private record ChunkLoadResult(String key, int chunkX, int chunkY, Chunk chunk) {}
}
