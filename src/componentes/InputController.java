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
            switch (e.getKeyCode()) {
                case KeyEvent.VK_A -> input.setKeyA(true);
                case KeyEvent.VK_D -> input.setKeyD(true);
                case KeyEvent.VK_SPACE -> input.pressSpace();
                default -> {}
            }
        }
        @Override
        public void keyReleased(KeyEvent e) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_SPACE -> input.releaseSpace();
                case KeyEvent.VK_A -> input.setKeyA(false);
                case KeyEvent.VK_D -> input.setKeyD(false);
                default -> {}
            }
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
