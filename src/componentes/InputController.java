package componentes;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * Controla la captura de teclado y actualiza el estado de {@link Input}.
 * Permite desacoplar el manejo de eventos de la clase Panel.
 */
public class InputController {
    private final JComponent component;
    private final Input input;
    private final KeyAdapter keyAdapter = new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
            final boolean estado = true;
            if (e.getKeyCode() == KeyEvent.VK_A) input.setKeyA(estado);
            if (e.getKeyCode() == KeyEvent.VK_D) input.setKeyD(estado);
            if (e.getKeyCode() == KeyEvent.VK_SPACE) input.pressSpace();
        }
        @Override
        public void keyReleased(KeyEvent e) {
            final boolean estado = false;
            if (e.getKeyCode() == KeyEvent.VK_SPACE) input.releaseSpace();
            if (e.getKeyCode() == KeyEvent.VK_A) input.setKeyA(estado);
            if (e.getKeyCode() == KeyEvent.VK_D) input.setKeyD(estado);
        }
    };

    /**
     * Crea un controlador de entrada asociado a un componente Swing.
     * @param component componente que recibirá los eventos de teclado
     * @param input objeto de estado que será actualizado
     */
    public InputController(JComponent component, Input input) {
        this.component = component;
        this.input = input;
    }

    /** Instala el KeyListener en el componente y solicita foco. */
    public void install() {
        component.setFocusable(true);
        component.requestFocusInWindow();
        component.addKeyListener(keyAdapter);
    }

    /** Desinstala el KeyListener del componente. */
    public void uninstall() {
        component.removeKeyListener(keyAdapter);
    }
}
