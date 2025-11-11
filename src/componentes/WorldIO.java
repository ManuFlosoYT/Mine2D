package componentes;

import juego.bloques.BasicBlock;
import tipos.Punto;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.StringTokenizer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Entrada/Salida de mundos a un formato de texto simple para depuración.
 *
 * Formato sin comprimir:
 * - Línea 1: ancho alto (enteros)
 * - Resto: alto líneas, cada una con ancho tokens separados por comas. Cada token es:
 *   id (p.ej. stone) para bloque, o . para aire.
 * - Opcional: última línea "#PLAYER x y".
 *
 * Formato comprimido (RLE + GZIP):
 * - Primera línea: modo ancho alto, donde modo=0 (ROW) o modo=1 (COL).
 * - Si ROW: cada fila se codifica en segmentos token*count separados por ';'.
 * - Si COL: cada columna X se codifica igual, en orden 0..ancho-1.
 * - Admite meta-RLE de líneas: N*LINE para repetir LINE N veces.
 * - Línea opcional #PLAYER al final.
 */
public final class WorldIO {
    private WorldIO() {}

    public static void save(File file, BasicBlock[][] mundo) throws IOException { save(file, mundo, null); }

    /** Guarda mundo sin comprimir y opcionalmente posición del jugador. */
    public static void save(File file, BasicBlock[][] mundo, Punto jugadorPos) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            if (mundo == null || mundo.length == 0) { bw.write("0 0\n"); return; }
            int alto = mundo.length; int ancho = mundo[0].length;
            bw.write(ancho + " " + alto + "\n");
            for (int arrayY = 0; arrayY < alto; arrayY++) {
                StringBuilder sb = new StringBuilder();
                for (int x = 0; x < ancho; x++) {
                    BasicBlock b = mundo[arrayY][x];
                    if (x > 0) sb.append(',');
                    sb.append(b == null ? '.' : b.getId());
                }
                bw.write(sb.toString()); bw.write('\n');
            }
            if (jugadorPos != null) { bw.write("#PLAYER " + jugadorPos.x() + " " + jugadorPos.y() + "\n"); }
        }
    }

    public static BasicBlock[][] load(File file) throws IOException { return loadWithPlayer(file).mundo(); }

    /** Carga mundo sin comprimir y posición de jugador si existe. */
    public static WorldData loadWithPlayer(File file) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String header = br.readLine();
            if (header == null || header.isEmpty()) return new WorldData(new BasicBlock[0][0], null);
            StringTokenizer st = new StringTokenizer(header); int ancho = Integer.parseInt(st.nextToken()); int alto = Integer.parseInt(st.nextToken());
            BasicBlock[][] mundo = new BasicBlock[alto][ancho]; double size = BasicBlock.getSize();
            for (int arrayY = 0; arrayY < alto; arrayY++) {
                String line = br.readLine(); if (line == null) break; if (line.startsWith("#")) { arrayY--; continue; }
                String[] tokens = line.split(",");
                for (int x = 0; x < Math.min(ancho, tokens.length); x++) {
                    String t = tokens[x].trim(); if (t.equals(".")) { mundo[arrayY][x] = null; } else if (!t.isEmpty()) {
                        int tileYTop = alto - 1 - arrayY; mundo[arrayY][x] = new BasicBlock(t, new Punto(x * size, tileYTop * size)); }
                }
            }
            Punto jugadorPos = null; String line; while ((line = br.readLine()) != null) {
                line = line.trim(); if (line.startsWith("#PLAYER")) { StringTokenizer pst = new StringTokenizer(line.substring(7).trim()); if (pst.hasMoreTokens()) { double x = Double.parseDouble(pst.nextToken()); double y = pst.hasMoreTokens() ? Double.parseDouble(pst.nextToken()) : 0.0; jugadorPos = new Punto(x, y); } }
            }
            return new WorldData(mundo, jugadorPos);
        }
    }

    // --- COMPRESIÓN RLE ---
    private static String rleEncodeRow(BasicBlock[] fila) {
        StringBuilder sb = new StringBuilder();
        int n = fila.length; int i = 0;
        while (i < n) {
            BasicBlock b = fila[i]; String id = (b == null ? "." : b.getId()); int run = 1; i++;
            while (i < n) { BasicBlock nb = fila[i]; String nid = (nb == null ? "." : nb.getId()); if (!nid.equals(id)) break; run++; i++; }
            if (sb.length() > 0) sb.append(';'); sb.append(id).append('*').append(run);
        }
        return sb.toString();
    }

    private static String rleEncodeColumn(BasicBlock[][] mundo, int x) {
        StringBuilder sb = new StringBuilder();
        int alto = mundo.length; int y = 0;
        while (y < alto) {
            BasicBlock b = mundo[y][x]; String id = (b == null ? "." : b.getId()); int run = 1; y++;
            while (y < alto) { BasicBlock nb = mundo[y][x]; String nid = (nb == null ? "." : nb.getId()); if (!nid.equals(id)) break; run++; y++; }
            if (sb.length() > 0) sb.append(';'); sb.append(id).append('*').append(run);
        }
        return sb.toString();
    }

    private static void rleDecodeRow(String line, BasicBlock[] fila, int arrayY, int alto, double size) {
        String[] segments = line.split(";"); int x = 0;
        for (String seg : segments) {
            if (seg.isEmpty()) continue; int star = seg.lastIndexOf('*'); if (star <= 0) continue;
            String id = seg.substring(0, star); int count = Integer.parseInt(seg.substring(star+1));
            for (int k = 0; k < count && x < fila.length; k++) {
                if (!id.equals(".")) { int tileYTop = alto - 1 - arrayY; fila[x] = new BasicBlock(id, new Punto(x * size, tileYTop * size)); } else { fila[x] = null; }
                x++;
            }
            if (x >= fila.length) break;
        }
        while (x < fila.length) { fila[x++] = null; }
    }

    private static void rleDecodeColumn(String line, BasicBlock[][] mundo, int x, int alto, double size) {
        String[] segments = line.split(";"); int y = 0;
        for (String seg : segments) {
            if (seg.isEmpty()) continue; int star = seg.lastIndexOf('*'); if (star <= 0) continue;
            String id = seg.substring(0, star); int count = Integer.parseInt(seg.substring(star+1));
            for (int k = 0; k < count && y < alto; k++) {
                if (!id.equals(".")) { int tileYTop = alto - 1 - y; mundo[y][x] = new BasicBlock(id, new Punto(x * size, tileYTop * size)); } else { mundo[y][x] = null; }
                y++;
            }
            if (y >= alto) break;
        }
        while (y < alto) { mundo[y++][x] = null; }
    }

    /** Guarda el mundo comprimido (RLE + GZIP) y posición del jugador si se provee.
     * Escribe en la cabecera un flag de modo: 0 (ROW) o 1 (COL). Aplica meta-RLE de líneas N*LINE. */
    public static void saveCompressed(File file, BasicBlock[][] mundo, Punto jugadorPos) throws IOException {
        try (GZIPOutputStream gz = new GZIPOutputStream(new FileOutputStream(file)); BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(gz, StandardCharsets.UTF_8))) {
            if (mundo == null || mundo.length == 0) { bw.write("0 0\n"); return; }
            int alto = mundo.length; int ancho = mundo[0].length;
            // Precalcular representaciones
            java.util.List<String> rowLines = new java.util.ArrayList<>();
            for (int arrayY = 0; arrayY < alto; arrayY++) { rowLines.add(rleEncodeRow(mundo[arrayY])); }
            java.util.List<String> colLines = new java.util.ArrayList<>();
            for (int x = 0; x < ancho; x++) { colLines.add(rleEncodeColumn(mundo, x)); }
            int rowChars = rowLines.stream().mapToInt(s -> s.length() + 1).sum();
            int colChars = colLines.stream().mapToInt(s -> s.length() + 1).sum();
            boolean useCol = colChars < rowChars;
            int modeFlag = useCol ? 1 : 0;
            // Cabecera: modo ancho alto
            bw.write(modeFlag + " " + ancho + " " + alto + "\n");
            // Meta-RLE de líneas
            java.util.List<String> source = useCol ? colLines : rowLines;
            int i = 0;
            while (i < source.size()) {
                String current = source.get(i);
                int run = 1; i++;
                while (i < source.size() && source.get(i).equals(current)) { run++; i++; }
                if (run > 1) { bw.write(run + "*" + current + "\n"); }
                else { bw.write(current + "\n"); }
            }
            if (jugadorPos != null) { bw.write(jugadorPos.x() + " " + jugadorPos.y() + "\n"); }
        }
    }

    /** Carga mundo comprimido (RLE + GZIP) y posición de jugador. Cabecera estricta: modo ancho alto (modo=0 filas, 1 columnas). */
    public static WorldData loadCompressedWithPlayer(File file) throws IOException {
        try (GZIPInputStream gz = new GZIPInputStream(new FileInputStream(file)); BufferedReader br = new BufferedReader(new InputStreamReader(gz, StandardCharsets.UTF_8))) {
            String header = br.readLine();
            if (header == null || header.isEmpty()) throw new IOException("Cabecera vacía en archivo comprimido");
            StringTokenizer st = new StringTokenizer(header);
            if (st.countTokens() < 3) throw new IOException("Cabecera inválida (se esperaban 3 tokens: modo ancho alto)");
            int modeFlag = Integer.parseInt(st.nextToken());
            if (modeFlag != 0 && modeFlag != 1) throw new IOException("Flag de modo inválido (debe ser 0 o 1): " + modeFlag);
            int ancho = Integer.parseInt(st.nextToken());
            int alto = Integer.parseInt(st.nextToken());
            BasicBlock[][] mundo = new BasicBlock[alto][ancho];
            double size = BasicBlock.getSize();
            String line = null;
            if (modeFlag == 1) { // COL
                int x = 0;
                while (x < ancho && (line = br.readLine()) != null) {
                    if (line.isBlank()) continue;
                    int starPos = line.indexOf('*');
                    if (starPos > 0) {
                        try {
                            int repeat = Integer.parseInt(line.substring(0, starPos));
                            String encoded = line.substring(starPos + 1);
                            for (int r = 0; r < repeat && x < ancho; r++) {
                                rleDecodeColumn(encoded, mundo, x, alto, size);
                                x++;
                            }
                            continue;
                        } catch (NumberFormatException ignore) { /* tratar como línea normal */ }
                    }
                    rleDecodeColumn(line, mundo, x, alto, size); x++;
                }
            } else { // ROW
                int arrayY = 0;
                while (arrayY < alto && (line = br.readLine()) != null) {
                    if (line.isBlank()) continue;
                    int starPos = line.indexOf('*');
                    boolean handledGroup = false;
                    if (starPos > 0) {
                        try {
                            int repeat = Integer.parseInt(line.substring(0, starPos));
                            String encoded = line.substring(starPos + 1);
                            for (int r = 0; r < repeat && arrayY < alto; r++) {
                                rleDecodeRow(encoded, mundo[arrayY], arrayY, alto, size);
                                arrayY++;
                            }
                            handledGroup = true;
                        } catch (NumberFormatException ignore) { /* no es meta-RLE */ }
                    }
                    if (!handledGroup) {
                        rleDecodeRow(line, mundo[arrayY], arrayY, alto, size); arrayY++;
                    }
                }
            }
            // Leer posición del jugador como "x y" si existe una línea adicional
            Punto jugadorPos = null;
            // Consumir líneas en blanco
            while ((line = br.readLine()) != null && line.isBlank()) { /* skip */ }
            if (line != null) {
                StringTokenizer pst = new StringTokenizer(line.trim());
                if (pst.hasMoreTokens()) {
                    double x = Double.parseDouble(pst.nextToken());
                    double y = pst.hasMoreTokens() ? Double.parseDouble(pst.nextToken()) : 0.0;
                    jugadorPos = new Punto(x, y);
                }
            }
            return new WorldData(mundo, jugadorPos);
        } catch (NumberFormatException e) {
            throw new IOException("Error parseando cabecera/composición del mundo: " + e.getMessage(), e);
        }
    }
}
