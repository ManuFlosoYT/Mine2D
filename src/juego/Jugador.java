package juego;

import componentes.Input;
import programa.Main;
import tipos.Punto;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.Objects;

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
    private static final double VX_MAX = 300;       // px/s velocidad horizontal máxima

    private static final double GRAVEDAD = 900;     // px/s^2 gravedad
    private static final double VY_SALTO = 500;     // px/s velocidad inicial de salto
    private static final double VY_MAX_CAIDA = 800; // px/s límite máximo de caída

    private boolean enSuelo;
    private boolean prevSpace; // para evitar saltos continuos manteniendo la tecla

    public Jugador() {
        sprite = new ImageIcon(Objects.requireNonNull(getClass().getResource("/assets/test.png"))).getImage();
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

    public void update(Input input, double dt){
        // Movimiento horizontal
        boolean left = input.isKeyA();
        boolean right = input.isKeyD();
        boolean space = input.isKeySpace();

        if (left && !right) {
            vx -= VX_ACEL * dt;
        } else if (right && !left) {
            vx += VX_ACEL * dt;
        } else {
            // aplicar fricción
            if (vx > 0) {
                vx -= VX_FRICTION * dt;
                if (vx < 0) vx = 0;
            } else if (vx < 0) {
                vx += VX_FRICTION * dt;
                if (vx > 0) vx = 0;
            }
        }

        // Limitar velocidad horizontal
        if (vx > VX_MAX) vx = VX_MAX;
        if (vx < -VX_MAX) vx = -VX_MAX;

        // Gravedad y salto (flanco de subida de SPACE)
        if (enSuelo && space && !prevSpace) {
            vy = -VY_SALTO; // hacia arriba negativo
            enSuelo = false;
        }
        prevSpace = space;

        // Aplicar gravedad si no está en el suelo
        if (!enSuelo) {
            vy += GRAVEDAD * dt; // incrementa hacia abajo (positivo)
            if (vy > VY_MAX_CAIDA) vy = VY_MAX_CAIDA;
        }

        // Integración posición
        x += vx * dt;
        y += vy * dt;

        // Colisiones con suelo (límite inferior de la ventana menos altura del jugador)
        double sueloY = Main.ALTO - SIZE * 1.5d; // y donde debe apoyarse
        if (y >= sueloY) {
            y = sueloY;
            vy = 0;
            enSuelo = true;
        }

        // Evitar salir por arriba
        if (y < 0) {
            y = 0;
            vy = 0; // si golpea techo
        }

        // Limitar dentro de ancho ventana
        if (x < 0) x = 0;
        if (x > Main.ANCHO - SIZE) x = Main.ANCHO - SIZE;
    }

}
