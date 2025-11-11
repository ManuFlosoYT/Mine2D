package programa;

import javax.swing.JComponent;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import componentes.*; // importar componentes refactorizados
import juego.Jugador;
import juego.bloques.BasicBlock;

/**
 * Superficie principal de renderizado del juego.
 *
 * <p>Responsabilidades:
 * - Inicializar mundo, cámara, HUD, renderer y editor.
 * - Gestionar el buffer offscreen y presentar la imagen en pantalla.
 * - Delegar el bucle de juego a {@link GameLoop} y el input a {@link InputController}.</p>
 */
public class Panel extends JComponent {
    private static final int TAM_BLOQUE = 64;
    private int ancho;
    private int alto;
    private Thread gameThread;

    private Graphics2D graphics; // offscreen
    private BufferedImage image;
    private Input input;
    private InputController inputController;
    private Jugador jugador;
    private volatile BasicBlock[][] mundo;
    private final List<BasicBlock> bloquesVisibles = new ArrayList<>();
    private Camara camara;
    private HudDebug hud;
    private EditorMundo editorMundo; // editor
    private Renderer renderer;
    private GameLoop loop;

    private static final int ANCHO_MUNDO = 1024;
    private static final int ALTO_MUNDO = 128;
    private static final File DEBUG_WORLD_FILE = new File("world_debug.wgz");

    /** Inicia el juego y sus subsistemas. */
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
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        initGame();
        input = new Input();
        inputController = new InputController(this, input);
        inputController.setDebugHotkeysListener(new InputController.DebugHotkeysListener() {
            @Override
            public void onSaveRequested() { debugSaveWorld(); }
            @Override
            public void onLoadRequested() { debugLoadWorld(); }
        });
        inputController.install();
        if (editorMundo != null) editorMundo.start();
        loop = new GameLoop(this, jugador, camara, hud, editorMundo, renderer, bloquesVisibles, input);
        gameThread = new Thread(loop, "GameLoopThread");
        gameThread.start();
    }

    /** Detiene el juego y libera recursos. */
    public void stop(){
        if (loop != null) loop.detener();
        if(gameThread != null && gameThread.isAlive()){
            try { gameThread.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        if (editorMundo != null) editorMundo.stop();
        if (inputController != null) inputController.uninstall();
    }

    private void initGame(){
        jugador = new Jugador();
        double startX = (double)(TAM_BLOQUE * ANCHO_MUNDO) / 2;
        jugador.colocar(new tipos.Punto(startX, 64 * TAM_BLOQUE - jugador.getAltoPx()));
        mundo = GeneradorMundo.generar(ANCHO_MUNDO, ALTO_MUNDO, 0, 0);
        camara = new Camara(ancho, alto);
        camara.update(jugador, mundo, 0);
        hud = new HudDebug();
        MundoHelper.actualizarBloquesVisibles(bloquesVisibles, mundo, camara, ancho, alto);
        if (editorMundo == null) {
            editorMundo = new EditorMundo(mundo, camara, this, jugador);
        } else {
            editorMundo.setMundo(mundo);
        }
        if (renderer == null) renderer = new Renderer();
    }

    /** Guardar estado del mundo a archivo debug. */
    private void debugSaveWorld() {
        try {
            WorldIO.saveCompressed(DEBUG_WORLD_FILE, mundo, new tipos.Punto(jugador.getX(), jugador.getY()));
            System.out.println("[DEBUG] Mundo+jugador (comprimido) guardado en " + DEBUG_WORLD_FILE.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("[DEBUG] Error guardando el mundo: " + e.getMessage());
        }
    }

    /** Cargar estado del mundo desde archivo debug. */
    private void debugLoadWorld() {
        try {
            if (!DEBUG_WORLD_FILE.exists()) {
                System.out.println("[DEBUG] Archivo de mundo no existe: " + DEBUG_WORLD_FILE.getAbsolutePath());
                return;
            }
            WorldData data = WorldIO.loadCompressedWithPlayer(DEBUG_WORLD_FILE);
            BasicBlock[][] cargado = data.mundo();
            if (cargado.length == 0) {
                System.out.println("[DEBUG] Mundo cargado vacío.");
                return;
            }
            mundo = cargado;
            if (editorMundo != null) editorMundo.setMundo(mundo);
            if (data.jugadorPos() != null) jugador.colocar(data.jugadorPos());
            camara.update(jugador, mundo, 0);
            System.out.println("[DEBUG] Mundo+jugador (comprimidos) cargados: " + mundo[0].length + "x" + mundo.length);
        } catch (IOException e) {
            System.err.println("[DEBUG] Error cargando el mundo: " + e.getMessage());
        }
    }

    /** Presenta el buffer offscreen en pantalla. */
    public void present(){
        Graphics g = this.getGraphics();
        if (g != null) {
            g.drawImage(image, 0, 0, null);
            g.dispose();
        }
    }

    // Getters
    public int getAncho(){ return ancho; }
    public int getAlto(){ return alto; }
    public Graphics2D getOffscreenGraphics(){ return graphics; }
    public Input getInput(){ return input; }
    public BasicBlock[][] getMundo(){ return mundo; }
    public void setMundo(BasicBlock[][] nuevo){ this.mundo = nuevo; }
}
