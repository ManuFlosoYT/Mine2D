package programa;

import javax.swing.JComponent;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.security.Key;

import componentes.Input;
import juego.Jugador;

public class Panel extends JComponent {
    private static final int FPS = 60;

    // Duración de cada frame objetivo en nanosegundos (≈16_666_666 ns para 60 FPS)
    private static final long FRAME_TIME_NANOS = 1_000_000_000L / FPS;

    private int ancho;
    private int alto;
    private Thread gameThread;
    private volatile boolean enJuego = true; // volatile para visibilidad entre hilos

    private Graphics2D graphics;
    private BufferedImage image;
    private Input input;
    private Jugador jugador;

    public void start() {

        ancho = getWidth();
        alto = getHeight();

        if (ancho <= 0 || alto <= 0) { // fallback por si aún no tiene tamaño
            ancho = Main.ANCHO;
            alto = Main.ALTO;
        }

        // Crear imagen en memoria para renderizado
        image = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        graphics = image.createGraphics();

        // Configurar opciones de renderizado
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        initGame();
        initInput();

        // Iniciar el hilo del juego
        gameThread = new Thread(() -> {
            long frame = 0; // Contador de frames para depuración
            final double dt = 1.0 / FPS; // paso fijo en segundos

            while(enJuego) {
                long tiempoInicioFrame = System.nanoTime();

                // Actualización de juego
                jugador.update(input, dt);

                // Renderizado
                drawBackground();
                drawGame();
                render();

                long tiempoTomado = System.nanoTime() - tiempoInicioFrame;
                long tiempoRestante = FRAME_TIME_NANOS - tiempoTomado;

                if( tiempoRestante > 0 ) {
                    long sleepMs = tiempoRestante / 1_000_000L; // parte en milisegundos
                    int sleepNs = (int) (tiempoRestante % 1_000_000L); // resto en nanosegundos (< 1_000_000)
                    sleep(sleepMs, sleepNs);

                    //System.out.println("Frame: " + frame + " sleep: " + sleepMs + " ms + " + sleepNs + " ns");
                } else {
                    // Vamos atrasados
                    System.err.println("Frame: " + frame + " fuera de presupuesto por: " + (-tiempoRestante / 1_000_000L) + " ms");
                }

                frame++;
            }
        }, "GameLoopThread");

        gameThread.start();
    }

    private void initGame(){
        jugador = new Jugador();
        // Colocar al jugador en el suelo, con un pequeño margen desde la izquierda
        jugador.colocar(new tipos.Punto(100, Main.ALTO - jugador.getAltoPx()));
    }

    private void initInput(){
        input = new Input();
        setFocusable(true);
        requestFocusInWindow();
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                final boolean estado = true;

                if(e.getKeyCode() == KeyEvent.VK_W){
                      input.setKeyW(estado);
                      return;
                }
                if(e.getKeyCode() == KeyEvent.VK_A){
                    input.setKeyA(estado);
                    return;
                }
                if(e.getKeyCode() == KeyEvent.VK_D){
                    input.setKeyD(estado);
                    return;
                }
                if(e.getKeyCode() == KeyEvent.VK_S){
                    input.setKeyS(estado);
                    return;
                }
                if(e.getKeyCode() == KeyEvent.VK_SPACE){
                    input.setKeySpace(estado);
                    return;
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                final boolean estado = false;

                if(e.getKeyCode() == KeyEvent.VK_W){
                    input.setKeyW(estado);
                    return;
                }
                if(e.getKeyCode() == KeyEvent.VK_A){
                    input.setKeyA(estado);
                    return;
                }
                if(e.getKeyCode() == KeyEvent.VK_D){
                    input.setKeyD(estado);
                    return;
                }
                if(e.getKeyCode() == KeyEvent.VK_S){
                    input.setKeyS(estado);
                    return;
                }
                if(e.getKeyCode() == KeyEvent.VK_SPACE){
                    input.setKeySpace(estado);
                    return;
                }
            }
        });
    }

    public void stop(){
        enJuego = false;
        if(gameThread != null && gameThread.isAlive()){
            try {
                gameThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void drawBackground(){
        graphics.setColor(new Color(30, 30, 30));
        graphics.fillRect(0, 0, ancho, alto);
    }

    private void drawGame(){
        jugador.draw(graphics);
    }

    private void render(){
        Graphics g = this.getGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
    }

    private void sleep(long millis, int nanos){
        try {
            Thread.sleep(millis, nanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }
    }

    private void sleep(long millis){
        this.sleep(millis, 0);
    }

}
