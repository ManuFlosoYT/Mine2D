package componentes;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Representa el estado actual de las teclas relevantes para el jugador.
 *
 * <p>Incluye lógica para detectar el flanco de pulsación de la barra espaciadora (salto)
 * mediante un latch atómico consumible.</p>
 */
public class Input {

    private volatile boolean key_a;
    private volatile boolean key_d;
    private volatile boolean key_space;

    // Latch para detectar el flanco de pulsación de SPACE (thread-safe)
    private final AtomicBoolean spacePressedOnce = new AtomicBoolean(false);

    /** Indica si la tecla A está actualmente presionada. */
    public boolean isKeyA() {
        return key_a;
    }

    /** Actualiza el estado de la tecla A. */
    public void setKeyA(boolean key_a) {
        this.key_a = key_a;
    }

    /** Indica si la tecla D está actualmente presionada. */
    public boolean isKeyD() {
        return key_d;
    }

    /** Actualiza el estado de la tecla D. */
    public void setKeyD(boolean key_d) {
        this.key_d = key_d;
    }

    /** Indica si la tecla SPACE está actualmente mantenida. */
    public boolean isKeySpace() {
        return key_space;
    }

    /** Actualiza directamente el estado mantenido de SPACE. */
    public void setKeySpace(boolean key_space) {
        this.key_space = key_space;
    }

    // --- Nuevos helpers para SPACE ---
    /** Marca la pulsación de SPACE y activa el latch de flanco. */
    public void pressSpace() {
        this.key_space = true;
        this.spacePressedOnce.set(true);
    }

    /** Marca la liberación de SPACE. */
    public void releaseSpace() {
        this.key_space = false;
    }

    /**
     * Devuelve true solo una vez tras una pulsación de SPACE y resetea el latch.
     * @return true si la pulsación no se había consumido todavía.
     */
    public boolean consumeSpacePressedOnce() {
        return spacePressedOnce.getAndSet(false);
    }
}
