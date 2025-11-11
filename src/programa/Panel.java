package programa;

import javax.swing.JComponent;
import java.awt.*;
import java.awt.image.BufferedImage;
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
    private BasicBlock[][] mundo;
    private final List<BasicBlock> bloquesVisibles = new ArrayList<>();
    private Camara camara;
    private HudDebug hud;
    private EditorMundo editorMundo; // editor
    private Renderer renderer;
    private GameLoop loop;

    private static final int ANCHO_MUNDO = 1024;
    private static final int ALTO_MUNDO = 128;

    /**
     * Inicia el juego: crea buffers, inicializa subsistemas, instala input y lanza el loop.
     */
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
        // Input control centralizado
        input = new Input();
        inputController = new InputController(this, input);
        inputController.install();
        if (editorMundo != null) editorMundo.start();
        loop = new GameLoop(this, jugador, mundo, camara, hud, editorMundo, renderer, bloquesVisibles, input);
        gameThread = new Thread(loop, "GameLoopThread");
        gameThread.start();
    }

    /**
     * Detiene el juego, desinstala listeners y cierra hilos auxiliares de forma ordenada.
     */
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

    /**
     * Presenta el buffer offscreen en pantalla.
     */
    public void present(){
        Graphics g = this.getGraphics();
        if (g != null) {
            g.drawImage(image, 0, 0, null);
            g.dispose();
        }
    }

    // Getters necesarios para GameLoop
    /**
     * Ancho actual del panel en píxeles.
     */
    public int getAncho(){ return ancho; }
    /**
     * Alto actual del panel en píxeles.
     */
    public int getAlto(){ return alto; }
    /**
     * Contexto gráfico del buffer offscreen. No debe almacenarse fuera del ciclo de render.
     */
    public Graphics2D getOffscreenGraphics(){ return graphics; }
    /**
     * Acceso al estado de entrada actual.
     */
    public Input getInput(){ return input; }
}
