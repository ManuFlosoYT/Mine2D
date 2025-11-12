package componentes;

import juego.bloques.BasicBlock;
import tipos.Punto;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.StringTokenizer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * E/S de mundos comprimidos (RLE + GZIP) codificando directamente IDs de bloque.
 *
 * Formato:
 * - Cabecera: modo ancho alto (modo=0 filas, 1 columnas)
 * - Cuerpo: líneas RLE; cada línea es una secuencia de segmentos token*count separados por ';'
 *   donde token es la ID del bloque (por ejemplo "stone", "water") o "." para aire.
 * - Meta-RLE: puede repetirse una línea idéntica N veces escribiendo N*LINE en lugar de repetirla N veces.
 * - Línea final opcional: "x y" con la posición del jugador.
 */
public final class WorldIO {
    private WorldIO() {}

    private static final String AIR = ".";

    // ------------------ RLE helpers con tokens ------------------
    private static String rleEncodeRowTokens(String[] fila) {
        StringBuilder sb = new StringBuilder();
        int n = fila.length; int i = 0;
        while (i < n) {
            String tok = fila[i]; int run = 1; i++;
            while (i < n && fila[i].equals(tok)) { run++; i++; }
            if (sb.length() > 0) sb.append(';'); sb.append(tok).append('*').append(run);
        }
        return sb.toString();
    }
    private static String rleEncodeColumnTokens(String[][] tokens, int x) {
        StringBuilder sb = new StringBuilder();
        int alto = tokens.length; int y = 0;
        while (y < alto) {
            String tok = tokens[y][x]; int run = 1; y++;
            while (y < alto && tokens[y][x].equals(tok)) { run++; y++; }
            if (sb.length() > 0) sb.append(';'); sb.append(tok).append('*').append(run);
        }
        return sb.toString();
    }
    private static void rleDecodeRowTokens(String line, String[] dest) throws IOException {
        String[] segments = line.split(";"); int x = 0;
        for (String seg : segments) {
            if (seg.isEmpty()) continue; int star = seg.lastIndexOf('*'); if (star <= 0) throw new IOException("Segmento RLE inválido: " + seg);
            String tok = seg.substring(0, star);
            int count = Integer.parseInt(seg.substring(star+1));
            for (int k = 0; k < count && x < dest.length; k++) { dest[x++] = tok; }
        }
        while (x < dest.length) dest[x++] = AIR;
    }
    private static void rleDecodeColumnTokens(String line, String[][] tokens, int x) throws IOException {
        String[] segments = line.split(";"); int y = 0;
        for (String seg : segments) {
            if (seg.isEmpty()) continue; int star = seg.lastIndexOf('*'); if (star <= 0) throw new IOException("Segmento RLE inválido: " + seg);
            String tok = seg.substring(0, star);
            int count = Integer.parseInt(seg.substring(star+1));
            for (int k = 0; k < count && y < tokens.length; k++) { tokens[y][x] = tok; y++; }
        }
        while (y < tokens.length) tokens[y++][x] = AIR;
    }

