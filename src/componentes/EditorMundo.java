package componentes;

import juego.bloques.BasicBlock;
import juego.Jugador;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;

public class EditorMundo {
    private volatile BasicBlock[][] mundo; // rejilla [fila(y)][col(x)]
    private final Camara camara;           // para convertir coords de pantalla -> mundo
    private final JComponent superficie;   // componente que recibe eventos de mouse
    private final Thread thread;           // thread de edición
    private final Jugador jugador;         // referencia al jugador para calcular adyacencia
    private volatile boolean running = true;

    // Estado del ratón
    private volatile boolean leftDown = false;
    private volatile int mouseX = 0; // coords de pantalla relativas a superficie
    private volatile int mouseY = 0;

    // Seguimiento del tile objetivo y progreso de rotura
    private volatile int targetTileX = Integer.MIN_VALUE;
    private volatile int targetTileY = Integer.MIN_VALUE;
    private volatile double holdTimeSeconds = 0.0; // ahora volatile para lectura segura
    private volatile double currentDureza = 0.0;    // dureza del bloque actual (0 si ninguno)

    // Tracking de bloque bajo el cursor
    private volatile int hoverTileX = Integer.MIN_VALUE;
    private volatile int hoverTileY = Integer.MIN_VALUE;

    public EditorMundo(BasicBlock[][] mundo, Camara camara, JComponent superficie, Jugador jugador) {
        this.mundo = mundo;
        this.camara = camara;
        this.superficie = superficie;
        this.jugador = jugador;
        instalarMouseListener();
        this.thread = new Thread(this::loop, "WorldEditorThread");
    }

    /** Inicia el thread de edición */
    public void start() { thread.start(); }

