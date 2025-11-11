package componentes;

import juego.Jugador;
import juego.bloques.BasicBlock;
import programa.Panel;

import java.awt.Graphics2D;
import java.util.List;

/**
 * Bucle principal del juego responsable de: actualizar estado, renderizar y regular FPS.
 *
 * <p>Implementa un game loop simple sincronizado a un objetivo de 60 FPS mediante cálculo
 * de tiempo de trabajo y compensación con sleep. Usa un buffer offscreen provisto por {@link Panel}.</p>
 */
public class GameLoop implements Runnable {
    private static final int FPS = 60;
    private static final long FRAME_TIME_NANOS = 1_000_000_000L / FPS;

    private final Panel panel;
    private final Jugador jugador;
    private final Camara camara;
    private final HudDebug hud;
    private final EditorMundo editorMundo;
    private final Renderer renderer;
    private final List<BasicBlock> bloquesVisibles;
    private final Input input;
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
                    Input input) {
        this.panel = panel;
        this.jugador = jugador;
        this.camara = camara;
        this.hud = hud;
        this.editorMundo = editorMundo;
        this.renderer = renderer;
        this.bloquesVisibles = bloquesVisibles;
        this.input = input;
    }

    /** Solicita la parada del loop en el siguiente ciclo. */
    public void detener() { running = false; }

    /**
     * Ejecuta el bucle: update -> render -> sincronización FPS.
     */
    @Override
    public void run() {
        final double dt = 1.0 / FPS;
        while (running) {
            long inicio = System.nanoTime();
            // Capturar referencia actual del mundo (puede cambiar por carga debug)
            BasicBlock[][] mundo = panel.getMundo();
            // Update
            var cercanos = MundoHelper.obtenerBloquesCercanosJugador(mundo, jugador, 2);
            jugador.update(input, dt, cercanos);
            camara.update(jugador, mundo, dt);
            MundoHelper.actualizarBloquesVisibles(bloquesVisibles, mundo, camara, panel.getAncho(), panel.getAlto());
            // Render offscreen
            Graphics2D g = panel.getOffscreenGraphics();
            renderer.drawBackground(g, panel.getAncho(), panel.getAlto());
            renderer.drawGame(g, bloquesVisibles, jugador, camara, editorMundo);
            hud.draw(g);
            panel.present();
            // Timing
            long trabajo = System.nanoTime() - inicio;
            long restante = FRAME_TIME_NANOS - trabajo;
            if (restante > 0) {
                long ms = restante / 1_000_000L;
                int ns = (int)(restante % 1_000_000L);
                try { Thread.sleep(ms, ns); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            long fin = System.nanoTime();
            hud.updateFrame(fin - inicio);
        }
    }
}
