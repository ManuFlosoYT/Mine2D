package componentes;

import juego.bloques.BasicBlock;
import tipos.Punto;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.StringTokenizer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Entrada/Salida de mundos en formato comprimido (RLE + GZIP) con diccionario de IDs.
 *
 * Formato comprimido:
 * - Primera línea: modo ancho alto [|id=ch;id=ch;...|], donde modo=0 (ROW) o modo=1 (COL).
 * - Si ROW: cada fila se codifica en segmentos ch*count separados por ';'.
 * - Si COL: cada columna X se codifica igual, en orden 0..ancho-1.
 * - Soporta meta-RLE de líneas: N*LINE para repetir LINE N veces.
 * - Última línea opcional: "x y" con la posición del jugador.
 */
public final class WorldIO {
    private WorldIO() {}

    // Caracter reservado para aire
    private static final char AIR_CHAR = '.';
    // Delimitadores diccionario
    private static final char DICT_START = '|';
    private static final char DICT_END = '|';

    /** Construye un diccionario id->char determinista y compacto. */
    private static java.util.Map<String, Character> buildDictionary(BasicBlock[][] mundo) {
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        if (mundo != null) {
            for (BasicBlock[] fila : mundo) {
                if (fila == null) continue;
                for (BasicBlock b : fila) { if (b != null) ids.add(b.getId()); }
            }
        }
        // Cadena de caracteres disponibles (evitar '.', '*', ';', '#', '|', ',', '=')
        String pool = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!$%&()+-<>?@^{}_";
        java.util.Map<String, Character> map = new java.util.LinkedHashMap<>();
        int i = 0;
        for (String id : ids) {
            if (i >= pool.length()) throw new IllegalStateException("Demasiados tipos de bloque para diccionario (max="+pool.length()+")");
            map.put(id, pool.charAt(i++));
        }
        return map;
    }

