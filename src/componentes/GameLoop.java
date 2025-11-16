package componentes;

import juego.Jugador;
import juego.bloques.BasicBlock;
import juego.mundo.Mundo;
import juego.mundo.Chunk;
import programa.Panel;

import java.awt.Graphics2D;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Bucle principal del juego responsable de: actualizar estado, renderizar y regular FPS.
 *
 * <p>Soporta dt variable y, opcionalmente, un límite de FPS tipo "vsync" mediante sleep.</p>
 */
public class GameLoop implements Runnable {
    private static final int TARGET_FPS = 60;
    private static final long FRAME_TIME_NANOS = 1_000_000_000L / TARGET_FPS;

    private final Panel panel;
    private final Jugador jugador;
    private final Camara camara;
    private final HudDebug hud;
    private final EditorMundo editorMundo;
    private final Renderer renderer;
    private final List<BasicBlock> bloquesVisibles;
    private final Input input;
    private final BooleanSupplier isPaused;
    private final Runnable awaitIfPaused;
    private final Supplier<Boolean> vsyncEnabled;
    private volatile boolean running = true;

    /**
     * Crea un loop con todas las dependencias necesarias del juego.
     */
    public GameLoop(Panel panel,
                    Jugador jugador,
                    Camara camara,
                    HudDebug hud,
                    EditorMundo editorMundo,
                    Renderer renderer,
                    List<BasicBlock> bloquesVisibles,
                    Input input,
                    BooleanSupplier isPaused,
                    Runnable awaitIfPaused,
                    Supplier<Boolean> vsyncEnabled) {
        this.panel = panel;
        this.jugador = jugador;
        this.camara = camara;
        this.hud = hud;
        this.editorMundo = editorMundo;
        this.renderer = renderer;
        this.bloquesVisibles = bloquesVisibles;
        this.input = input;
        this.isPaused = (isPaused != null) ? isPaused : () -> false;
        this.awaitIfPaused = (awaitIfPaused != null) ? awaitIfPaused : () -> {};
        this.vsyncEnabled = (vsyncEnabled != null) ? vsyncEnabled : () -> Boolean.TRUE;
    }

    /** Solicita la parada del loop en el siguiente ciclo. */
    public void detener() { running = false; }

    /**
     * Ejecuta el bucle con dt variable; si vsync está activo, se limita a TARGET_FPS.
     */
    @Override
    public void run() {
        long lastTime = System.nanoTime();
        while (running) {
            if (isPaused.getAsBoolean()) {
                awaitIfPaused.run();
                lastTime = System.nanoTime(); // resetear delta tras reanudar
                continue;
            }
            long inicio = System.nanoTime();
            double dt = (inicio - lastTime) / 1_000_000_000.0; // segundos
            lastTime = inicio;
            if (dt > 0.1) dt = 0.1;

            Mundo mundo = panel.getMundo();
            if (mundo == null) continue;

            mundo.update(jugador.getPosicion());

            var cercanos = MundoHelper.obtenerBloquesCercanosJugador(mundo, jugador, 2);
            jugador.update(input, dt, cercanos);
            camara.update(jugador, null, dt); // mundo grid no longer used
            int vpWWorldPx = (int)Math.round(panel.getAncho() / panel.getRenderScale());
            int vpHWorldPx = (int)Math.round(panel.getAlto() / panel.getRenderScale());
            MundoHelper.actualizarBloquesVisibles(bloquesVisibles, mundo, camara, vpWWorldPx, vpHWorldPx);

            synchronized (((programa.Panel)panel).getRenderLock()) {
                Graphics2D g = panel.getOffscreenGraphics();
                renderer.drawBackground(g, panel.getAncho(), panel.getAlto());
                renderer.drawGame(g, bloquesVisibles, jugador, camara, editorMundo, panel.getRenderScale(), panel.getLightGrid(), panel.isLightDebugEnabled());
                if (input.isDebugChunkGrid()) {
                    renderer.drawChunkGrid(g, camara, panel.getRenderScale());
                }
                // Actualizar datos del HUD antes de dibujarlo
                double size = BasicBlock.getSize();
                double tx = Math.floor(jugador.getX() / size);
                double screenYBlocks = Math.floor(jugador.getY() / size);
                // Convertir Y de pantalla (origen arriba) a Y lógica (origen abajo)
                final int WORLD_HEIGHT_BLOCKS = Mundo.WORLD_HEIGHT_BLOCKS;
                int tyLogical = (int)(WORLD_HEIGHT_BLOCKS - 1 - screenYBlocks);
                hud.setPlayerPosition(tx, tyLogical);
                // Calcular chunk usando tamaño real y Y lógica (bottom-based)
                int blockX = (int)Math.floor(jugador.getX() / size);
                int blockY = tyLogical;
                int playerChunkX = Math.floorDiv(blockX, Chunk.CHUNK_SIZE);
                int playerChunkY = Math.floorDiv(blockY, Chunk.CHUNK_SIZE);
                hud.setPlayerChunk(playerChunkX, playerChunkY);
                hud.draw(g);
            }
            panel.present();

            long trabajo = System.nanoTime() - inicio;
            if (Boolean.TRUE.equals(vsyncEnabled.get())) {
                long restante = FRAME_TIME_NANOS - trabajo;
                if (restante > 0) {
                    long ms = restante / 1_000_000L;
                    int ns = (int)(restante % 1_000_000L);
                    try { Thread.sleep(ms, ns); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            }
            long fin = System.nanoTime();
            hud.updateFrame(fin - inicio);
        }
    }
}
