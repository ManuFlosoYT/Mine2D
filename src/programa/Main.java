package programa;

import javax.swing.JFrame;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Ventana principal del juego Mine2D.
 *
 * <p>Configura el {@link Panel} de renderizado y controla el ciclo de vida del juego
 * iniciando el loop al abrir la ventana y deteniéndolo al cerrarla.</p>
 */
public class Main extends JFrame {

    private static final String TITULO = "Mine2D";
    /** Ancho de la ventana en píxeles. */
    public static final int ANCHO = 1200;
    /** Alto de la ventana en píxeles. */
    public static final int ALTO = 800;

    /** Crea y configura la ventana de juego. */
    public Main() {
        init();
    }

    /**
     * Inicializa propiedades de la ventana y registra listeners de ciclo de vida.
     */
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

    /**
     * Punto de entrada de la aplicación.
     * @param args argumentos de línea de comandos (no usados)
     */
    public static void main(String[] args) {
        // Crear y mostrar la ventana principal
        Main main = new Main();
        main.setVisible(true);
    }

}
