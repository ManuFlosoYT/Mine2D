package programa;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.*; // para Toolkit y Dimension

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
                startGameWithLoadSaved();
            }
            @Override
            public void onNewWorldRequested() {
                int[] dims = promptWorldSize();
                if (dims != null) {
                    startGameNewWorld(dims[0], dims[1]);
                }
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

    private void startGameWithLoadSaved() {
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
            gamePanel.cargarPartidaGuardada();
        }
    }

    private void startGameNewWorld(int anchoMundo, int altoMundo) {
        if (!gameStarted) {
            gamePanel = new Panel(() -> {
                root.remove(gamePanel);
                gamePanel = null;
                gameStarted = false;
                showMenu();
                root.revalidate();
                root.repaint();
            });
            // Configurar tamaño del mundo antes de iniciar
            gamePanel.setWorldSize(anchoMundo, altoMundo);
            root.add(gamePanel, CARD_GAME);
            showGame();
            gamePanel.start();
            // No cargar partida guardada: es un mundo nuevo
            gameStarted = true;
        } else {
            // Si ya hubiera un juego (caso raro al estar en menú), simplemente mostrar
            showGame();
        }
    }

    /** Dialoga con el usuario para obtener ancho (X) y alto (Y) del nuevo mundo. */
    private int[] promptWorldSize() {
        String sx = JOptionPane.showInputDialog(this, "Tamaño en X (ancho en bloques)", "1024");
        if (sx == null) return null; // cancelado
        String sy = JOptionPane.showInputDialog(this, "Tamaño en Y (alto en bloques)", "128");
        if (sy == null) return null; // cancelado
        try {
            int w = Integer.parseInt(sx.trim());
            int h = Integer.parseInt(sy.trim());
            if (w <= 0 || h <= 0) throw new NumberFormatException("Valores deben ser > 0");
            // Opcional: límites razonables para evitar cuelgues
            if (w > 10000) w = 10000;
            if (h > 2048) h = 2048;
            return new int[]{ w, h };
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Valores inválidos. Introduce enteros positivos.", "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    /** Punto de entrada de la aplicación. */
    public static void main(String[] args) {
        Main main = new Main();
        main.setVisible(true);
    }
}
