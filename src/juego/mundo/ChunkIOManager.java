package juego.mundo;

import juego.bloques.BasicBlock;
import juego.bloques.BedrockBlock;
import juego.bloques.WaterBlock;
import tipos.Punto;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ChunkIOManager {
    private static final String WORLD_FILE = "world.wgz";
    private static final String PLAYER_FILE = "player.dat";
    private static final String META_FILE = "meta.dat";
    private static final String CHUNKS_DIR = "chunks/";

    private final ExecutorService ioExecutor;

    private static final class ArchiveEntries {
        final Map<String, byte[]> metadataEntries = new LinkedHashMap<>();
        final Map<String, byte[]> chunkEntries = new LinkedHashMap<>();
    }

    public ChunkIOManager() {
        this.ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "chunk-io-thread");
            t.setDaemon(true);
            return t;
        });
    }

    public void saveWorld(Mundo mundo, Punto playerPosition) {
        CompletableFuture.runAsync(() -> saveWorldInternal(mundo, playerPosition), ioExecutor)
                .exceptionally(ex -> {
                    System.err.println("[SAVE] Error guardando mundo: " + ex.getMessage());
                    return null;
                })
                .join();
    }

    private void saveWorldInternal(Mundo mundo, Punto playerPosition) {
        ArchiveEntries archive = readArchiveEntries();
        Map<String, byte[]> metadataEntries = archive.metadataEntries;
        Map<String, byte[]> chunkEntries = archive.chunkEntries;
        if (playerPosition != null) {
            metadataEntries.put(PLAYER_FILE, serializePlayerPosition(playerPosition));
        }
        metadataEntries.put(META_FILE, serializeSeed(mundo.getSeed()));
        for (Chunk chunk : mundo.getLoadedChunks().values()) {
            if (chunk.needsSaving()) {
                chunkEntries.put(entryName(chunk.chunkX, chunk.chunkY), serializeChunk(chunk));
                chunk.saved();
            }
        }
        writeArchive(metadataEntries, chunkEntries);
    }

    public void saveChunk(Chunk chunk) {
        if (chunk == null) return;
        byte[] serialized = serializeChunk(chunk);
        final String entry = entryName(chunk.chunkX, chunk.chunkY);
        chunk.saved();
        CompletableFuture.runAsync(() -> writeSingleChunk(entry, serialized), ioExecutor)
                .exceptionally(ex -> {
                    System.err.println("[SAVE] Error escribiendo chunk (" + chunk.chunkX + "," + chunk.chunkY + "): " + ex.getMessage());
                    return null;
                });
    }

    private void writeSingleChunk(String entryName, byte[] serializedChunk) {
        ArchiveEntries archive = readArchiveEntries();
        archive.chunkEntries.put(entryName, serializedChunk);
        writeArchive(archive.metadataEntries, archive.chunkEntries);
    }

    public CompletableFuture<Chunk> loadChunkAsync(int chunkX, int chunkY) {
        return CompletableFuture.supplyAsync(() -> loadChunkInternal(chunkX, chunkY), ioExecutor);
    }

    public Chunk loadChunk(int chunkX, int chunkY) {
        try {
            return loadChunkAsync(chunkX, chunkY).join();
        } catch (CompletionException ex) {
            System.err.println("[LOAD] Error cargando chunk (" + chunkX + "," + chunkY + "): " + ex.getCause().getMessage());
            return null;
        }
    }

    private Chunk loadChunkInternal(int chunkX, int chunkY) {
        String entryName = entryName(chunkX, chunkY);
        try (ZipInputStream zis = openZip()) {
            if (zis == null) return null;
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    Chunk chunk = new Chunk(chunkX, chunkY);
                    byte[] rawData = zis.readAllBytes();
                    readChunkData(new String(rawData, StandardCharsets.UTF_8), chunk, chunkX, chunkY);
                    zis.closeEntry();
                    return chunk;
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            System.err.println("[LOAD] Error cargando chunk (" + chunkX + "," + chunkY + "): " + e.getMessage());
        }
        return null;
    }

    public void flush() {
        CompletableFuture.runAsync(() -> {}, ioExecutor).join();
    }

    public void shutdown() {
        flush();
        ioExecutor.shutdown();
    }

    private ArchiveEntries readArchiveEntries() {
        ArchiveEntries archive = new ArchiveEntries();
        try (ZipInputStream zis = openZip()) {
            if (zis == null) return archive;
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().startsWith(CHUNKS_DIR)) {
                    archive.chunkEntries.put(entry.getName(), zis.readAllBytes());
                } else {
                    archive.metadataEntries.put(entry.getName(), zis.readAllBytes());
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            System.err.println("[SAVE] Error leyendo archivo del mundo: " + e.getMessage());
        }
        return archive;
    }

    private void writeArchive(Map<String, byte[]> metadataEntries, Map<String, byte[]> chunkEntries) {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(WORLD_FILE))) {
            for (Map.Entry<String, byte[]> entry : metadataEntries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
            for (Map.Entry<String, byte[]> entry : chunkEntries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        } catch (IOException e) {
            System.err.println("[SAVE] Error escribiendo archivo del mundo: " + e.getMessage());
        }
    }

    private byte[] serializePlayerPosition(Punto playerPosition) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeDouble(playerPosition.x());
            dos.writeDouble(playerPosition.y());
            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] serializeSeed(long seed) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeLong(seed);
            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] serializeChunk(Chunk chunk) {
        String encoded = encodeChunk(chunk);
        return encoded.getBytes(StandardCharsets.UTF_8);
    }

    private ZipInputStream openZip() throws IOException {
        Path worldPath = Paths.get(WORLD_FILE);
        if (!Files.exists(worldPath)) {
            return null;
        }
        return new ZipInputStream(new FileInputStream(WORLD_FILE));
    }

    private String entryName(int chunkX, int chunkY) {
        return CHUNKS_DIR + chunkFileName(chunkX, chunkY);
    }

    private String chunkFileName(int chunkX, int chunkY) {
        return "chunk_" + chunkX + "_" + chunkY + ".dat";
    }

    private String encodeChunk(Chunk chunk) {
        StringBuilder builder = new StringBuilder();
        String currentId = null;
        int runLength = 0;
        for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
            for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                BasicBlock block = chunk.getBlock(x, y);
                String blockId = (block == null) ? "air" : block.getId();
                if (currentId == null) {
                    currentId = blockId;
                    runLength = 1;
                } else if (currentId.equals(blockId)) {
                    runLength++;
                } else {
                    appendRun(builder, runLength, currentId);
                    currentId = blockId;
                    runLength = 1;
                }
            }
        }
        if (currentId != null) {
            appendRun(builder, runLength, currentId);
        }
        return builder.toString();
    }

    private void appendRun(StringBuilder builder, int count, String blockId) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(count).append('*').append(blockId);
    }

    private void readChunkData(String encodedData, Chunk chunk, int chunkX, int chunkY) throws IOException {
        if (encodedData == null || encodedData.isEmpty()) {
            throw new IOException("Datos de chunk vacíos");
        }
        String[] runs = encodedData.split("\n");
        final int totalBlocks = Chunk.CHUNK_SIZE * Chunk.CHUNK_SIZE;
        int index = 0;
        for (String rawRun : runs) {
            String run = rawRun.trim();
            if (run.isEmpty()) {
                continue;
            }
            int separator = run.indexOf('*');
            if (separator <= 0 || separator == run.length() - 1) {
                throw new IOException("Formato de run inválido: " + run);
            }
            int count;
            try {
                count = Integer.parseInt(run.substring(0, separator));
            } catch (NumberFormatException e) {
                throw new IOException("Conteo inválido en run: " + run, e);
            }
            String blockId = run.substring(separator + 1);
            for (int i = 0; i < count; i++) {
                if (index >= totalBlocks) {
                    throw new IOException("Chunk con más bloques de los esperados (" + totalBlocks + ")");
                }
                int y = index / Chunk.CHUNK_SIZE;
                int x = index % Chunk.CHUNK_SIZE;
                placeBlockFromId(chunk, x, y, chunkX, chunkY, blockId);
                index++;
            }
        }
        if (index != totalBlocks) {
            throw new IOException("Chunk incompleto. Esperados " + totalBlocks + " bloques, obtenidos " + index);
        }
        chunk.saved();
    }

    private void placeBlockFromId(Chunk chunk, int localX, int localY, int chunkX, int chunkY, String blockId) {
        if ("air".equals(blockId)) {
            chunk.setBlockGenerated(localX, localY, null);
            return;
        }
        double blockWorldX = (chunkX * Chunk.CHUNK_SIZE + localX) * BasicBlock.getSize();
        int logicalY = chunkY * Chunk.CHUNK_SIZE + localY;
        double blockWorldY = (Mundo.WORLD_HEIGHT_BLOCKS - 1 - logicalY) * BasicBlock.getSize();
        Punto pos = new Punto(blockWorldX, blockWorldY);
        if ("water".equals(blockId)) {
            chunk.setBlockGenerated(localX, localY, new WaterBlock(pos));
        } else if ("bedrock".equals(blockId)) {
            chunk.setBlockGenerated(localX, localY, new BedrockBlock(pos));
        } else {
            chunk.setBlockGenerated(localX, localY, new BasicBlock(blockId, pos));
        }
    }

    public Punto loadWorld(Mundo mundo) {
        Map<String, byte[]> metadataEntries = readMetadataOnly();
        Punto spawn = new Punto(0, 0);
        byte[] playerData = metadataEntries.get(PLAYER_FILE);
        if (playerData != null) {
            try {
                spawn = deserializePlayerPosition(playerData);
            } catch (UncheckedIOException e) {
                System.err.println("[LOAD] Error leyendo posición del jugador: " + e.getMessage());
            }
        }
        byte[] seedData = metadataEntries.get(META_FILE);
        if (seedData != null && mundo != null) {
            try {
                long storedSeed = deserializeSeed(seedData);
                if (mundo.getSeed() != storedSeed) {
                    System.err.println("[LOAD] Advertencia: semilla guardada (" + storedSeed + ") difiere de la actual (" + mundo.getSeed() + ")");
                }
            } catch (UncheckedIOException e) {
                System.err.println("[LOAD] Error leyendo semilla guardada: " + e.getMessage());
            }
        }
        return spawn;
    }

    private Map<String, byte[]> readMetadataOnly() {
        Map<String, byte[]> metadataEntries = new LinkedHashMap<>();
        try (ZipInputStream zis = openZip()) {
            if (zis == null) return metadataEntries;
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.getName().startsWith(CHUNKS_DIR)) {
                    metadataEntries.put(entry.getName(), zis.readAllBytes());
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            System.err.println("[LOAD] Error leyendo metadata del mundo: " + e.getMessage());
        }
        return metadataEntries;
    }

    private Punto deserializePlayerPosition(byte[] data) {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data))) {
            double x = dis.readDouble();
            double y = dis.readDouble();
            return new Punto(x, y);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private long deserializeSeed(byte[] data) {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data))) {
            return dis.readLong();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