    /** Formatea el diccionario para cabecera: |id=ch;id=ch;...| */
    private static String formatDictionary(java.util.Map<String, Character> dict) {
        if (dict == null || dict.isEmpty()) return ""; // sin diccionario => se asumirá aire/IDs desconocidos como aire
        StringBuilder sb = new StringBuilder(); sb.append(DICT_START);
        boolean first = true;
        for (var e : dict.entrySet()) {
            if (!first) sb.append(';'); first = false;
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        sb.append(DICT_END);
        return sb.toString();
    }

    /** Parsea el diccionario de la cabecera si existe. */
    private static java.util.Map<Character,String> parseDictionary(String header) {
        int start = header.indexOf(DICT_START);
        int end = header.lastIndexOf(DICT_END);
        java.util.Map<Character,String> map = new java.util.HashMap<>();
        if (start >= 0 && end > start) {
            String inner = header.substring(start+1, end).trim();
            if (!inner.isEmpty()) {
                String[] parts = inner.split(";");
                for (String p : parts) {
                    int eq = p.indexOf('='); if (eq <= 0 || eq >= p.length()-1) continue;
                    String id = p.substring(0, eq).trim();
                    char ch = p.charAt(eq+1);
                    if (ch != AIR_CHAR) map.put(ch, id);
                }
            }
        }
        return map;
    }

    // --- UTILIDADES RLE ---
    private static String rleEncodeRowChars(char[] fila) {
        StringBuilder sb = new StringBuilder();
        int n = fila.length; int i = 0;
        while (i < n) {
            char ch = fila[i]; int run = 1; i++;
            while (i < n && fila[i] == ch) { run++; i++; }
            if (sb.length() > 0) sb.append(';'); sb.append(ch).append('*').append(run);
        }
        return sb.toString();
    }
    private static String rleEncodeColumnChars(char[][] mundoChars, int x) {
        StringBuilder sb = new StringBuilder();
        int alto = mundoChars.length; int y = 0;
        while (y < alto) {
            char ch = mundoChars[y][x]; int run = 1; y++;
            while (y < alto && mundoChars[y][x] == ch) { run++; y++; }
            if (sb.length() > 0) sb.append(';'); sb.append(ch).append('*').append(run);
        }
        return sb.toString();
    }
    private static void rleDecodeRowChars(String line, char[] fila) {
        String[] segments = line.split(";"); int x = 0;
        for (String seg : segments) {
            if (seg.isEmpty()) continue; int star = seg.lastIndexOf('*'); if (star <= 0) continue;
            char ch = seg.charAt(0); // antes de '*'
            int count = Integer.parseInt(seg.substring(star+1));
            for (int k = 0; k < count && x < fila.length; k++) { fila[x++] = ch; }
        }
        while (x < fila.length) fila[x++] = AIR_CHAR;
    }
    private static void rleDecodeColumnChars(String line, char[][] mundoChars, int x) {
        String[] segments = line.split(";"); int y = 0;
        for (String seg : segments) {
            if (seg.isEmpty()) continue; int star = seg.lastIndexOf('*'); if (star <= 0) continue;
            char ch = seg.charAt(0); int count = Integer.parseInt(seg.substring(star+1));
            for (int k = 0; k < count && y < mundoChars.length; k++) { mundoChars[y][x] = ch; y++; }
        }
        while (y < mundoChars.length) mundoChars[y++][x] = AIR_CHAR;
    }

    /** Guarda el mundo comprimido (RLE + GZIP) y posición del jugador; añade diccionario en cabecera. */
    public static void saveCompressed(File file, BasicBlock[][] mundo, Punto jugadorPos) throws IOException {
        try (GZIPOutputStream gz = new GZIPOutputStream(new FileOutputStream(file)); BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(gz, StandardCharsets.UTF_8))) {
            if (mundo == null || mundo.length == 0) { bw.write("0 0\n"); return; }
            int alto = mundo.length; int ancho = mundo[0].length;
            var dict = buildDictionary(mundo);
            String dictHeader = formatDictionary(dict);
            // Convertir mundo a matriz de chars
            char[][] mundoChars = new char[alto][ancho];
            for (int y = 0; y < alto; y++) {
                for (int x = 0; x < ancho; x++) {
                    BasicBlock b = mundo[y][x];
                    char out = AIR_CHAR;
                    if (b != null) {
                        Character ch = dict.get(b.getId());
                        out = (ch != null ? ch : AIR_CHAR);
                    }
                    mundoChars[y][x] = out;
                }
            }
            // Decidir modo (filas/columnas) usando longitud RLE char
            java.util.List<String> rowLines = new java.util.ArrayList<>();
            for (int arrayY = 0; arrayY < alto; arrayY++) { rowLines.add(rleEncodeRowChars(mundoChars[arrayY])); }
            java.util.List<String> colLines = new java.util.ArrayList<>();
            for (int x = 0; x < ancho; x++) { colLines.add(rleEncodeColumnChars(mundoChars, x)); }
            int rowChars = rowLines.stream().mapToInt(s -> s.length() + 1).sum();
            int colChars = colLines.stream().mapToInt(s -> s.length() + 1).sum();
            boolean useCol = colChars < rowChars;
            int modeFlag = useCol ? 1 : 0;
            // Cabecera: modo ancho alto [diccionario]
            bw.write(modeFlag + " " + ancho + " " + alto + (dictHeader.isEmpty()?"":" " + dictHeader) + "\n");
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

    /** Carga mundo comprimido (RLE + GZIP) y posición de jugador. Soporta diccionario opcional. */
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
            java.util.Map<Character,String> dictMap = parseDictionary(header);
            BasicBlock[][] mundo = new BasicBlock[alto][ancho];
            char[][] mundoChars = new char[alto][ancho];
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
                                rleDecodeColumnChars(encoded, mundoChars, x);
                                x++;
                            }
                            continue;
                        } catch (NumberFormatException ignore) { }
                    }
                    rleDecodeColumnChars(line, mundoChars, x); x++;
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
                                rleDecodeRowChars(encoded, mundoChars[arrayY]);
                                arrayY++;
                            }
                            handledGroup = true;
                        } catch (NumberFormatException ignore) { }
                    }
                    if (!handledGroup) {
                        rleDecodeRowChars(line, mundoChars[arrayY]); arrayY++;
                    }
                }
            }
            // Convertir chars a bloques
            double size = BasicBlock.getSize();
            for (int arrayY = 0; arrayY < alto; arrayY++) {
                for (int x = 0; x < ancho; x++) {
                    char c = mundoChars[arrayY][x];
                    if (c == AIR_CHAR) { mundo[arrayY][x] = null; }
                    else {
                        String id = dictMap.get(c);
                        if (id != null) {
                            int tileYTop = alto - 1 - arrayY;
                            mundo[arrayY][x] = new BasicBlock(id, new Punto(x * size, tileYTop * size));
                        } else { mundo[arrayY][x] = null; }
                    }
                }
            }
            // Leer posición del jugador
            Punto jugadorPos = null;
            while ((line = br.readLine()) != null && line.isBlank()) { }
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
