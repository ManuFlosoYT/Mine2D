package juego;

import componentes.Input;
import programa.Main;
import tipos.Punto;
import juego.bloques.BasicBlock;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.net.URL;
import java.util.List;

public class Jugador {
    private static final double SIZE = 64; // tamaño del sprite en píxeles
    private final Image sprite;

    private double x;
    private double y;

    private double vx;
    private double vy;

    // Parámetros de física
    private static final double VX_ACEL = 1200;      // px/s^2 aceleración horizontal
    private static final double VX_FRICTION = 1100;  // px/s^2 fricción cuando no se pulsa nada
    private static final double VX_MAX = 300;        // px/s velocidad horizontal máxima

    private static final double GRAVEDAD = 900;      // px/s^2 gravedad
    private static final double VY_SALTO = 500;      // px/s velocidad inicial de salto
    private static final double VY_MAX_CAIDA = 800;  // px/s límite máximo de caída

    // Jump buffering & coyote time
    private static final double JUMP_BUFFER_TIME = 0.20; // segundos
    private static final double COYOTE_TIME = 0.10;      // segundos
    private double jumpBufferTimer = 0.0;
    private double coyoteTimer = 0.0;

    private boolean enSuelo;

    public Jugador() {
        sprite = cargarImagen("assets/test.png");
    }

    private Image cargarImagen(@org.jetbrains.annotations.NotNull String path) {
        // Intentar vía classpath
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        URL url = getClass().getClassLoader().getResource(normalized);
        if (url != null) {
            return new ImageIcon(url).getImage();
        }
        // Fallback relativo al directorio de ejecución (out), apuntando a ../src/
        File file = new File("../src/" + normalized);
        if (file.exists()) {
            return new ImageIcon(file.getAbsolutePath()).getImage();
        }
        throw new IllegalStateException("No se pudo cargar la imagen: " + path + " (normalizado: " + normalized + ")" );
    }

    public void draw(Graphics2D g) {
        AffineTransform at = g.getTransform();
        g.translate(x, y);
        // dibujar escalado a SIZE para coincidir con colisiones
        g.drawImage(sprite, 0, 0, (int) SIZE, (int) SIZE, null);
        g.setTransform(at);
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getVX() { return vx; }
    public double getVY() { return vy; }

    public int getAltoPx(){
        return (int) SIZE; // altura del sprite (suponemos cuadrado)
    }

    public void colocar(Punto p){
        this.x = p.x();
        this.y = p.y();
    }

    // Nuevo: bounds para colisión
    public Rectangle2D getBounds() {
        return new Rectangle2D.Double(x, y, SIZE, SIZE);
    }

    // Update recibe lista de bloques para colisión
    public void update(Input input, double dt, List<BasicBlock> bloques){
        // --- INPUT: izquierda/derecha ---
        boolean left = input.isKeyA();
        boolean right = input.isKeyD();

        // --- Actualizar coyote timer según estado del frame anterior ---
        if (enSuelo) {
            coyoteTimer = COYOTE_TIME;
        } else {
            coyoteTimer -= dt;
            if (coyoteTimer < 0) coyoteTimer = 0;
        }

        // --- Registrar salto en buffer si hay flanco ---
        if (input.consumeSpacePressedOnce()) {
            jumpBufferTimer = JUMP_BUFFER_TIME;
        } else if (jumpBufferTimer > 0) {
            jumpBufferTimer -= dt;
            if (jumpBufferTimer < 0) jumpBufferTimer = 0;
        }

        // --- Movimiento horizontal con aceleración / fricción ---
        if (left && !right) {
            vx -= VX_ACEL * dt;
        } else if (right && !left) {
            vx += VX_ACEL * dt;
        } else {
            if (vx > 0) {
                vx -= VX_FRICTION * dt;
                if (vx < 0) vx = 0;
            } else if (vx < 0) {
                vx += VX_FRICTION * dt;
                if (vx > 0) vx = 0;
            }
        }

        if (vx > VX_MAX) vx = VX_MAX;
        if (vx < -VX_MAX) vx = -VX_MAX;

        // --- Decidir salto ANTES de integrar eje Y ---
        if (jumpBufferTimer > 0 && (enSuelo || coyoteTimer > 0)) {
            vy = -VY_SALTO; // hacia arriba
            enSuelo = false;
            jumpBufferTimer = 0; // consumir buffer al usarlo
            coyoteTimer = 0;
        }

        // --- Gravedad ---
        if (!enSuelo) {
            vy += GRAVEDAD * dt; // hacia abajo positiva
            if (vy > VY_MAX_CAIDA) vy = VY_MAX_CAIDA;
        }

        // --- Integración y colisión eje X ---
        double nuevoX = x + vx * dt;
        Rectangle2D futuroX = new Rectangle2D.Double(nuevoX, y, SIZE, SIZE);
        for (BasicBlock b : bloques) {
            if (futuroX.intersects(b.getBounds())) {
                Rectangle2D br = b.getBounds();
                if (vx > 0) {
                    nuevoX = br.getX() - SIZE; // a la izquierda del bloque
                } else if (vx < 0) {
                    nuevoX = br.getX() + br.getWidth(); // a la derecha del bloque
                }
                vx = 0;
                break; // ya resolvimos una colisión horizontal
            }
        }
        x = nuevoX;

        // --- Integración y colisión eje Y ---
        double nuevoY = y + vy * dt;
        Rectangle2D futuroY = new Rectangle2D.Double(x, nuevoY, SIZE, SIZE);
        enSuelo = false;
        for (BasicBlock b : bloques) {
            if (futuroY.intersects(b.getBounds())) {
                Rectangle2D br = b.getBounds();
                if (vy > 0) { // cayendo
                    nuevoY = br.getY() - SIZE; // arriba del bloque
                    vy = 0;
                    enSuelo = true;
                } else if (vy < 0) { // subiendo
                    nuevoY = br.getY() + br.getHeight(); // debajo (techo)
                    vy = 0;
                }
                break; // una colisión vertical es suficiente
            }
        }
        y = nuevoY;
    }

}
