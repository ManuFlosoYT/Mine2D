package programa;

import javax.swing.JFrame;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class Main extends JFrame {

    private static final String TITULO = "Mine2D";
    public static final int ANCHO = 1200;
    public static final int ALTO = 800;

    public Main() {
        init();
    }

    private void init() {

        // Definir propiedades de la ventana
        setTitle(TITULO);
        setSize(ANCHO, ALTO);
        setResizable(false);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Configurar el diseño y agregar el panel
        setLayout(new BorderLayout());
        Panel panel = new Panel();
        add(panel);

        requestFocus();

        // Agregar listeners para manejar el inicio y parada del programa
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                panel.start();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                panel.stop();
            }
        });

    }

    static void main(String[] args) {
        // Crear y mostrar la ventana principal
        Main main = new Main();
        main.setVisible(true);
    }

}
