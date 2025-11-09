package programa;

import javax.swing.JComponent;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import componentes.Input;
import componentes.GeneradorMundo;
import juego.Jugador;
import juego.bloques.BasicBlock;

public class Panel extends JComponent {
    private static final int FPS = 60;

    // Duración de cada frame objetivo en nanosegundos (≈16_666_666 ns para 60 FPS)
    private static final long FRAME_TIME_NANOS = 1_000_000_000L / FPS;

    private static final int TAM_BLOQUE = 64;
    private int ancho;
    private int alto;
    private Thread gameThread;
    private volatile boolean enJuego = true; // volatile para visibilidad entre hilos

    private Graphics2D graphics;
    private BufferedImage image;
    private Input input;
    private Jugador jugador;
    private BasicBlock[][] mundo; // rejilla 2D
    private final List<BasicBlock> bloquesVisibles = new ArrayList<>();

    // Cámara (ahora horizontal y vertical)
    private double cameraX = 0;
    private double cameraY = 0;

    // Ratios zona muerta horizontal (tercios)
    private static final double DEADZONE_LEFT_RATIO = 1.0 / 3.0;
    private static final double DEADZONE_RIGHT_RATIO = 2.0 / 3.0;
    // Ratios zona muerta vertical (tercios)
    private static final double DEADZONE_TOP_RATIO = 1.0 / 3.0;
    private static final double DEADZONE_BOTTOM_RATIO = 2.0 / 3.0;

    // Suavizado de cámara (valor alto = más rápido). Unidades ~ 1/seg
    private static final double CAMERA_SMOOTH_SPEED = 10.0;

    // HUD datos
    private long fpsWindowFrames = 0;
    private long fpsWindowNanos = 0;
    private static final long HUD_FPS_UPDATE_NS = 250_000_000L; // 250 ms
    private int hudFPS = 0; // valor mostrado
    private double hudFrameTimeMs = 0.0; // último frame en ms (total)

    public void start() {

        ancho = getWidth();
        alto = getHeight();

        if (ancho <= 0 || alto <= 0) { // fallback por si aún no tiene tamaño
            ancho = Main.ANCHO;
            alto = Main.ALTO;
        }

        // Crear imagen en memoria para renderizado
        image = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        graphics = image.createGraphics();

        // Configurar opciones de renderizado
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        initGame();
        initInput();

        // Iniciar el hilo del juego
        gameThread = new Thread(() -> {
            long frame = 0; // Contador de frames para depuración
            final double dt = 1.0 / FPS; // paso fijo en segundos

            while(enJuego) {
                long tiempoInicioFrame = System.nanoTime();

                // Construir lista de bloques cercanos al jugador para colisiones
                List<BasicBlock> bloquesCercanos = obtenerBloquesCercanosJugador(2);

                // Actualización de juego
                jugador.update(input, dt, bloquesCercanos);

                // Actualizar cámara tras mover al jugador (con dt para interpolación)
                actualizarCamara(dt);

                // --- Renderizado ---
                drawBackground();
                drawGame();
                drawHUD();
                render();

                // Tiempo de trabajo (sin sleep), para respetar el presupuesto
                long tiempoTrabajoNs = System.nanoTime() - tiempoInicioFrame;

                long tiempoRestante = FRAME_TIME_NANOS - tiempoTrabajoNs;
                if( tiempoRestante > 0 ) {
                    long sleepMs = tiempoRestante / 1_000_000L; // parte en ms
                    int sleepNs = (int) (tiempoRestante % 1_000_000L); // resto en ns
                    sleep(sleepMs, sleepNs);
                } else {
                    // Vamos atrasados, no dormimos
                }

                // Medir frame total (incluye sleep)
                long tiempoFinFrame = System.nanoTime();
                long frameNs = tiempoFinFrame - tiempoInicioFrame;
                hudFrameTimeMs = frameNs / 1_000_000.0;

                // Ventana de FPS (≈ cada 250 ms)
                fpsWindowFrames++;
                fpsWindowNanos += frameNs;
                if (fpsWindowNanos >= HUD_FPS_UPDATE_NS) {
                    double secs = fpsWindowNanos / 1_000_000_000.0;
                    hudFPS = (int) Math.round(fpsWindowFrames / secs);
                    fpsWindowFrames = 0;
                    fpsWindowNanos = 0;
                }

                frame++;
            }
        }, "GameLoopThread");

        gameThread.start();
    }

