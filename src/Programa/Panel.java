package Programa;

import javax.swing.JComponent;
import java.awt.*;
import java.awt.image.BufferedImage;

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

    public void start() {

        ancho = getWidth();
        alto = getHeight();

        // Crear imagen en memoria para renderizado
        image = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        graphics = image.createGraphics();

        // Configurar opciones de renderizado
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // Iniciar el hilo del juego
        gameThread = new Thread(() -> {
            long frame = 0; // Contador de frames para depuración

            while(enJuego) {
                long tiempoInicioFrame = System.nanoTime();

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
        // TODO lógica de juego y dibujo
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

}
