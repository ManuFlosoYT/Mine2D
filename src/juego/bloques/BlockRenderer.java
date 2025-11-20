package juego.bloques;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.*;

/**
 * Handles the rendering of blocks, including sprite loading and lighting tinting.
 */
public class BlockRenderer {

    private static final Map<String, BufferedImage> spriteCache = new ConcurrentHashMap<>();
    private static final Map<String, BufferedImage[]> tintCache = new ConcurrentHashMap<>();
    private static final double SIZE = BasicBlock.getSize();

    public void draw(Graphics2D g, BasicBlock block, double brightness) {
        if (g == null) return;

        BufferedImage img = getTintedSprite(block.getId(), brightness);
        
        AffineTransform at = g.getTransform();
        g.translate(block.getX(), block.getY());
        g.drawImage(img, 0, 0, (int) SIZE, (int) SIZE, null);
        g.setTransform(at);
    }

    private BufferedImage getTintedSprite(String blockId, double brightness) {
        int level = calculateLightLevel(brightness);
        
        // Check tint cache
        // Note: This simple cache key strategy might need improvement if memory usage becomes an issue,
        // but for now it separates tint arrays per block type. 
        // Actually, let's stick to the array approach per block ID for speed.
        
        BufferedImage[] tints = tintCache.computeIfAbsent(blockId, k -> new BufferedImage[16]);
        
        if (tints[level] != null) {
            return tints[level];
        }

        // Generate tinted image
        BufferedImage sprite = getSprite(blockId);
        BufferedImage tinted = createTintedImage(sprite, level);
        tints[level] = tinted;
        return tinted;
    }

    private int calculateLightLevel(double brightness) {
        if (brightness >= 0.999) return 15;
        if (brightness <= 0.0) return 0;
        int level = (int) Math.round(brightness * 15.0);
        return Math.max(0, Math.min(15, level));
    }

    private BufferedImage getSprite(String blockId) {
        return spriteCache.computeIfAbsent(blockId, this::loadSprite);
    }

    private BufferedImage loadSprite(String blockId) {
        String path = "assets/blocks/" + blockId + ".png";
        return loadAndConvertImage(path);
    }

    private BufferedImage loadAndConvertImage(String path) {
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        URL url = getClass().getClassLoader().getResource(normalized);
        Image img = null;
        
        if (url != null) {
            img = new ImageIcon(url).getImage();
        } else {
            File file = new File("../src/" + normalized);
            if (file.exists()) {
                img = new ImageIcon(file.getAbsolutePath()).getImage();
            }
        }

        if (img == null) {
             // Fallback or error handling could go here. For now, throwing as before.
             throw new IllegalStateException("No se pudo cargar la imagen del bloque: " + path);
        }

        return toBuffered(img);
    }

    private BufferedImage toBuffered(Image img) {
        if (img instanceof BufferedImage bi) return bi;
        BufferedImage buff = new BufferedImage((int) SIZE, (int) SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = buff.createGraphics();
        g.drawImage(img, 0, 0, (int) SIZE, (int) SIZE, null);
        g.dispose();
        return buff;
    }

    private BufferedImage createTintedImage(BufferedImage sprite, int level) {
        if (level >= 15) return sprite;
        
        double brightness = level / 15.0;
        BufferedImage tinted = new BufferedImage(sprite.getWidth(), sprite.getHeight(), BufferedImage.TYPE_INT_ARGB);
        
        for (int y = 0; y < sprite.getHeight(); y++) {
            for (int x = 0; x < sprite.getWidth(); x++) {
                int argb = sprite.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                
                r = (int) (r * brightness);
                g = (int) (g * brightness);
                b = (int) (b * brightness);
                
                int newArgb = (a << 24) | (r << 16) | (g << 8) | b;
                tinted.setRGB(x, y, newArgb);
            }
        }
        return tinted;
    }
}
