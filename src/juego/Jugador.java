package juego;

import componentes.Input;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.net.URL;
import java.util.List;
import javax.swing.*;
import juego.bloques.BasicBlock;
import tipos.Punto;

/**
 * Entidad controlable por el usuario que se desplaza y salta en el mundo.
 *
 * <p>Incluye un modelo simple de física 2D: aceleración/fricción horizontal, gravedad,
 * salto con jump buffer y coyote time, y colisiones AABB contra bloques sólidos.</p>
 */
public class Jugador {
    // Un bloque mide 64px (consultamos BasicBlock.getSize())
    private static final double BLOCK_SIZE = BasicBlock.getSize();
    private static final double WIDTH = BLOCK_SIZE * 0.8;      // 0.8 bloques de ancho
    private static final double HEIGHT = BLOCK_SIZE * 1.8;     // 1.8 bloques de alto
    private final Image sprite;

    private final PlayerPhysics physics = new PlayerPhysics();
    private double x;
    private double y;

    /** Crea una instancia del jugador y carga su sprite. */
    public Jugador() {
        sprite = cargarImagen();
    }

    private Image cargarImagen() {
        String path = "assets/player.png";
        URL url = getClass().getClassLoader().getResource(path);
        if (url != null) {
            return new ImageIcon(url).getImage();
        }
        File file = new File("../src/" + path);
        if (file.exists()) {
            return new ImageIcon(file.getAbsolutePath()).getImage();
        }
        throw new IllegalStateException("No se pudo cargar la imagen: " + path + " (normalizado: " + path + ")" );
    }

    /** Dibuja el sprite del jugador en su posición actual. */
    public void draw(Graphics2D g) {
        AffineTransform at = g.getTransform();
        g.translate(x, y);
        // dibujar escalado a WIDTH x HEIGHT para coincidir con colisiones
        g.drawImage(sprite, 0, 0, (int) WIDTH, (int) HEIGHT, null);
        g.setTransform(at);
    }

    /** Posición X (píxeles del mundo). */
    public double getX() { return x; }
    /** Posición Y (píxeles del mundo). */
    public double getY() { return y; }
    
    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }

    /** Alto visual/colisional del jugador en píxeles. */
    public int getAltoPx(){
        return (int) HEIGHT; // altura del sprite (1.8 bloques)
    }

    /** Ancho visual/colisional del jugador en píxeles. */
    public int getAnchoPx(){ return (int) WIDTH; }

    /** Coloca al jugador en la posición indicada. */
    public void colocar(Punto p){
        this.x = p.x();
        this.y = p.y();
    }

    public Punto getPosicion() {
        return new Punto(x, y);
    }

    /**
     * Rectángulo de colisión del jugador en píxeles del mundo.
     */
    public Rectangle2D getBounds() {
        return new Rectangle2D.Double(x, y, WIDTH, HEIGHT);
    }

    /**
     * Actualiza el estado del jugador: procesa input, integra física y resuelve colisiones.
     * @param input estado de entrada (teclas)
     * @param dt delta de tiempo en segundos
     * @param bloques lista de bloques cercanos para pruebas de colisión
     */
    public void update(Input input, double dt, List<BasicBlock> bloques){
        physics.update(this, input, dt, bloques);
    }
}
