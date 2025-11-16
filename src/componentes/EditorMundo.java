package componentes;

import juego.bloques.BasicBlock;
import juego.Jugador;
import juego.mundo.Mundo;
import tipos.Punto;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

public class EditorMundo {
    private volatile Mundo mundo;
    private final Camara camara;
    private final JComponent superficie;
    private final Thread thread;
    private final Jugador jugador;
    private final BooleanSupplier isPausedSupplier;
    private final Runnable awaitIfPaused;
    private volatile boolean running = true;

    // Estado del ratón
    private volatile boolean leftDown = false;
    private volatile int mouseX = 0;
    private volatile int mouseY = 0;

    // Seguimiento del tile objetivo y progreso de rotura
    private volatile int targetTileX = Integer.MIN_VALUE;
    private volatile int targetTileY = Integer.MIN_VALUE;
    private volatile double holdTimeSeconds = 0.0;
    private volatile double currentDureza = 0.0;

    // Tracking de bloque bajo el cursor
    private volatile int hoverTileX = Integer.MIN_VALUE;
    private volatile int hoverTileY = Integer.MIN_VALUE;
    private volatile boolean hoverHasBlock = false;

    private final DoubleSupplier scaleSupplier;

    // Callback cuando el mundo cambia
    private Runnable onWorldChanged;

    public EditorMundo(Mundo mundo, Camara camara, JComponent superficie, Jugador jugador, BooleanSupplier isPausedSupplier, Runnable awaitIfPaused, DoubleSupplier scaleSupplier) {
        this.mundo = mundo;
        this.camara = camara;
        this.superficie = superficie;
        this.jugador = jugador;
        this.isPausedSupplier = (isPausedSupplier != null) ? isPausedSupplier : () -> false;
        this.awaitIfPaused = (awaitIfPaused != null) ? awaitIfPaused : () -> {};
        this.scaleSupplier = (scaleSupplier != null) ? scaleSupplier : () -> 1.0;
        instalarMouseListener();
        this.thread = new Thread(this::loop, "WorldEditorThread");
    }

    public void start() { thread.start(); }

