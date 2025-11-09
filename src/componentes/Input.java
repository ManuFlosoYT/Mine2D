package componentes;

import java.util.concurrent.atomic.AtomicBoolean;

public class Input {

    private volatile boolean key_w;
    private volatile boolean key_a;
    private volatile boolean key_s;
    private volatile boolean key_d;
    private volatile boolean key_space;

    // Latch para detectar el flanco de pulsación de SPACE (thread-safe)
    private final AtomicBoolean spacePressedOnce = new AtomicBoolean(false);

    public boolean isKeyW() {
        return key_w;
    }

    public void setKeyW(boolean key_w) {
        this.key_w = key_w;
    }

    public boolean isKeyA() {
        return key_a;
    }

    public void setKeyA(boolean key_a) {
        this.key_a = key_a;
    }

    public boolean isKeyS() {
        return key_s;
    }

    public void setKeyS(boolean key_s) {
        this.key_s = key_s;
    }

    public boolean isKeyD() {
        return key_d;
    }

    public void setKeyD(boolean key_d) {
        this.key_d = key_d;
    }

    public boolean isKeySpace() {
        return key_space;
    }

    public void setKeySpace(boolean key_space) {
        this.key_space = key_space;
    }

    // --- Nuevos helpers para SPACE ---
    public void pressSpace() {
        this.key_space = true;
        this.spacePressedOnce.set(true);
    }

    public void releaseSpace() {
        this.key_space = false;
    }

    public boolean consumeSpacePressedOnce() {
        return spacePressedOnce.getAndSet(false);
    }
}
