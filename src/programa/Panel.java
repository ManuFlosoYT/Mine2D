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
import componentes.Camara;
import componentes.HudDebug;
import juego.Jugador;
import juego.bloques.BasicBlock;

public class Panel extends JComponent {
    private static final int FPS = 60;
    private static final long FRAME_TIME_NANOS = 1_000_000_000L / FPS;
    private static final int TAM_BLOQUE = 64;
    private int ancho;
    private int alto;
    private Thread gameThread;
    private volatile boolean enJuego = true;

    private Graphics2D graphics;
    private BufferedImage image;
    private Input input;
    private Jugador jugador;
    private BasicBlock[][] mundo;
    private final List<BasicBlock> bloquesVisibles = new ArrayList<>();
    private Camara camara;
    private HudDebug hud;

    public void start() {
        ancho = getWidth();
        alto = getHeight();
        if (ancho <= 0 || alto <= 0) {
            ancho = Main.ANCHO;
            alto = Main.ALTO;
        }
        image = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        initGame();
        initInput();
        gameThread = new Thread(() -> {
            final double dt = 1.0 / FPS;
            while(enJuego) {
                long tiempoInicioFrame = System.nanoTime();
                List<BasicBlock> bloquesCercanos = obtenerBloquesCercanosJugador();
                jugador.update(input, dt, bloquesCercanos);
                camara.update(jugador, mundo, dt);
                drawBackground();
                drawGame();
                hud.draw(graphics);
                render();
                long tiempoTrabajoNs = System.nanoTime() - tiempoInicioFrame;
                long tiempoRestante = FRAME_TIME_NANOS - tiempoTrabajoNs;
                if( tiempoRestante > 0 ) {
                    long sleepMs = tiempoRestante / 1_000_000L;
                    int sleepNs = (int) (tiempoRestante % 1_000_000L);
                    sleep(sleepMs, sleepNs);
                }
                long tiempoFinFrame = System.nanoTime();
                long frameNs = tiempoFinFrame - tiempoInicioFrame;
                hud.updateFrame(frameNs);
            }
        }, "GameLoopThread");
        gameThread.start();
    }

    public void stop(){
        enJuego = false;
        if(gameThread != null && gameThread.isAlive()){
            try { gameThread.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    private void initGame(){
        jugador = new Jugador();
        jugador.colocar(new tipos.Punto(100, 64 * TAM_BLOQUE - jugador.getAltoPx()));
        mundo = GeneradorMundo.generar(1024, 128, 0, 0);
        camara = new Camara(ancho, alto);
        camara.update(jugador, mundo, 0);
        hud = new HudDebug();
        actualizarBloquesVisibles();
    }

    private List<BasicBlock> obtenerBloquesCercanosJugador() {
        final int margenTiles = 2; // margen fijo de búsqueda
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
        int colsVisible = (int) Math.ceil(ancho / size) + 2;
        int rowsVisible = (int) Math.ceil(alto / size) + 2;
        int startX = (int)Math.floor(camara.getX() / size);
        int startY = (int)Math.floor(camara.getY() / size);
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
        graphics.translate(-camara.getX(), -camara.getY());
        for (BasicBlock b : bloquesVisibles) { b.draw(graphics); }
        jugador.draw(graphics);
        graphics.setTransform(at);
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
                if(e.getKeyCode() == KeyEvent.VK_W) input.setKeyW(estado);
                if(e.getKeyCode() == KeyEvent.VK_A) input.setKeyA(estado);
                if(e.getKeyCode() == KeyEvent.VK_D) input.setKeyD(estado);
                if(e.getKeyCode() == KeyEvent.VK_S) input.setKeyS(estado);
                if(e.getKeyCode() == KeyEvent.VK_SPACE) input.pressSpace();
            }
            @Override
            public void keyReleased(KeyEvent e) {
                final boolean estado = false;
                if(e.getKeyCode() == KeyEvent.VK_SPACE) input.releaseSpace();
                if(e.getKeyCode() == KeyEvent.VK_W) input.setKeyW(estado);
                if(e.getKeyCode() == KeyEvent.VK_A) input.setKeyA(estado);
                if(e.getKeyCode() == KeyEvent.VK_D) input.setKeyD(estado);
                if(e.getKeyCode() == KeyEvent.VK_S) input.setKeyS(estado);
            }
        });
    }

    private void sleep(long millis, int nanos){
        try { Thread.sleep(millis, nanos); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