    /** Guarda el mundo comprimido (RLE + GZIP) y posición del jugador. */
    public static void saveCompressed(File file, BasicBlock[][] mundo, Punto jugadorPos) throws IOException {
        try (GZIPOutputStream gz = new GZIPOutputStream(new FileOutputStream(file)); BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(gz, StandardCharsets.UTF_8))) {
            if (mundo == null || mundo.length == 0) { bw.write("0 0\n"); return; }
            int alto = mundo.length; int ancho = mundo[0].length;
            // Convertir mundo a tokens (IDs o '.')
            String[][] tokens = new String[alto][ancho];
            for (int y = 0; y < alto; y++) {
                for (int x = 0; x < ancho; x++) {
                    BasicBlock b = mundo[y][x];
                    tokens[y][x] = (b == null) ? AIR : b.getId();
                }
            }
            // Decidir modo por longitud total
            java.util.List<String> rowLines = new java.util.ArrayList<>();
            for (int y = 0; y < alto; y++) rowLines.add(rleEncodeRowTokens(tokens[y]));
            java.util.List<String> colLines = new java.util.ArrayList<>();
            for (int x = 0; x < ancho; x++) colLines.add(rleEncodeColumnTokens(tokens, x));
            int rowChars = rowLines.stream().mapToInt(s -> s.length() + 1).sum();
            int colChars = colLines.stream().mapToInt(s -> s.length() + 1).sum();
            boolean useCol = colChars < rowChars;
            int modeFlag = useCol ? 1 : 0;
            bw.write(modeFlag + " " + ancho + " " + alto + "\n");
            java.util.List<String> src = useCol ? colLines : rowLines;
            int i = 0;
            while (i < src.size()) {
                String current = src.get(i);
                int run = 1; i++;
                while (i < src.size() && src.get(i).equals(current)) { run++; i++; }
                if (run > 1) bw.write(run + "*" + current + "\n");
                else bw.write(current + "\n");
            }
            if (jugadorPos != null) bw.write(jugadorPos.x() + " " + jugadorPos.y() + "\n");
        }
    }

    /** Carga mundo comprimido (RLE + GZIP) y posición de jugador. */
    public static WorldData loadCompressedWithPlayer(File file) throws IOException {
        try (GZIPInputStream gz = new GZIPInputStream(new FileInputStream(file)); BufferedReader br = new BufferedReader(new InputStreamReader(gz, StandardCharsets.UTF_8))) {
            String header = br.readLine();
            if (header == null || header.isEmpty()) throw new IOException("Cabecera vacía en archivo comprimido");
            StringTokenizer st = new StringTokenizer(header);
            if (st.countTokens() < 3) throw new IOException("Cabecera inválida (modo ancho alto)");
            int modeFlag = Integer.parseInt(st.nextToken());
            if (modeFlag != 0 && modeFlag != 1) throw new IOException("Flag de modo inválido: " + modeFlag);
            int ancho = Integer.parseInt(st.nextToken());
            int alto = Integer.parseInt(st.nextToken());

            String[][] tokens = new String[alto][ancho];
            String line;
            if (modeFlag == 1) { // columnas
                int x = 0;
                while (x < ancho && (line = br.readLine()) != null) {
                    if (line.isBlank()) continue;
                    int starPos = line.indexOf('*');
                    if (starPos > 0) {
                        try {
                            int repeat = Integer.parseInt(line.substring(0, starPos));
                            String encoded = line.substring(starPos + 1);
                            for (int r = 0; r < repeat && x < ancho; r++) { rleDecodeColumnTokens(encoded, tokens, x); x++; }
                            continue;
                        } catch (NumberFormatException ignore) { }
                    }
                    rleDecodeColumnTokens(line, tokens, x); x++;
                }
            } else { // filas
                int y = 0;
                while (y < alto && (line = br.readLine()) != null) {
                    if (line.isBlank()) continue;
                    int starPos = line.indexOf('*');
                    boolean handledGroup = false;
                    if (starPos > 0) {
                        try {
                            int repeat = Integer.parseInt(line.substring(0, starPos));
                            String encoded = line.substring(starPos + 1);
                            for (int r = 0; r < repeat && y < alto; r++) { rleDecodeRowTokens(encoded, tokens[y]); y++; }
                            handledGroup = true;
                        } catch (NumberFormatException ignore) { }
                    }
                    if (!handledGroup) { rleDecodeRowTokens(line, tokens[y]); y++; }
                }
            }

            // Convertir tokens a bloques
            BasicBlock[][] mundo = new BasicBlock[alto][ancho];
            double size = BasicBlock.getSize();
            for (int arrayY = 0; arrayY < alto; arrayY++) {
                for (int x = 0; x < ancho; x++) {
                    String tok = tokens[arrayY][x];
                    if (tok == null || AIR.equals(tok)) mundo[arrayY][x] = null;
                    else {
                        int tileYTop = alto - 1 - arrayY;
                        mundo[arrayY][x] = new BasicBlock(tok, new Punto(x * size, tileYTop * size));
                    }
                }
            }

            // Posición del jugador (opcional)
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
        }
    }
}
