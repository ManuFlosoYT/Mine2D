package juego;

import componentes.Input;
import tipos.Punto;
import juego.bloques.BasicBlock;
import juego.bloques.BlockType; // nuevo: para distinguir agua en colisiones/física

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.net.URL;
import java.util.List;

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

    private double x;
    private double y;

    private double vx;
    private double vy;

    // Parámetros de física (aire/suelo)
    private static final double VX_ACEL = 1200;      // px/s^2 aceleración horizontal
    private static final double VX_FRICTION = 1100;  // px/s^2 fricción cuando no se pulsa nada
    private static final double VX_MAX = 300;        // px/s velocidad horizontal máxima

    private static final double NOCLIP_SPEED = 1200; // px/s velocidad en modo noclip

    // En este sistema consideramos que Y física crece hacia ARRIBA, por lo que
    // una gravedad positiva reduce Y (empuja hacia abajo) y un salto aumenta Y.
    private static final double GRAVEDAD = 1800;      // px/s^2 gravedad (reduce Y)
    private static final double VY_SALTO = 550;       // px/s velocidad inicial de salto (aumenta Y)
    private static final double VY_MAX_CAIDA = 1200;  // px/s límite máximo de caída (magnitud)

    // Parámetros de física en agua
    private static final double WATER_GRAVITY = 400;        // gravedad reducida en agua
    private static final double WATER_UP_ACCEL = 900;       // aceleración hacia arriba al mantener salto
    private static final double WATER_DOWN_ACCEL = 300;     // aceleración de hundimiento
    private static final double SWIM_UP_SPEED = 150;        // velocidad máx. de ascenso
    private static final double SWIM_DOWN_SPEED = 150;      // velocidad máx. de descenso
    // Movimiento horizontal en agua (más lento)
    private static final double WATER_VX_ACEL = 450;        // px/s^2 aceleración horizontal reducida
    private static final double WATER_VX_FRICTION = 1300;   // px/s^2 fricción algo mayor por drag
    private static final double WATER_VX_MAX = 140;         // px/s velocidad horizontal máxima reducida


    // Persistencia de física de agua para suavizar cruces de superficie
    private static final double WATER_STICKY_TIME = 0.15;   // segundos de ventana tras salir del agua
    private static final double WATER_DETECT_TOL = Math.max(4, BLOCK_SIZE * 0.12); // px de tolerancia hacia arriba
    private double aguaStickyTimer = 0.0;

    // Tolerancia para considerar que está apoyado sobre un bloque sólido
    private static final double GROUND_EPS = 3.0; // px

    // Jump buffering & coyote time
    private static final double JUMP_BUFFER_TIME = 0.20; // segundos
    private static final double COYOTE_TIME = 0.10;      // segundos
    private double jumpBufferTimer = 0.0;
    private double coyoteTimer = 0.0;

    private boolean enSuelo;

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

    // Helper: determinar si hay soporte sólido justo bajo los pies del jugador
    private boolean isSupportedBySolid(List<BasicBlock> bloques, double px, double py) {
        double feet = py + HEIGHT;
        for (BasicBlock b : bloques) {
            if (b.getType() == BlockType.WATER) continue;
            Rectangle2D br = b.getBounds();
            // Solape horizontal
            boolean overlapX = (px + WIDTH) > br.getX() + 0.001 && px < (br.getX() + br.getWidth()) - 0.001;
            if (!overlapX) continue;
            double top = br.getY();
            // Si los pies están a la altura del top del bloque (con tolerancia)
            if (feet >= top - GROUND_EPS && feet <= top + GROUND_EPS) {
                return true;
            }
        }
        return false;
    }

    // Helper: obtener la Y superior exacta del bloque que soporta al jugador (si está dentro de la tolerancia)
    private Double getSupportTopY(List<BasicBlock> bloques, double px, double py) {
        double feet = py + HEIGHT;
        Double bestTop = null;
        double bestAbs = GROUND_EPS + 1;
        for (BasicBlock b : bloques) {
            if (b.getType() == BlockType.WATER) continue;
            Rectangle2D br = b.getBounds();
            boolean overlapX = (px + WIDTH) > br.getX() + 0.001 && px < (br.getX() + br.getWidth()) - 0.001;
            if (!overlapX) continue;
            double top = br.getY();
            double diff = Math.abs(feet - top);
            if (diff <= GROUND_EPS && diff < bestAbs) {
                bestAbs = diff;
                bestTop = top;
            }
        }
        return bestTop;
    }

    /**
     * Actualiza el estado del jugador: procesa input, integra física y resuelve colisiones.
     * @param input estado de entrada (teclas)
     * @param dt delta de tiempo en segundos
     * @param bloques lista de bloques cercanos para pruebas de colisión
     */
    public void update(Input input, double dt, List<BasicBlock> bloques){
        // Noclip activo?
        boolean noclip = input.isNoclipActive();
        // --- INPUT: izquierda/derecha ---
        boolean left = input.isKeyA();
        boolean right = input.isKeyD();

        if (noclip) {
            // Movimiento horizontal libre sin fricción/gravedad
            if (left && !right) vx = -NOCLIP_SPEED; else if (right && !left) vx = NOCLIP_SPEED; else vx = 0;
            // Movimiento vertical libre controlado por SPACE/SHIFT
            if (input.isKeySpace() && !input.isKeyShift()) {
                vy = -NOCLIP_SPEED; // subir (Y de pantalla disminuye)
            } else if (input.isKeyShift() && !input.isKeySpace()) {
                vy = NOCLIP_SPEED; // bajar (Y de pantalla aumenta)
            } else {
                vy = 0;
            }
            // Integración directa sin colisiones
            x += vx * dt;
            y += vy * dt;
            return; // saltar resto de física
        }
        // Detectar si está dentro del agua (intersección con cualquier bloque de agua)
        boolean enAgua = false;
        Rectangle2D pbActual = getBounds();
        // Considerar solo la parte inferior del jugador para la detección de agua (evitar falsos positivos por hombros)
        Rectangle2D piesJugador = new Rectangle2D.Double(pbActual.getX(), pbActual.getY() + pbActual.getHeight() * 0.4,
                pbActual.getWidth(), pbActual.getHeight() * 0.6);
        for (BasicBlock b : bloques) {
            if (b.getType() == BlockType.WATER) {
                Rectangle2D wb = b.getBounds();
                // Expandir la caja de agua hacia arriba para compensar el recorte de hitbox y evitar gaps
                Rectangle2D wbDetect = new Rectangle2D.Double(wb.getX(), wb.getY() - WATER_DETECT_TOL, wb.getWidth(), wb.getHeight() + WATER_DETECT_TOL);
                if (piesJugador.intersects(wbDetect)) {
                    enAgua = true;
                    break;
                }
            }
        }
        // ¿Estamos apoyados sobre bloque sólido en la posición actual?
        boolean soportadoPre = isSupportedBySolid(bloques, x, y);

        // Actualizar sticky si tocamos agua (pero si estamos apoyados, no alargar el sticky innecesariamente)
        if (enAgua && !soportadoPre) {
            aguaStickyTimer = WATER_STICKY_TIME;
        } else if (aguaStickyTimer > 0) {
            aguaStickyTimer -= dt;
            if (aguaStickyTimer < 0) aguaStickyTimer = 0;
        }

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
        boolean usarAguaHorizontal = (enAgua || aguaStickyTimer > 0) && !soportadoPre;
        double acel = usarAguaHorizontal ? WATER_VX_ACEL : VX_ACEL;
        double fric = usarAguaHorizontal ? WATER_VX_FRICTION : VX_FRICTION;
        double vmax = usarAguaHorizontal ? WATER_VX_MAX : VX_MAX;
        if (left && !right) {
            vx -= acel * dt;
        } else if (right && !left) {
            vx += acel * dt;
        } else {
            if (vx > 0) {
                vx -= fric * dt;
                if (vx < 0) vx = 0;
            } else if (vx < 0) {
                vx += fric * dt;
                if (vx > 0) vx = 0;
            }
        }

        if (vx > vmax) vx = vmax;
        if (vx < -vmax) vx = -vmax;

        // --- Decidir salto ANTES de integrar eje Y ---
        if (!enAgua || soportadoPre) {
            if (jumpBufferTimer > 0 && (enSuelo || coyoteTimer > 0 || soportadoPre)) {
                vy = -VY_SALTO; // salto: velocidad vertical negativa (hacia arriba en pantalla)
                enSuelo = false;
                jumpBufferTimer = 0; // consumir buffer al usarlo
                coyoteTimer = 0;
            }
        } else {
            // En agua no aplicamos salto balístico; el SPACE se usará como nado hacia arriba
            jumpBufferTimer = 0; // limpiar para no acumular
        }

        // --- Gravedad / flotabilidad ---
        if (enAgua && !soportadoPre) {
            // Hundimiento suave por defecto
            if (input.isKeySpace()) {
                // Nadar hacia arriba (disminuir Y de pantalla)
                vy -= WATER_UP_ACCEL * dt;
                if (vy < -SWIM_UP_SPEED) vy = -SWIM_UP_SPEED;
            } else {
                // Tender hacia hundirse lentamente (aumentar Y de pantalla)
                vy += Math.max(WATER_GRAVITY, WATER_DOWN_ACCEL) * dt;
                if (vy > SWIM_DOWN_SPEED) vy = SWIM_DOWN_SPEED;
            }
        } else {
            if (!enSuelo) {
                // Gravedad: aumentar Y de pantalla (vy positiva al caer)
                vy += GRAVEDAD * dt;
                if (vy > VY_MAX_CAIDA) vy = VY_MAX_CAIDA;
            }
        }

        // --- Integración y colisión eje X ---
        double nuevoX = x + vx * dt;
        Rectangle2D futuroX = new Rectangle2D.Double(nuevoX, y, WIDTH, HEIGHT);
        for (BasicBlock b : bloques) {
            if (b.getType() == BlockType.WATER) continue; // agua no colisiona
            if (futuroX.intersects(b.getBounds())) {
                Rectangle2D br = b.getBounds();
                if (vx > 0) {
                    nuevoX = br.getX() - WIDTH; // a la izquierda del bloque
                } else if (vx < 0) {
                    nuevoX = br.getX() + br.getWidth(); // a la derecha del bloque
                }
                vx = 0;
                break; // ya resolvimos una colisión horizontal
            }
        }
        x = nuevoX;

        // --- Integración y colisión eje Y ---
        double nuevoY = y + vy * dt; // Y pantalla aumenta cuando vy es positiva (caída)
        Rectangle2D futuroY = new Rectangle2D.Double(x, nuevoY, WIDTH, HEIGHT);
        enSuelo = false;
        for (BasicBlock b : bloques) {
            if (b.getType() == BlockType.WATER) continue; // agua no colisiona
            if (futuroY.intersects(b.getBounds())) {
                Rectangle2D br = b.getBounds();
                if (vy < 0) { // subiendo (vy negativa, sube en pantalla)
                    nuevoY = br.getY() + br.getHeight(); // por debajo (techo)
                    vy = 0;
                } else if (vy > 0) { // cayendo (vy positiva, baja en pantalla)
                    nuevoY = br.getY() - HEIGHT; // colocar encima del bloque
                    vy = 0;
                    enSuelo = true;
                }
                break; // una colisión vertical es suficiente
            }
        }
        y = nuevoY;

        // Recalcular soporte tras integrar para estados estacionarios
        boolean soportadoPost = isSupportedBySolid(bloques, x, y);
        if (soportadoPost) {
            Double topY = getSupportTopY(bloques, x, y);
            if (topY != null) {
                // Alinear los pies exactamente con el top del bloque para evitar levitación
                y = topY - HEIGHT;
            }
            enSuelo = true;
            vy = 0;
            aguaStickyTimer = 0; // al estar apoyado, no queremos drag de agua residual
        }

    }

}
