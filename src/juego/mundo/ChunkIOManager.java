package juego.mundo;

import juego.bloques.BasicBlock;
import juego.bloques.WaterBlock;
import tipos.Punto;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ChunkIOManager {
    private static final String WORLD_FILE = "world.wgz";
    private static final String PLAYER_FILE = "player.dat";
    private static final String META_FILE = "meta.dat";
    private static final String CHUNKS_DIR = "chunks/";

    private static final class ArchiveEntries {
        final Map<String, byte[]> metadataEntries = new LinkedHashMap<>();
        final Map<String, byte[]> chunkEntries = new LinkedHashMap<>();
    }

    public void saveWorld(Mundo mundo, Punto playerPosition) {
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
        ArchiveEntries archive = readArchiveEntries();
        archive.chunkEntries.put(entryName(chunk.chunkX, chunk.chunkY), serializeChunk(chunk));
        chunk.saved();
        writeArchive(archive.metadataEntries, archive.chunkEntries);
    }

    public Punto loadWorld(Mundo mundo) {
        Punto playerPosition = new Punto(0, 0);
        Path worldPath = Paths.get(WORLD_FILE);
        if (!Files.exists(worldPath)) {
            System.out.println("[LOAD] No existe " + WORLD_FILE + ". Se creará un mundo nuevo.");
            return mundo.encontrarSpawnSeguro(0);
        }

        System.out.println("[LOAD] Cargando " + WORLD_FILE + " ...");
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(WORLD_FILE))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(PLAYER_FILE)) {
                    System.out.println("[LOAD] Leyendo " + PLAYER_FILE + " ...");
                    DataInputStream dis = new DataInputStream(zis);
                    double x = dis.readDouble();
                    double y = dis.readDouble();
                    playerPosition = new Punto(x, y);
                } else if (entry.getName().equals(META_FILE)) {
                    System.out.println("[LOAD] Leyendo " + META_FILE + " ...");
                    DataInputStream dis = new DataInputStream(zis);
                    dis.readLong(); // seed
                } else if (entry.getName().startsWith(CHUNKS_DIR)) {
                    System.out.println("[LOAD] Encontrado chunk registrado: " + entry.getName());
                }
                zis.closeEntry();
            }
            System.out.println("[LOAD] Carga de metadatos completada. Posición jugador: (" + playerPosition.x() + ", " + playerPosition.y() + ")");
        } catch (IOException e) {
            System.err.println("[LOAD] Error cargando el mundo: " + e.getMessage());
        }
        return playerPosition;
    }

    public Chunk loadChunk(int chunkX, int chunkY) {
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
        } else {
            chunk.setBlockGenerated(localX, localY, new BasicBlock(blockId, pos));
        }
    }
}
