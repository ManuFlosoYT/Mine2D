package juego.bloques;

import tipos.Punto;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;

/**
 * Bloque básico del mundo, renderizable y colisionable.
 *
 * <p>Cada bloque tiene un identificador (ID) que determina su sprite y su dureza
 * (tiempo necesario para romperlo). Su posición se almacena en píxeles del mundo.</p>
 */
public class BasicBlock {

    private static final double SIZE = 64; // tamaño del sprite en píxeles
    private final BufferedImage sprite;
    private final BufferedImage[] tintCache = new BufferedImage[16]; // 0..15 niveles de luz
    private final Punto p;
    private final String blockID;
    private final double dureza; // segundos necesarios de mantener click para romper
    private final BlockType type;

    /**
     * Crea un bloque de tipo dado en la posición indicada.
     * @param blockID identificador (p.ej. "stone", "dirt")
     * @param p posición en píxeles del mundo (esquina superior izquierda del bloque)
     */
    public BasicBlock(String blockID, Punto p) {
        this(BlockType.fromId(blockID), p);
    }

    /**
     * Crea un bloque desde su tipo enum.
     */
    public BasicBlock(BlockType type, Punto p) {
        this.type = (type != null) ? type : BlockType.UNKNOWN;
        this.blockID = this.type.getId();
        this.sprite = cargarImagen("assets/blocks/" + this.blockID + ".png");
        this.p = p;
        this.dureza = this.type.getHardness();
    }

    /** Identificador de tipo de bloque (p.ej. "stone", "dirt"). */
    public String getId() { return blockID; }

    /** Tipo del bloque. */
    public BlockType getType() { return type; }

    /** Tamaño del bloque en píxeles de lado. */
    public static double getSize() { return SIZE; }

    private BufferedImage cargarImagen(String path) {
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        URL url = getClass().getClassLoader().getResource(normalized);
        if (url != null) {
            return toBuffered(new ImageIcon(url).getImage());
        }
        File file = new File("../src/" + normalized);
        if (file.exists()) {
            return toBuffered(new ImageIcon(file.getAbsolutePath()).getImage());
        }
        throw new IllegalStateException("No se pudo cargar la imagen del bloque: " + path);
    }

    private BufferedImage toBuffered(Image img) {
        if (img instanceof BufferedImage bi) return bi;
        BufferedImage buff = new BufferedImage((int)SIZE, (int)SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = buff.createGraphics();
        g.drawImage(img, 0, 0, (int)SIZE, (int)SIZE, null);
        g.dispose();
        return buff;
    }

    /** Dureza en segundos necesarios de "minado" continuo para romper el bloque. */
    public double getDureza() { return dureza; }

    /** Por defecto los bloques son rompibles. */
    public boolean isBreakable() { return true; }

    // --- Nuevos helpers de colisión ---
    /** Rectángulo de colisión del bloque en píxeles del mundo. */
    public Rectangle2D getBounds() { return new Rectangle2D.Double(p.x(), p.y(), SIZE, SIZE); }

    // --- Soporte para subclases: acceso protegido a la posición ---
    protected double getX() { return p.x(); }
    protected double getY() { return p.y(); }

    /** Dibuja el bloque con brillo (0..1). */
    public void drawTinted(Graphics2D g, double brightness) {
        if (g == null) return;
        int level;
        if (brightness >= 0.999) {
            level = 15;
        } else if (brightness <= 0.0) {
            level = 0;
        } else {
            level = (int)Math.round(brightness * 15.0);
            if (level < 0) level = 0; if (level > 15) level = 15;
        }
        BufferedImage img = getTinted(level);
        AffineTransform at = g.getTransform();
        g.translate(p.x(), p.y());
        g.drawImage(img, 0, 0, (int) SIZE, (int) SIZE, null);
        g.setTransform(at);
    }

    /** Versión original sin tintado (bright = 1). */
    public void draw(Graphics2D g) { drawTinted(g, 1.0); }

    private BufferedImage getTinted(int level) {
        if (level >= 15) return sprite;
        if (level < 0) level = 0;
        BufferedImage cached = tintCache[level];
        if (cached != null) return cached;
        double brightness = level / 15.0; // 0..1
        BufferedImage tinted = new BufferedImage(sprite.getWidth(), sprite.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < sprite.getHeight(); y++) {
            for (int x = 0; x < sprite.getWidth(); x++) {
                int argb = sprite.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int gCh = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                r = (int)(r * brightness);
                gCh = (int)(gCh * brightness);
                b = (int)(b * brightness);
                int newArgb = (a << 24) | (r << 16) | (gCh << 8) | b;
                tinted.setRGB(x, y, newArgb);
            }
        }
        tintCache[level] = tinted;
        return tinted;
    }
}
