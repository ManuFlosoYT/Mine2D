package programa;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import javax.swing.JFrame;
import javax.swing.JPanel;

/**
 * Ventana principal del juego Mine2D.
 *
 * <p>Configura un menú principal con opciones de "Jugar", "Nuevo mundo" y "Salir". Al pulsar
 * "Jugar" se crea el {@link Panel} de juego y se inicia el loop; si existe el archivo world.wgz,
 * se carga la partida guardada. "Nuevo mundo" pregunta dimensiones (X e Y) y crea un mundo nuevo
 * sin cargar el guardado.</p>
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
    public Main() { init(); }

    /** Inicializa propiedades de la ventana y registra listeners de ciclo de vida. */
    private void init() {
        setTitle(TITULO);
        // Pantalla completa sin decoraciones (forzada por tamaño de pantalla)
        setUndecorated(true);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setBounds(0, 0, screen.width, screen.height);
        setResizable(false);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // No centrar; ya forzado a (0,0) y tamaño completo

        // Contenedor con CardLayout: menú principal y juego
        cardLayout = new CardLayout();
        root = new JPanel(cardLayout);

        // Menú principal
        menuPanel = new MenuPanel(new MenuPanel.Listener() {
            @Override
            public void onPlayRequested() {
                startGame(true); // Cargar mundo existente
            }
            @Override
            public void onNewWorldRequested() {
                // Borrar el mundo guardado para empezar de cero
                File worldFile = new File("world.wgz");
                if (worldFile.exists()) {
                    if (!worldFile.delete()) {
                        System.err.println("No se pudo borrar el mundo anterior.");
                    }
                }
                startGame(false); // Iniciar sin cargar
            }
            @Override
            public void onExitRequested() {
                dispatchEvent(new WindowEvent(Main.this, WindowEvent.WINDOW_CLOSING));
            }
        });

        root.add(menuPanel, CARD_MENU);
        // No creamos el gamePanel aún; se construye al pulsar una opción

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

    private void startGame(boolean loadSaved) {
        if (!gameStarted) {
            gamePanel = new Panel(() -> {
                // salida al menú desde pausa
                root.remove(gamePanel);
                gamePanel = null;
                showMenu();
                root.revalidate();
                root.repaint();
            });
            root.add(gamePanel, CARD_GAME);
            showGame();
            // El panel se encarga de cargar el mundo si existe en su método start()
            // Si es un mundo nuevo, el fichero ya se ha borrado.
            gamePanel.start();
            gameStarted = true;
        } else {
            showGame();
        }
    }

    /** Punto de entrada de la aplicación. */
    public static void main(String[] args) {
        Main main = new Main();
        main.setVisible(true);
    }
}
