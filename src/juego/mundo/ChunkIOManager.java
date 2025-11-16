package juego.mundo;

import juego.bloques.BasicBlock;
import juego.bloques.BlockType;
import juego.bloques.WaterBlock;
import tipos.Punto;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ChunkIOManager {
    private static final String WORLD_FILE = "world.wgz";
    private static final String PLAYER_FILE = "player.dat";
    private static final String META_FILE = "meta.dat";
    private static final String CHUNKS_DIR = "chunks/";

    public void saveWorld(Mundo mundo, Punto playerPosition) {
        System.out.println("[SAVE] Abriendo " + WORLD_FILE + " para escritura...");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(WORLD_FILE))) {
            // Save player data
            System.out.println("[SAVE] Escribiendo " + PLAYER_FILE + " ...");
            zos.putNextEntry(new ZipEntry(PLAYER_FILE));
            DataOutputStream dos = new DataOutputStream(zos);
            dos.writeDouble(playerPosition.x());
            dos.writeDouble(playerPosition.y());
            dos.flush();
            zos.closeEntry();
            System.out.println("[SAVE] " + PLAYER_FILE + " OK");

            // Save metadata (seed)
            System.out.println("[SAVE] Escribiendo " + META_FILE + " (seed=" + mundo.getSeed() + ") ...");
            zos.putNextEntry(new ZipEntry(META_FILE));
            dos.writeLong(mundo.getSeed());
            dos.flush();
            zos.closeEntry();
            System.out.println("[SAVE] " + META_FILE + " OK");

            // Create chunks directory entry
            System.out.println("[SAVE] Creando directorio de chunks '" + CHUNKS_DIR + "' ...");
            zos.putNextEntry(new ZipEntry(CHUNKS_DIR));
            zos.closeEntry();

            // Save chunks
            int total = 0;
            for (Chunk chunk : mundo.getLoadedChunks().values()) {
                if (chunk.needsSaving()) {
                    System.out.println("[SAVE] Guardando chunk (" + chunk.chunkX + "," + chunk.chunkY + ") ...");
                    saveChunk(zos, chunk);
                    chunk.saved();
                    total++;
                }
            }
            System.out.println("[SAVE] Guardado completado. Chunks escritos: " + total);
        } catch (IOException e) {
            System.err.println("[SAVE] Error guardando el mundo: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Punto loadWorld(Mundo mundo) {
        Punto playerPosition = new Punto(0, 0);
        Path worldPath = Paths.get(WORLD_FILE);
        if (!Files.exists(worldPath)) {
            System.out.println("[LOAD] No existe " + WORLD_FILE + ". Se creará un mundo nuevo.");
            return playerPosition;
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
                    long seed = dis.readLong();
                    // Si quisieras validar seed vs mundo.getSeed(), podrías hacerlo aquí.
                } else if (entry.getName().startsWith(CHUNKS_DIR)) {
                    // Chunks on-demand
                }
                zis.closeEntry();
            }
            System.out.println("[LOAD] Carga de metadatos completada. Posición jugador: (" + playerPosition.x() + ", " + playerPosition.y() + ")");
        } catch (IOException e) {
            System.err.println("[LOAD] Error cargando el mundo: " + e.getMessage());
            e.printStackTrace();
        }
        return playerPosition;
    }


    public void saveChunk(Chunk chunk) {
        // Guardado individual no usado en este flujo; se guarda desde saveWorld
    }

    private void saveChunk(ZipOutputStream zos, Chunk chunk) throws IOException {
        String chunkFileName = CHUNKS_DIR + "chunk_" + chunk.chunkX + "_" + chunk.chunkY + ".dat";
        zos.putNextEntry(new ZipEntry(chunkFileName));
        DataOutputStream dos = new DataOutputStream(zos);
        for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
            for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                BasicBlock block = chunk.getBlock(x, y);
                if (block == null) {
                    dos.writeUTF("air");
                } else {
                    dos.writeUTF(block.getId());
                }
            }
        }
        dos.flush(); // no cerrar: cerraría el ZipOutputStream
        zos.closeEntry();
    }

    public Chunk loadChunk(int chunkX, int chunkY) {
        Path worldPath = Paths.get(WORLD_FILE);
        if (!Files.exists(worldPath)) {
            return null;
        }
        String chunkFileName = CHUNKS_DIR + "chunk_" + chunkX + "_" + chunkY + ".dat";
        try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(WORLD_FILE)) {
            java.util.zip.ZipEntry entry = zipFile.getEntry(chunkFileName);
            if (entry == null) return null;
            try (InputStream is = zipFile.getInputStream(entry)) {
                DataInputStream dis = new DataInputStream(is);
                Chunk chunk = new Chunk(chunkX, chunkY);
                for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                    for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                        String blockId = dis.readUTF();
                        if (!"air".equals(blockId)) {
                            double blockWorldX = (chunkX * Chunk.CHUNK_SIZE + x) * BasicBlock.getSize();
                            int logicalY = chunkY * Chunk.CHUNK_SIZE + y;
                            double blockWorldY = (Mundo.WORLD_HEIGHT_BLOCKS - 1 - logicalY) * BasicBlock.getSize();
                            Punto pos = new Punto(blockWorldX, blockWorldY);
                            if ("water".equals(blockId)) {
                                chunk.setBlockGenerated(x, y, new WaterBlock(pos));
                            } else {
                                chunk.setBlockGenerated(x, y, new BasicBlock(blockId, pos));
                            }
                        }
                    }
                }
                chunk.saved();
                return chunk;
            }
        } catch (IOException e) {
            System.err.println("[LOAD] Error cargando chunk (" + chunkX + "," + chunkY + "): " + e.getMessage());
        }
        return null;
    }
}