    /** Detiene el thread de edición */
    public void stop() {
        running = false;
        thread.interrupt();
        try { thread.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /** Permite actualizar la referencia al mundo si se regenera */
    public void setMundo(BasicBlock[][] nuevo) { this.mundo = nuevo; }

    private void instalarMouseListener() {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    leftDown = true;
                    mouseX = e.getX();
                    mouseY = e.getY();
                }
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    mouseX = e.getX();
                    mouseY = e.getY();
                }
            }
            @Override
            public void mouseMoved(MouseEvent e) {
                // Actualizar posición del cursor aunque no haya click
                mouseX = e.getX();
                mouseY = e.getY();
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    leftDown = false;
                    // resetear progreso al soltar
                    holdTimeSeconds = 0.0;
                    targetTileX = Integer.MIN_VALUE;
                    targetTileY = Integer.MIN_VALUE;
                }
            }
        };
        superficie.addMouseListener(adapter);
        superficie.addMouseMotionListener(adapter);
    }

    private boolean isTileInteractable(int tileX, int tileY, Rectangle2D pb, double size, BasicBlock[][] world) {
        int pMinX = (int) Math.floor(pb.getX() / size);
        int pMinY = (int) Math.floor(pb.getY() / size);
        // restamos un epsilon para incluir borde superior/derecho dentro del mismo tile cuando el tamaño es múltiplo exacto
        double eps = 1e-6;
        int pMaxX = (int) Math.floor((pb.getX() + pb.getWidth() - eps) / size);
        int pMaxY = (int) Math.floor((pb.getY() + pb.getHeight() - eps) / size);

        // Área de interacción: expandir 1 tile a cada lado horizontal y vertical
        int areaMinX = pMinX - 1;
        int areaMaxX = pMaxX + 1;
        int areaMinY = pMinY - 1;
        int areaMaxY = pMaxY + 1; // (jugador 2 tiles alto +1 arriba +1 abajo = 4)

        // Fuera del área
        if (tileX < areaMinX || tileX > areaMaxX || tileY < areaMinY || tileY > areaMaxY) return false;
        // Dentro del cuerpo del jugador: no interactuable
        if (tileX >= pMinX && tileX <= pMaxX && tileY >= pMinY && tileY <= pMaxY) return false;

        return hasLineOfSight(tileX, tileY, pMinX, pMaxX, pMinY, pMaxY, world);
    }

    private boolean hasLineOfSight(int tileX, int tileY, int pMinX, int pMaxX, int pMinY, int pMaxY, BasicBlock[][] world) {
        int originX = clamp(tileX, pMinX, pMaxX);
        int originY = clamp(tileY, pMinY, pMaxY);
        // Probar ambos órdenes de recorrido Manhattan
        return pathClearHV(originX, originY, tileX, tileY, world) || pathClearVH(originX, originY, tileX, tileY, world);
    }

    private boolean pathClearHV(int x0, int y0, int x1, int y1, BasicBlock[][] world) {
        return segmentClearHorizontal(x0, y0, x1, world) && segmentClearVertical(x1, y0, y1, world);
    }

    private boolean pathClearVH(int x0, int y0, int x1, int y1, BasicBlock[][] world) {
        return segmentClearVertical(x0, y0, y1, world) && segmentClearHorizontal(x0, y1, x1, world);
    }

    private int clamp(int v, int min, int max) { return Math.min(Math.max(v, min), max); }

    private boolean segmentClearHorizontal(int x0, int y, int x1, BasicBlock[][] world) {
        int dir = Integer.compare(x1, x0);
        for (int x = x0 + dir; x != x1; x += dir) {
            if (!isAir(x, y, world)) return false;
        }
        return true;
    }

    private boolean segmentClearVertical(int x, int y0, int y1, BasicBlock[][] world) {
        int dir = Integer.compare(y1, y0);
        for (int y = y0 + dir; y != y1; y += dir) {
            if (!isAir(x, y, world)) return false;
        }
        return true;
    }

    private boolean isAir(int tileX, int tileY, BasicBlock[][] world) {
        if (tileY < 0 || tileX < 0) return true;
        int arrY = world.length - 1 - tileY;
        if (arrY < 0 || arrY >= world.length || tileX >= world[0].length) return true;
        return world[arrY][tileX] == null;
    }

    // --- Getters para feedback visual ---
    public int getTargetTileX() { return targetTileX; }
    public int getTargetTileY() { return targetTileY; }
    public double getBreakProgress() {
        if (currentDureza <= 0) return 0.0;
        return Math.min(1.0, holdTimeSeconds / currentDureza);
    }
    public boolean isBreaking() {
        return leftDown && currentDureza > 0 && targetTileX != Integer.MIN_VALUE && targetTileY != Integer.MIN_VALUE && holdTimeSeconds > 0.0;
    }
    public int getHoverTileX() { return hoverTileX; }
    public int getHoverTileY() { return hoverTileY; }
    public boolean isHoveringInteractable() { return hoverTileX != Integer.MIN_VALUE && hoverTileY != Integer.MIN_VALUE; }

    private void loop() {
        final double size = BasicBlock.getSize();
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            double dt = (now - last) / 1_000_000_000.0;
            last = now;

            BasicBlock[][] local = mundo;
            if (local != null) {
                double worldXHover = camara.getX() + mouseX;
                double worldYHover = camara.getY() + mouseY;
                int htx = (int)Math.floor(worldXHover / size);
                int hty = (int)Math.floor(worldYHover / size);
                int harrY = local.length - 1 - hty;
                if (htx >= 0 && hty >= 0 && harrY >= 0 && harrY < local.length && htx < local[0].length) {
                    Rectangle2D pbHover = jugador.getBounds();
                    if (isTileInteractable(htx, hty, pbHover, size, local) && local[harrY][htx] != null) {
                        hoverTileX = htx;
                        hoverTileY = hty;
                    } else {
                        hoverTileX = Integer.MIN_VALUE;
                        hoverTileY = Integer.MIN_VALUE;
                    }
                } else {
                    hoverTileX = Integer.MIN_VALUE;
                    hoverTileY = Integer.MIN_VALUE;
                }
            } else {
                hoverTileX = Integer.MIN_VALUE;
                hoverTileY = Integer.MIN_VALUE;
            }

            if (leftDown && local != null) {
                double worldX = camara.getX() + mouseX;
                double worldY = camara.getY() + mouseY;
                int tileX = (int) Math.floor(worldX / size);
                int tileY = (int) Math.floor(worldY / size);
                int arrYBreak = local.length - 1 - tileY;
                if (tileY >= 0 && tileX >= 0 && arrYBreak >= 0 && arrYBreak < local.length && tileX < local[0].length) {
                    Rectangle2D pb = jugador.getBounds();
                    if (!isTileInteractable(tileX, tileY, pb, size, local)) {
                        holdTimeSeconds = 0.0;
                        targetTileX = Integer.MIN_VALUE;
                        targetTileY = Integer.MIN_VALUE;
                        currentDureza = 0.0;
                    } else {
                        BasicBlock b = local[arrYBreak][tileX];
                        if (b == null) {
                            holdTimeSeconds = 0.0;
                            targetTileX = Integer.MIN_VALUE;
                            targetTileY = Integer.MIN_VALUE;
                            currentDureza = 0.0;
                        } else {
                            if (tileX != targetTileX || tileY != targetTileY) {
                                targetTileX = tileX;
                                targetTileY = tileY;
                                holdTimeSeconds = 0.0;
                                currentDureza = b.getDureza();
                            }
                            holdTimeSeconds += dt;
                            double dureza = b.getDureza();
                            currentDureza = dureza;
                            if (holdTimeSeconds >= dureza) {
                                local[arrYBreak][tileX] = null;
                                holdTimeSeconds = 0.0;
                                targetTileX = Integer.MIN_VALUE;
                                targetTileY = Integer.MIN_VALUE;
                                currentDureza = 0.0;
                            }
                        }
                    }
                } else {
                    holdTimeSeconds = 0.0;
                    targetTileX = Integer.MIN_VALUE;
                    targetTileY = Integer.MIN_VALUE;
                    currentDureza = 0.0;
                }
            } else {
                // No presionado: solo mantener hover, reset de rotura
                holdTimeSeconds = 0.0;
                targetTileX = Integer.MIN_VALUE;
                targetTileY = Integer.MIN_VALUE;
                currentDureza = 0.0;
            }

            try { Thread.sleep(10); } catch (InterruptedException e) { if (!running) break; }
            // Sleep breve para reducir uso de CPU; considerar integrar con game loop para eliminar warning
        }
    }
}
