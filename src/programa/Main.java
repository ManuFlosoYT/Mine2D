package programa;

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Ventana principal del juego Mine2D.
 *
 * <p>Configura un menú principal con opciones de "Jugar" y "Salir". Al pulsar
 * "Jugar" se crea el {@link Panel} de juego y se inicia el loop; al cerrar la
 * ventana se detiene el juego si está en marcha.</p>
 */
public class Main extends JFrame {

    private static final String TITULO = "Mine2D";
    /** Ancho de la ventana en píxeles. */
    public static final int ANCHO = 1200;
    /** Alto de la ventana en píxeles. */
    public static final int ALTO = 800;

    // Cards
    private static final String CARD_MENU = "menu";
    private static final String CARD_GAME = "game";

    private CardLayout cardLayout;
    private JPanel root;
    private MenuPanel menuPanel;
    private Panel gamePanel;
    private boolean gameStarted = false;

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

        // Contenedor con CardLayout: menú principal y juego
        cardLayout = new CardLayout();
        root = new JPanel(cardLayout);

        // Menú principal
        menuPanel = new MenuPanel(new MenuPanel.Listener() {
            @Override
            public void onPlayRequested() {
                startGameIfNeeded();
            }

            @Override
            public void onExitRequested() {
                dispatchEvent(new WindowEvent(Main.this, WindowEvent.WINDOW_CLOSING));
            }
        });

        root.add(menuPanel, CARD_MENU);
        // No creamos el gamePanel aún; se construye al pulsar Jugar

        setLayout(new BorderLayout());
        setContentPane(root);

        // Agregar listeners para manejar el inicio y parada del programa
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                // Mostrar primero el menú
                showMenu();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                if (gamePanel != null) {
                    gamePanel.stop();
                }
            }
        });

    }

    private void showMenu() {
        cardLayout.show(root, CARD_MENU);
        root.requestFocusInWindow();
    }

    private void showGame() {
        cardLayout.show(root, CARD_GAME);
        if (gamePanel != null) {
            gamePanel.requestFocusInWindow();
        }
    }

    private void startGameIfNeeded() {
        if (!gameStarted) {
            gamePanel = new Panel(() -> {
                // salida al menú desde pausa
                root.remove(gamePanel);
                gamePanel = null;
                gameStarted = false;
                showMenu();
                root.revalidate();
                root.repaint();
            });
            root.add(gamePanel, CARD_GAME);
            showGame();
            gamePanel.start();
            // Cargar partida guardada si existe
            gamePanel.cargarPartidaGuardada();
            gameStarted = true;
        } else {
            showGame();
            // Si se regresa al juego y aún no se cargó (caso improbable), intentar cargar
            gamePanel.cargarPartidaGuardada();
        }
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