    public void stop(){
        enJuego = false;
        if(gameThread != null && gameThread.isAlive()){
            try {
                gameThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void initGame(){
        jugador = new Jugador();
        // Colocar al jugador justo encima de la primera fila sólida (y=64 tiles comienza el bloque en píxeles y=64*64)
        jugador.colocar(new tipos.Punto(100, 64 * TAM_BLOQUE - jugador.getAltoPx()));

        mundo = GeneradorMundo.generar(1024, 128, 0, 0);
        // Centrar cámara instantáneamente (dt=0 provoca snap)
        actualizarCamara(0);
        actualizarBloquesVisibles();
    }

    private void actualizarCamara(double dt) {
        double size = BasicBlock.getSize();
        double worldHeightPx = (mundo != null ? mundo.length : 0) * size;
        double worldWidthPx = (mundo != null && mundo.length > 0 ? mundo[0].length : 0) * size;

        // Datos del jugador
        double playerX = jugador.getX();
        double playerY = jugador.getY();
        double playerSize = jugador.getAltoPx(); // sprite cuadrado
        double playerLeft = playerX;
        double playerRight = playerX + playerSize;
        double playerTop = playerY;
        double playerBottom = playerY + playerSize;

        // Umbrales actuales en coords de mundo (según cámara actual)
        double leftThreshold = cameraX + ancho * DEADZONE_LEFT_RATIO;
        double rightThreshold = cameraX + ancho * DEADZONE_RIGHT_RATIO;
        double topThreshold = cameraY + alto * DEADZONE_TOP_RATIO;
        double bottomThreshold = cameraY + alto * DEADZONE_BOTTOM_RATIO;

        // Objetivo deseado (no se aplica directo; se interpola salvo dt<=0)
        double desiredX = cameraX;
        double desiredY = cameraY;

        // Ajuste horizontal por zona muerta
        if (playerLeft < leftThreshold) {
            desiredX = playerLeft - ancho * DEADZONE_LEFT_RATIO;
        } else if (playerRight > rightThreshold) {
            desiredX = playerRight - ancho * DEADZONE_RIGHT_RATIO;
        }

        // Ajuste vertical por zona muerta
        if (playerTop < topThreshold) {
            desiredY = playerTop - alto * DEADZONE_TOP_RATIO;
        } else if (playerBottom > bottomThreshold) {
            desiredY = playerBottom - alto * DEADZONE_BOTTOM_RATIO;
        }

        // Clamp objetivos
        if (desiredX < 0) desiredX = 0;
        double maxCamX = Math.max(0, worldWidthPx - ancho);
        if (desiredX > maxCamX) desiredX = maxCamX;

        if (desiredY < 0) desiredY = 0;
        double maxCamY = Math.max(0, worldHeightPx - alto);
        if (desiredY > maxCamY) desiredY = maxCamY;

        // Snap instantáneo si dt <= 0 (inicialización)
        if (dt <= 0) {
            cameraX = desiredX;
            cameraY = desiredY;
            return;
        }

        // Interpolación dependiente de tiempo
        double alpha = 1.0 - Math.exp(-CAMERA_SMOOTH_SPEED * dt);
        cameraX = cameraX + (desiredX - cameraX) * alpha;
        cameraY = cameraY + (desiredY - cameraY) * alpha;

        if (Math.abs(desiredX - cameraX) < 0.1) cameraX = desiredX;
        if (Math.abs(desiredY - cameraY) < 0.1) cameraY = desiredY;
    }

    private List<BasicBlock> obtenerBloquesCercanosJugador(int margenTiles) {
        List<BasicBlock> lista = new ArrayList<>();
        if (mundo == null) return lista;
        Rectangle2D pb = jugador.getBounds();
        double size = BasicBlock.getSize();
        int minTileX = Math.max(0, (int)Math.floor(pb.getX() / size) - margenTiles);
        int maxTileX = (int)Math.floor((pb.getX() + pb.getWidth()) / size) + margenTiles;
        int minTileY = Math.max(0, (int)Math.floor(pb.getY() / size) - margenTiles);
        int maxTileY = (int)Math.floor((pb.getY() + pb.getHeight()) / size) + margenTiles;

        if (mundo.length == 0) return lista;
        int maxY = mundo.length - 1;
        int maxX = mundo[0].length - 1;
        if (maxTileX > maxX) maxTileX = maxX;
        if (maxTileY > maxY) maxTileY = maxY;

        for (int y = minTileY; y <= maxTileY; y++) {
            BasicBlock[] fila = mundo[y];
            for (int x = minTileX; x <= maxTileX; x++) {
                BasicBlock b = fila[x];
                if (b != null) lista.add(b);
            }
        }
        return lista;
    }

    private void actualizarBloquesVisibles(){
        bloquesVisibles.clear();
        if (mundo == null) return;
        double size = BasicBlock.getSize();
        int colsVisible = (int) Math.ceil(ancho / size) + 2; // margen
        int rowsVisible = (int) Math.ceil(alto / size) + 2;
        int startX = (int)Math.floor(cameraX / size);
        int startY = (int)Math.floor(cameraY / size);
        for (int y = startY; y < Math.min(mundo.length, startY + rowsVisible); y++) {
            BasicBlock[] fila = mundo[y];
            for (int x = startX; x < Math.min(fila.length, startX + colsVisible); x++) {
                BasicBlock b = fila[x];
                if (b != null) bloquesVisibles.add(b);
            }
        }
    }

    private void drawBackground(){
        graphics.setColor(new Color(0, 191, 255));
        graphics.fillRect(0, 0, ancho, alto);
    }

    private void drawGame(){
        actualizarBloquesVisibles();
        AffineTransform at = graphics.getTransform();
        graphics.translate(-cameraX, -cameraY);
        for (BasicBlock b : bloquesVisibles) {
            b.draw(graphics);
        }
        jugador.draw(graphics);
        graphics.setTransform(at);
    }

    private void drawHUD(){
        // Dibujar texto encima (sin aplicar cámara)
        Graphics2D g2 = graphics;
        AffineTransform at = g2.getTransform();
        g2.setTransform(new AffineTransform());
        g2.setFont(new Font("Consolas", Font.PLAIN, 14));
        g2.setColor(new Color(0,0,0,140));
        g2.fillRoundRect(8, 8, 140, 48, 8, 8);
        g2.setColor(Color.WHITE);
        String fpsTxt = "FPS: " + hudFPS;
        String ftTxt = String.format("Frame: %.2f ms", hudFrameTimeMs);
        g2.drawString(fpsTxt, 16, 28);
        g2.drawString(ftTxt, 16, 46);
        g2.setTransform(at);
    }

    private void render(){
        Graphics g = this.getGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
    }

    private void initInput(){
        input = new Input();
        setFocusable(true);
        requestFocusInWindow();
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                final boolean estado = true;

                if(e.getKeyCode() == KeyEvent.VK_W){
                    input.setKeyW(estado);
                }
                if(e.getKeyCode() == KeyEvent.VK_A){
                    input.setKeyA(estado);
                }
                if(e.getKeyCode() == KeyEvent.VK_D){
                    input.setKeyD(estado);
                }
                if(e.getKeyCode() == KeyEvent.VK_S){
                    input.setKeyS(estado);
                }
                if(e.getKeyCode() == KeyEvent.VK_SPACE){
                    input.pressSpace();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                final boolean estado = false;
                if(e.getKeyCode() == KeyEvent.VK_SPACE){
                    input.releaseSpace();
                }
                if(e.getKeyCode() == KeyEvent.VK_W){
                    input.setKeyW(estado);
                }
                if(e.getKeyCode() == KeyEvent.VK_A){
                    input.setKeyA(estado);
                }
                if(e.getKeyCode() == KeyEvent.VK_D){
                    input.setKeyD(estado);
                }
                if(e.getKeyCode() == KeyEvent.VK_S){
                    input.setKeyS(estado);
                }
            }
        });
    }

    private void sleep(long millis, int nanos){
        try {
            Thread.sleep(millis, nanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }
    }

    private void sleep(long millis){
        this.sleep(millis, 0);
    }

}