    public void stop() {
        running = false;
        thread.interrupt();
        try { thread.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public void setMundo(Mundo nuevo) { this.mundo = nuevo; }

    public void setOnWorldChanged(Runnable r) { this.onWorldChanged = r; }

    private void instalarMouseListener() {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (isPausedSupplier.getAsBoolean()) return;
                double scale = Math.max(1.0, scaleSupplier.getAsDouble());
                double size = BasicBlock.getSize();
                double worldX = camara.getX() + e.getX() / scale;
                double worldY = camara.getY() + e.getY() / scale;
                int tileX = (int) Math.floor(worldX / size);
                int tileY = (int) Math.floor(worldY / size);
                int worldBlockY = (Mundo.WORLD_HEIGHT_BLOCKS - 1) - tileY;

                if (SwingUtilities.isLeftMouseButton(e)) {
                    BasicBlock b = mundo.getBlockAtTile(tileX, worldBlockY);
                    if (b != null && !b.isBreakable()) {
                        leftDown = false;
                        resetBreakState();
                        return;
                    }
                    leftDown = true;
                    mouseX = (int)Math.round(e.getX() / scale);
                    mouseY = (int)Math.round(e.getY() / scale);
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    Rectangle2D pb = jugador.getBounds();
                    if (!isTileInteractable(tileX, tileY, pb, size, mundo)) return;

                    BasicBlock existing = mundo.getBlockAtTile(tileX, worldBlockY);
                    if (existing == null || "water".equals(existing.getId())) {
                        // The block's logical position is determined by the tile coordinates.
                        // The block's visual position (Punto) must be in screen-based pixel coordinates for rendering.
                        Punto visualPosition = new Punto(tileX * size, tileY * size);
                        mundo.setBlockAtTile(tileX, worldBlockY, new BasicBlock("stone", visualPosition));
                        mundo.markChunkDirty(tileX, worldBlockY); // Mark for lighting update
                        hoverTileX = tileX; hoverTileY = tileY; hoverHasBlock = true;
                        if (onWorldChanged != null) onWorldChanged.run();
                    }
                }
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    double scale = Math.max(1.0, scaleSupplier.getAsDouble());
                    mouseX = (int)Math.round(e.getX() / scale);
                    mouseY = (int)Math.round(e.getY() / scale);
                }
            }
            @Override
            public void mouseMoved(MouseEvent e) {
                double scale = Math.max(1.0, scaleSupplier.getAsDouble());
                mouseX = (int)Math.round(e.getX() / scale);
                mouseY = (int)Math.round(e.getY() / scale);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    leftDown = false;
                    resetBreakState();
                }
            }
        };
        superficie.addMouseListener(adapter);
        superficie.addMouseMotionListener(adapter);
    }

    private void resetBreakState() {
        holdTimeSeconds = 0.0;
        targetTileX = Integer.MIN_VALUE;
        targetTileY = Integer.MIN_VALUE;
        currentDureza = 0.0;
    }

    private boolean isTileInteractable(int tileX, int tileY, Rectangle2D pb, double size, Mundo world) {
        // tileY is screen-based (top-down). Convert player bounds to the same coordinate system.
        int pMinX = (int) Math.floor(pb.getX() / size);
        int pMinY = (int) Math.floor(pb.getY() / size);
        double eps = 1e-6;
        int pMaxX = (int) Math.floor((pb.getX() + pb.getWidth() - eps) / size);
        int pMaxY = (int) Math.floor((pb.getY() + pb.getHeight() - eps) / size);

        // Define interaction area in screen-based tile coordinates
        int reach = 4; // Player reach in blocks
        int areaMinX = pMinX - reach;
        int areaMaxX = pMaxX + reach;
        int areaMinY = pMinY - reach;
        int areaMaxY = pMaxY + reach;

        // Check if the target tile is within the interaction area
        if (tileX < areaMinX || tileX > areaMaxX || tileY < areaMinY || tileY > areaMaxY) {
            return false;
        }

        // Prevent interaction with blocks the player is inside
        if (tileX >= pMinX && tileX <= pMaxX && tileY >= pMinY && tileY <= pMaxY) {
            return false;
        }

        return hasLineOfSight(tileX, tileY, pMinX, pMaxX, pMinY, pMaxY, world);
    }

    private boolean hasLineOfSight(int tileX, int tileY, int pMinX, int pMaxX, int pMinY, int pMaxY, Mundo world) {
        int originX = clamp(tileX, pMinX, pMaxX);
        int originY = clamp(tileY, pMinY, pMaxY);
        return pathClearHV(originX, originY, tileX, tileY, world) || pathClearVH(originX, originY, tileX, tileY, world);
    }

    private boolean pathClearHV(int x0, int y0, int x1, int y1, Mundo world) {
        return segmentClearHorizontal(x0, y0, x1, world) && segmentClearVertical(x1, y0, y1, world);
    }

    private boolean pathClearVH(int x0, int y0, int x1, int y1, Mundo world) {
        return segmentClearVertical(x0, y0, y1, world) && segmentClearHorizontal(x0, y1, x1, world);
    }

    private int clamp(int v, int min, int max) { return Math.min(Math.max(v, min), max); }

    private boolean segmentClearHorizontal(int x0, int y, int x1, Mundo world) {
        int dir = Integer.compare(x1, x0);
        for (int x = x0 + dir; x != x1; x += dir) {
            if (!isAir(x, y, world)) return false;
        }
        return true;
    }

    private boolean segmentClearVertical(int x, int y0, int y1, Mundo world) {
        int dir = Integer.compare(y1, y0);
        for (int y = y0 + dir; y != y1; y += dir) {
            if (!isAir(x, y, world)) return false;
        }
        return true;
    }

    private boolean isAir(int tileX, int tileY, Mundo world) {
        int worldY = (Mundo.WORLD_HEIGHT_BLOCKS - 1) - tileY;
        BasicBlock b = world.getBlockAtTile(tileX, worldY);
        return b == null || "water".equals(b.getId());
    }

    // --- Getters para feedback visual ---
    public int getTargetTileX() { return targetTileX; }
    public int getTargetTileY() { return targetTileY; }
    public double getBreakProgress() {
        if (currentDureza <= 0) return 0.0;
        return Math.min(1.0, holdTimeSeconds / currentDureza);
    }
    public boolean isBreaking() {
        if (!leftDown || currentDureza <= 0 || holdTimeSeconds <= 0) return false;
        if (targetTileX == Integer.MIN_VALUE || targetTileY == Integer.MIN_VALUE) return false;
        int worldBlockY = (Mundo.WORLD_HEIGHT_BLOCKS - 1) - targetTileY;
        BasicBlock b = mundo.getBlockAtTile(targetTileX, worldBlockY);
        return b != null && b.isBreakable();
    }
    public int getHoverTileX() { return hoverTileX; }
    public int getHoverTileY() { return hoverTileY; }
    public boolean isHoveringInteractable() { return hoverTileX != Integer.MIN_VALUE && hoverTileY != Integer.MIN_VALUE; }
    public boolean hoverHasBlock() { return hoverHasBlock; }

    private void loop() {
        long last = System.nanoTime();
        while (running) {
            if (isPausedSupplier.getAsBoolean()) {
                leftDown = false;
                resetBreakState();
                hoverTileX = Integer.MIN_VALUE;
                hoverTileY = Integer.MIN_VALUE;
                hoverHasBlock = false;
                awaitIfPaused.run();
                last = System.nanoTime();
                continue;
            }

            long now = System.nanoTime();
            double dt = (now - last) / 1_000_000_000.0;
            last = now;

            final double size = BasicBlock.getSize();
            Mundo localMundo = mundo;

            if (localMundo != null) {
                double worldXHover = camara.getX() + mouseX;
                double worldYHover = camara.getY() + mouseY;
                int htx = (int)Math.floor(worldXHover / size);
                int hty = (int)Math.floor(worldYHover / size);
                int htyWorld = (Mundo.WORLD_HEIGHT_BLOCKS - 1) - hty;

                Rectangle2D pbHover = jugador.getBounds();
                if (isTileInteractable(htx, hty, pbHover, size, localMundo)) {
                    hoverTileX = htx;
                    hoverTileY = hty;
                    hoverHasBlock = localMundo.getBlockAtTile(htx, htyWorld) != null;
                } else {
                    hoverTileX = Integer.MIN_VALUE;
                    hoverTileY = Integer.MIN_VALUE;
                    hoverHasBlock = false;
                }

                if (leftDown) {
                    double worldX = camara.getX() + mouseX;
                    double worldY = camara.getY() + mouseY;
                    int tileX = (int) Math.floor(worldX / size);
                    int tileY = (int) Math.floor(worldY / size);
                    int worldBlockYLoop = (Mundo.WORLD_HEIGHT_BLOCKS - 1) - tileY;

                    Rectangle2D pb = jugador.getBounds();
                    if (!isTileInteractable(tileX, tileY, pb, size, localMundo)) {
                        resetBreakState();
                    } else {
                        BasicBlock b = localMundo.getBlockAtTile(tileX, worldBlockYLoop);
                        if (b != null && !b.isBreakable()) {
                            leftDown = false;
                            resetBreakState();
                        } else if (b == null) {
                            resetBreakState();
                        } else {
                            if (tileX != targetTileX || tileY != targetTileY) {
                                targetTileX = tileX;
                                targetTileY = tileY;
                                holdTimeSeconds = 0.0;
                                currentDureza = b.getDureza();
                            }
                            holdTimeSeconds += dt;
                            if (holdTimeSeconds >= currentDureza) {
                                localMundo.setBlockAtTile(tileX, worldBlockYLoop, null);
                                localMundo.markChunkDirty(tileX, worldBlockYLoop); // Mark for lighting update
                                resetBreakState();
                                if (hoverTileX == tileX && hoverTileY == tileY) hoverHasBlock = false;
                                if (onWorldChanged != null) onWorldChanged.run();
                            }
                        }
                    }
                } else {
                    resetBreakState();
                }
            } else {
                hoverTileX = Integer.MIN_VALUE;
                hoverTileY = Integer.MIN_VALUE;
                hoverHasBlock = false;
                resetBreakState();
            }

            try { Thread.sleep(10); } catch (InterruptedException e) { if (!running) break; }
        }
    }
}
