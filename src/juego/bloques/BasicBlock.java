package juego.bloques;

import tipos.Punto;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.net.URL;
import java.util.Objects;

public class BasicBlock {

    private static final double SIZE = 64; // tamaño del sprite en píxeles
    private final Image sprite;
    private final Punto p;
    private final String blockID;
    private final double dureza; // segundos necesarios de mantener click para romper

    public BasicBlock(String blockID, Punto p) {
        this.blockID = blockID;
        this.sprite = cargarImagen("assets/blocks/" + blockID + ".png");
        this.p = p;
        this.dureza = calcularDureza(blockID);
    }

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

    public void draw(Graphics2D g) {
        AffineTransform at = g.getTransform();
        g.translate(p.x(), p.y());
        g.drawImage(sprite, 0, 0, (int) SIZE, (int) SIZE, null);
        g.setTransform(at);
    }

    public Punto getP() {
        return p;
    }

    public String getBlockID() {
        return blockID;
    }

    public double getDureza() { return dureza; }

    private double calcularDureza(String id) {
        // Valores ejemplo; ajustar según diseño
        return switch (id) {
            case "stone" -> 1.2; // 1.5 segundos de mantener click
            case "dirt" -> 0.8;
            case "grass_block" -> 0.85;
            default -> 1.0; // valor por defecto
        };
    }

    // --- Nuevos helpers de colisión ---
    public Rectangle2D getBounds() {
        return new Rectangle2D.Double(p.x(), p.y(), SIZE, SIZE);
    }

    public double getX() { return p.x(); }
    public double getY() { return p.y(); }
}
