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

    private final Object renderLock = new Object();
    private Graphics2D graphics; // offscreen
    private volatile BufferedImage image; // buffer mostrado por Swing
    private Input input;
    private InputController inputController;
    private Jugador jugador;
    private volatile BasicBlock[][] mundo;
    private volatile Lighting.LightGrid lightGrid;
    private final List<BasicBlock> bloquesVisibles = new ArrayList<>();
    private Camara camara;
    private HudDebug hud;
    private EditorMundo editorMundo; // editor
    private Renderer renderer;
    private GameLoop loop;

    // Tamaño del mundo configurable
    private static final int DEFAULT_ANCHO_MUNDO = 1024;
    private static final int DEFAULT_ALTO_MUNDO = 128;
    private int worldWidth = DEFAULT_ANCHO_MUNDO;
    private int worldHeight = DEFAULT_ALTO_MUNDO;

    private static final File DEBUG_WORLD_FILE = new File("world.wgz");

    private final GameState gameState = new GameState();

    private PauseMenuPanel pauseMenu;
    public interface Listener { void onExitToMenuRequested(); }
    private final Listener listener;

    private volatile boolean vsyncEnabled = true; // limitar FPS (VSync simulado)
    private volatile double renderScale = 1.0; // factor de escala de mundo -> pantalla
    private volatile boolean debugLight = false; // mostrar números de luz en bloques

    /** Permite configurar el tamaño del mundo a generar al iniciar nueva partida. Llamar antes de start(). */
    public void setWorldSize(int width, int height) {
        // Forzar mínimos requeridos
        int w = width > 0 ? width : 0;
        int h = height > 0 ? height : 0;
        if (w < 256) w = 256;
        if (h < 128) h = 128;
        this.worldWidth = w;
        this.worldHeight = h;
    }

    public double getRenderScale() { return renderScale; }

    /** Inicia el juego y sus subsistemas. */
    public void start() {
        ancho = getWidth();
        alto = getHeight();
        if (ancho <= 0 || alto <= 0) {
            ancho = Main.ANCHO;
            alto = Main.ALTO;
        }
        // Escala basada en altura para mantener el tamaño aparente del bloque
        renderScale = (double) alto / Main.ALTO;
        image = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        initGame();
        // Recomputar iluminación inicial
        recomputeLighting();
        setupPauseMenu();
        setupKeyBindings();
        input = new Input();
        inputController = new InputController(this, input);
        inputController.install();
        if (editorMundo != null) editorMundo.start();
        loop = new GameLoop(this, jugador, camara, hud, editorMundo, renderer, bloquesVisibles, input, gameState::isPaused, gameState::awaitIfPaused, () -> vsyncEnabled);
        gameThread = new Thread(loop, "GameLoopThread");
        gameThread.start();
    }

    /** Detiene el juego y libera recursos. */
    public void stop(){
        // Asegurar que no estamos en pausa para no bloquear hilos al detener
        resumeGame();
        if (loop != null) loop.detener();
        if(gameThread != null && gameThread.isAlive()){
            try { gameThread.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        if (editorMundo != null) editorMundo.stop();
        if (inputController != null) inputController.uninstall();
    }

    private void initGame(){
        jugador = new Jugador();
        double startX = (double)(TAM_BLOQUE * worldWidth) / 2;
        // Generar mundo antes de colocar al jugador para poder buscar el suelo
        mundo = GeneradorMundo.generar(worldWidth, worldHeight, 0, 0);
        // Colocar al jugador justo sobre el bloque más alto (suelo) bajo startX
        double spawnY = computeGroundSpawnY(mundo, startX) - jugador.getAltoPx();
        if (Double.isNaN(spawnY)) spawnY = 0; // fallback defensivo
        jugador.colocar(new tipos.Punto(startX, spawnY));
        // La cámara debe usar el viewport en píxeles de mundo (pantalla dividida por escala)
        int vpWWorldPx = (int)Math.round(ancho / renderScale);
        int vpHWorldPx = (int)Math.round(alto / renderScale);
        camara = new Camara(vpWWorldPx, vpHWorldPx);
        camara.update(jugador, mundo, 0);
        hud = new HudDebug();
        // Calcular visibles con dimensiones del viewport en mundo
        MundoHelper.actualizarBloquesVisibles(bloquesVisibles, mundo, camara, vpWWorldPx, vpHWorldPx);
        if (editorMundo == null) {
            editorMundo = new EditorMundo(mundo, camara, this, jugador, gameState::isPaused, gameState::awaitIfPaused, this::getRenderScale);
        } else {
            editorMundo.setMundo(mundo);
        }
        // Notificar cambios de mundo -> recomputar iluminación
        editorMundo.setOnWorldChanged(this::recomputeLighting);
        if (renderer == null) renderer = new Renderer();
    }

    private void recomputeLighting() {
        this.lightGrid = Lighting.computeLightGrid(this.mundo);
    }

    /** Devuelve la coordenada Y (en píxeles) de la parte superior del bloque más alto en la columna de xPx.
     * Si no hay bloques en esa columna, devuelve 0. */
    private double computeGroundSpawnY(BasicBlock[][] grid, double xPx) {
        if (grid == null || grid.length == 0 || grid[0].length == 0) return 0;
        int anchoTiles = grid[0].length;
        int altoTiles = grid.length;
        double size = BasicBlock.getSize();
        int tileX = (int)Math.floor(xPx / size);
        if (tileX < 0) tileX = 0; else if (tileX >= anchoTiles) tileX = anchoTiles - 1;
        // Buscar desde arriba hacia abajo el primer bloque no nulo (el "suelo" visible)
        for (int arrayY = altoTiles - 1; arrayY >= 0; arrayY--) {
            BasicBlock b = grid[arrayY][tileX];
            if (b != null) {
                return b.getBounds().getY(); // top del bloque
            }
        }
        return 0; // columna vacía
    }

    /** Guardar estado del mundo a archivo. */
    private void saveWorld() {
        try {
            WorldIO.saveCompressed(DEBUG_WORLD_FILE, mundo, new tipos.Punto(jugador.getX(), jugador.getY()));
            System.out.println("[INFO] Mundo guardado en " + DEBUG_WORLD_FILE.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("[ERROR] Error guardando el mundo: " + e.getMessage());
        }
    }

    /** Cargar estado del mundo desde archivo. */
    private void loadWorld() {
        try {
            if (!DEBUG_WORLD_FILE.exists()) {
                System.out.println("[INFO] Archivo de mundo no existe: " + DEBUG_WORLD_FILE.getAbsolutePath());
                return;
            }
            WorldData data = WorldIO.loadCompressedWithPlayer(DEBUG_WORLD_FILE);
            BasicBlock[][] cargado = data.mundo();
            if (cargado.length == 0) {
                System.out.println("[INFO] Mundo cargado vacío.");
                return;
            }
            mundo = cargado;
            if (editorMundo != null) editorMundo.setMundo(mundo);
            if (data.jugadorPos() != null) jugador.colocar(data.jugadorPos());
            camara.update(jugador, mundo, 0);
            // Recalcular iluminación tras cargar
            recomputeLighting();
            System.out.println("[INFO] Mundo cargado: " + mundo[0].length + "x" + mundo.length);
        } catch (IOException e) {
            System.err.println("[ERROR] Error cargando el mundo: " + e.getMessage());
        }
    }

    /** Carga la partida guardada si existe (world.wgz) sin mensajes de depuración excesivos. */
    public void cargarPartidaGuardada() {
        if (!DEBUG_WORLD_FILE.exists()) return; // nada que cargar
        try {
            WorldData data = WorldIO.loadCompressedWithPlayer(DEBUG_WORLD_FILE);
            BasicBlock[][] cargado = data.mundo();
            if (cargado.length == 0) return;
            mundo = cargado;
            if (editorMundo != null) editorMundo.setMundo(mundo);
            if (data.jugadorPos() != null) jugador.colocar(data.jugadorPos());
            camara.update(jugador, mundo, 0);
            int vpWWorldPx = (int)Math.round(ancho / renderScale);
            int vpHWorldPx = (int)Math.round(alto / renderScale);
            MundoHelper.actualizarBloquesVisibles(bloquesVisibles, mundo, camara, vpWWorldPx, vpHWorldPx);
            // Recalcular iluminación
            recomputeLighting();
        } catch (IOException e) {
            System.err.println("Error cargando partida guardada: " + e.getMessage());
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        synchronized (renderLock) {
            if (image != null) {
                g.drawImage(image, 0, 0, null);
            }
        }
        // Swing pintará los hijos después
    }

    /** Presenta el buffer offscreen en pantalla. */
    public void present(){
        // Delegar en Swing; bloqueo de lectura/escritura se gestiona con renderLock
        repaint();
        try { java.awt.Toolkit.getDefaultToolkit().sync(); } catch (Throwable ignore) {}
    }

    /** Devuelve el lock de render para sincronizar escritura/lectura del buffer. */
    public Object getRenderLock() { return renderLock; }

    private void setupPauseMenu() {
        setLayout(null);
        pauseMenu = new PauseMenuPanel(new PauseMenuPanel.Listener() {
            @Override public void onResume() { resumeGame(); }
            @Override public void onSave() { saveWorld(); }
            @Override public void onExit() { exitToMenu(); }
            @Override public void onToggleVSync(boolean enabled) { setVsyncEnabled(enabled); }
        });
        pauseMenu.setVisible(false);
        add(pauseMenu);
        // Ajustar tamaño centrado
        pauseMenu.setSize(300, 300);
        centerPauseMenu();
    }
    private void centerPauseMenu() {
        if (pauseMenu != null) {
            int w = pauseMenu.getWidth(); int h = pauseMenu.getHeight();
            pauseMenu.setLocation((ancho - w)/2, (alto - h)/2);
        }
    }
    private void setupKeyBindings() {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE,0),"togglePause");
        getActionMap().put("togglePause", new javax.swing.AbstractAction() { @Override public void actionPerformed(java.awt.event.ActionEvent e) { togglePause(); }});
        // Toggle debug de luz con F5
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F5,0),"toggleLightDebug");
        getActionMap().put("toggleLightDebug", new javax.swing.AbstractAction() { @Override public void actionPerformed(java.awt.event.ActionEvent e) { debugLight = !debugLight; repaint(); }});
    }
    private void togglePause() { if (!gameState.isPaused()) pauseGame(); else resumeGame(); }
    public boolean isPaused() { return gameState.isPaused(); }
    public void pauseGame() { gameState.pause(); if(pauseMenu!=null){ pauseMenu.setVisible(true); pauseMenu.requestFocusInWindow(); } }
    public void resumeGame() { gameState.resume(); if(pauseMenu!=null){ pauseMenu.setVisible(false); requestFocusInWindow(); } }
    private void exitToMenu() { stop(); if (listener != null) listener.onExitToMenuRequested(); }
    public void saveGame() { saveWorld(); }
    @Override public void doLayout() { super.doLayout(); centerPauseMenu(); }

    // Getters
    public int getAncho(){ return ancho; }
    public int getAlto(){ return alto; }
    public Graphics2D getOffscreenGraphics(){ return graphics; }
    public Input getInput(){ return input; }
    public BasicBlock[][] getMundo(){ return mundo; }
    public void setMundo(BasicBlock[][] nuevo){ this.mundo = nuevo; }
    public boolean isVsyncEnabled() { return vsyncEnabled; }
    public void setVsyncEnabled(boolean enabled) { this.vsyncEnabled = enabled; }
    public Lighting.LightGrid getLightGrid() { return lightGrid; }
    public boolean isLightDebugEnabled() { return debugLight; }

    public Panel() {
        this(null);
    }
    public Panel(Listener listener) {
        this.listener = listener;
    }
}
