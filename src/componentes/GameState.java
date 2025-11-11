package componentes;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Gestor de estado de juego (RUNNING/PAUSED) con primitiva de espera para pausar hilos.
 */
public class GameState {
    public enum State { RUNNING, PAUSED }

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition unpaused = lock.newCondition();
    private volatile State state = State.RUNNING;

    /** Devuelve true si el juego está en pausa. */
    public boolean isPaused() { return state == State.PAUSED; }

    /** Pone el estado en pausa (no bloquea hilos actuales). */
    public void pause() {
        lock.lock();
        try { state = State.PAUSED; } finally { lock.unlock(); }
    }

    /** Reanuda el juego y despierta a todos los hilos en espera. */
    public void resume() {
        lock.lock();
        try { state = State.RUNNING; unpaused.signalAll(); } finally { lock.unlock(); }
    }

    /** Bloquea el hilo hasta que el estado no sea PAUSED. Es segura contra interrupciones. */
    public void awaitIfPaused() {
        lock.lock();
        try {
            while (state == State.PAUSED) {
                try {
                    unpaused.await();
                } catch (InterruptedException ie) {
                    // Permitir salir si el hilo ha sido interrumpido (p.ej., al detener)
                    return;
                }
            }
        } finally {
            lock.unlock();
        }
    }
}
