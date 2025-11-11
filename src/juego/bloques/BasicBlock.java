package juego.bloques;

import tipos.Punto;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
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
    private final Image sprite;
    private final Punto p;
    private final String blockID;
    private final double dureza; // segundos necesarios de mantener click para romper

    /**
     * Crea un bloque de tipo dado en la posición indicada.
     * @param blockID identificador (p.ej. "stone", "dirt")
     * @param p posición en píxeles del mundo (esquina superior izquierda del bloque)
     */
    public BasicBlock(String blockID, Punto p) {
        this.blockID = blockID;
        this.sprite = cargarImagen("assets/blocks/" + blockID + ".png");
        this.p = p;
        this.dureza = calcularDureza(blockID);
    }

    /**
     * Identificador de tipo de bloque (p.ej. "stone", "dirt").
     */
    public String getId() { return blockID; }

    /**
     * Tamaño del bloque en píxeles de lado.
     */
    public static double getSize() {
        return SIZE;
    }

    private Image cargarImagen(String path) {
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        URL url = getClass().getClassLoader().getResource(normalized);
        if (url != null) {
            return new ImageIcon(url).getImage();
        }
        File file = new File("../src/" + normalized);
        if (file.exists()) {
            return new ImageIcon(file.getAbsolutePath()).getImage();
        }
        throw new IllegalStateException("No se pudo cargar la imagen del bloque: " + path);
    }

    /**
     * Dibuja el bloque en la posición indicada.
     */
    public void draw(Graphics2D g) {
        AffineTransform at = g.getTransform();
        g.translate(p.x(), p.y());
        g.drawImage(sprite, 0, 0, (int) SIZE, (int) SIZE, null);
        g.setTransform(at);
    }

    /**
     * Dureza en segundos necesarios de "minado" continuo para romper el bloque.
     */
    public double getDureza() { return dureza; }

    private double calcularDureza(String id) {
        // Valores ejemplo; ajustar según diseño
        return switch (id) {
            case "stone" -> 1.5;
            case "dirt" -> 0.8;
            case "sand" -> 0.4;
            case "grass_block" -> 0.6;
            default -> 1.0;
        };
    }

    // --- Nuevos helpers de colisión ---
    /** Rectángulo de colisión del bloque en píxeles del mundo. */
    public Rectangle2D getBounds() {
        return new Rectangle2D.Double(p.x(), p.y(), SIZE, SIZE);
    }
}
