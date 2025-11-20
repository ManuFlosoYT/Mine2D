package programa;

import componentes.*;
import componentes.Renderer;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import juego.Jugador;
import juego.bloques.BasicBlock;
import juego.mundo.Chunk;
import juego.mundo.ChunkIOManager;
import juego.mundo.Mundo;

/**
 * Superficie principal de renderizado del juego.
 *
 * <p>Responsabilidades:
 * - Inicializar mundo, cámara, HUD, renderer y editor.
 * - Gestionar el buffer offscreen y presentar la imagen en pantalla.
 * - Delegar el bucle de juego a {@link GameLoop} y el input a {@link InputController}.</p>
 */
public class Panel extends JComponent {
    private int ancho;
    private int alto;
    private Thread gameThread;

    private final Object renderLock = new Object();
    private Graphics2D graphics; // offscreen
    private volatile BufferedImage image; // buffer mostrado por Swing
    private Input input;
    private InputController inputController;
    private Jugador jugador;
    private Mundo mundo;
    private ChunkIOManager chunkIOManager;
    private volatile Lighting.LightGrid lightGrid;
    private final List<BasicBlock> bloquesVisibles = new ArrayList<>();
    private Camara camara;
    private HudDebug hud;
    private EditorMundo editorMundo; // editor
    private Renderer renderer;
    private GameLoop loop;

    private final GameState gameState = new GameState();

    private PauseMenuPanel pauseMenu;
    public interface Listener { void onExitToMenuRequested(); }
    private final Listener listener;

    private volatile boolean vsyncEnabled = true; // limitar FPS (VSync simulado)
    private volatile double renderScale = 1.0; // factor de escala de mundo -> pantalla
    private volatile boolean debugLight = false; // mostrar números de luz en bloques

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
        if (mundo != null) mundo.close();
    }

    private void initGame(){
        long seed = 12345L; // Or load from metadata
        mundo = new Mundo(seed);
        chunkIOManager = new ChunkIOManager();

        jugador = new Jugador();
        tipos.Punto spawnPoint = chunkIOManager.loadWorld(mundo);

        // Spawn por defecto en X=0
        double startX = 0;

        if (spawnPoint.x() == 0 && spawnPoint.y() == 0) {
            // Mundo nuevo: precargar un área 5x5 de chunks para evitar mundo vacío
            int worldBlockX = (int)Math.floor(startX / BasicBlock.getSize());
            int spawnChunkX = worldBlockX / Chunk.CHUNK_SIZE;
            for (int cx = spawnChunkX - 2; cx <= spawnChunkX + 2; cx++) {
                for (int cy = 0; cy <= 4; cy++) { // cargar algunas alturas iniciales
                    mundo.ensureChunkLoadedSync(cx, cy);
                }
            }

            double spawnY = computeGroundSpawnY(startX) - jugador.getAltoPx();
            if (Double.isNaN(spawnY)) spawnY = 0; // fallback defensivo
            spawnPoint = new tipos.Punto(startX, spawnY);
        }
        jugador.colocar(spawnPoint);

        // La cámara debe usar el viewport en píxeles de mundo (pantalla dividida por escala)
        int vpWWorldPx = (int)Math.round(ancho / renderScale);
        int vpHWorldPx = (int)Math.round(alto / renderScale);
        camara = new Camara(vpWWorldPx, vpHWorldPx);
        camara.update(jugador, null, 0); // mundo is not a grid anymore
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
        // This should be handled inside Mundo now, perhaps triggered by block changes.
        // For now, let's leave it empty.
    }

    /** Devuelve la coordenada Y (en píxeles) de la parte superior del bloque más alto en la columna de xPx.
     * Si no hay bloques en esa columna, devuelve 0. */
    private double computeGroundSpawnY(double xPx) {
        int worldX = (int) Math.floor(xPx / BasicBlock.getSize());
        int chunkX = worldX / Chunk.CHUNK_SIZE;
        int blockXInChunk = worldX % Chunk.CHUNK_SIZE;

        // Buscar desde arriba (worldHeight-1) hacia abajo (0) en términos lógicos
        final int worldHeight = 256; // debe coincidir con GeneradorMundo

        for (int logicalY = worldHeight - 1; logicalY >= 0; logicalY--) {
            int chunkY = logicalY / Chunk.CHUNK_SIZE;
            int blockYInChunk = logicalY % Chunk.CHUNK_SIZE;
            Chunk chunk = mundo.getChunk(chunkX, chunkY);
            if (chunk == null) continue;
            if (chunk.getBlock(blockXInChunk, blockYInChunk) != null) {
                // Convertir altura lógica (desde el fondo) a Y de pantalla usando misma fórmula que GeneradorMundo
                double screenY = (worldHeight - 1 - logicalY) * BasicBlock.getSize();
                return screenY;
            }
        }
        return 0; // fallback
    }

    /** Guardar estado del mundo a archivo. */
    private void saveWorld() {
        if (mundo != null && chunkIOManager != null) {
            chunkIOManager.saveWorld(mundo, jugador.getPosicion());
            System.out.println("[INFO] Mundo guardado.");
        }
    }

    /** Carga la partida guardada si existe (world.wgz) sin mensajes de depuración excesivos. */
    public void cargarPartidaGuardada() {
        // This is now handled in initGame()
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
        getActionMap().put("toggleLightDebug", new javax.swing.AbstractAction() { @Override public void actionPerformed(java.awt.event.ActionEvent e) {
            boolean current = isLightDebugEnabled();
            debugLight = !current;
            repaint();
        }});
    }
    private void togglePause() { if (gameState.isPaused()) resumeGame(); else pauseGame(); }
    public void pauseGame() { gameState.pause(); if(pauseMenu!=null){ pauseMenu.setVisible(true); pauseMenu.requestFocusInWindow(); } }
    public void resumeGame() { gameState.resume(); if(pauseMenu!=null){ pauseMenu.setVisible(false); requestFocusInWindow(); } }
    private void exitToMenu() { stop(); if (listener != null) listener.onExitToMenuRequested(); }
    @Override public void doLayout() { super.doLayout(); centerPauseMenu(); }

    // Getters
    public int getAncho(){ return ancho; }
    public int getAlto(){ return alto; }
    public Graphics2D getOffscreenGraphics(){ return graphics; }
    public Mundo getMundo(){ return mundo; }
    public void setMundo(Mundo nuevo){ this.mundo = nuevo; }
    public void setVsyncEnabled(boolean enabled) { this.vsyncEnabled = enabled; }
    public Lighting.LightGrid getLightGrid() { return lightGrid; }
    public boolean isLightDebugEnabled() { return debugLight; }

    public Panel(Listener listener) {
        this.listener = listener;
    }
}
