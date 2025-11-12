package componentes;

import juego.Jugador;
import juego.bloques.BasicBlock;
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

            BasicBlock[][] mundo = panel.getMundo();
            var cercanos = MundoHelper.obtenerBloquesCercanosJugador(mundo, jugador, 2);
            jugador.update(input, dt, cercanos);
            camara.update(jugador, mundo, dt);
            int vpWWorldPx = (int)Math.round(panel.getAncho() / panel.getRenderScale());
            int vpHWorldPx = (int)Math.round(panel.getAlto() / panel.getRenderScale());
            MundoHelper.actualizarBloquesVisibles(bloquesVisibles, mundo, camara, vpWWorldPx, vpHWorldPx);

            synchronized (((programa.Panel)panel).getRenderLock()) {
                Graphics2D g = panel.getOffscreenGraphics();
                renderer.drawBackground(g, panel.getAncho(), panel.getAlto());
                renderer.drawGame(g, bloquesVisibles, jugador, camara, editorMundo, panel.getRenderScale());
                // Actualizar datos del HUD antes de dibujarlo (en bloques, Y invertida: 0 abajo)
                double size = BasicBlock.getSize();
                double tx = Math.floor(jugador.getX() / size);
                int altoTiles = (panel.getMundo() != null) ? panel.getMundo().length : 0;
                int tileTop = (int)Math.floor(jugador.getY() / size);
                int yFromBottom = 0;
                if (altoTiles > 0) {
                    yFromBottom = altoTiles - 1 - tileTop;
                    if (yFromBottom < 0) yFromBottom = 0;
                    if (yFromBottom >= altoTiles) yFromBottom = altoTiles - 1;
                }
                hud.setPlayerPosition(tx, yFromBottom);
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
