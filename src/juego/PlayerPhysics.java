package juego;

import juego.bloques.BasicBlock;
import juego.bloques.BlockType;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * Manages player physics, including movement, gravity, and collision detection.
 */
public class PlayerPhysics {

    // Physics Constants
    private static final double VX_ACEL = 1200;
    private static final double VX_FRICTION = 1100;
    private static final double VX_MAX = 300;
    private static final double NOCLIP_SPEED = 1200;
    private static final double GRAVEDAD = 1800;
    private static final double VY_SALTO = 550;
    private static final double VY_MAX_CAIDA = 1200;
    private static final double WATER_GRAVITY = 400;
    private static final double WATER_UP_ACCEL = 900;
    private static final double WATER_DOWN_ACCEL = 300;
    private static final double SWIM_UP_SPEED = 150;
    private static final double SWIM_DOWN_SPEED = 150;
    private static final double WATER_VX_ACEL = 450;
    private static final double WATER_VX_FRICTION = 1300;
    private static final double WATER_VX_MAX = 140;
    private static final double WATER_STICKY_TIME = 0.15;
    private static final double WATER_DETECT_TOL = 4.0; // Simplified tolerance
    private static final double GROUND_EPS = 3.0;
    private static final double JUMP_BUFFER_TIME = 0.20;
    private static final double COYOTE_TIME = 0.10;

    // State
    private double vx;
    private double vy;
    private double aguaStickyTimer = 0.0;
    private double jumpBufferTimer = 0.0;
    private double coyoteTimer = 0.0;
    private boolean enSuelo;

    public void update(Jugador player, componentes.Input input, double dt, List<BasicBlock> bloques) {
        boolean noclip = input.isNoclipActive();
        boolean left = input.isKeyA();
        boolean right = input.isKeyD();

        if (noclip) {
            updateNoclip(player, input, dt, left, right);
            return;
        }

        boolean enAgua = checkWater(player, bloques);
        boolean soportadoPre = isSupportedBySolid(player, bloques);

        updateTimers(dt, enAgua, soportadoPre, enSuelo, input.consumeSpacePressedOnce());
        
        updateHorizontalMovement(dt, left, right, enAgua, soportadoPre);
        updateVerticalMovement(dt, input, enAgua, soportadoPre);

        // Integration and Collision
        integrateAndCollide(player, dt, bloques);

        // Post-integration checks
        boolean soportadoPost = isSupportedBySolid(player, bloques);
        if (soportadoPost) {
            snapToGround(player, bloques);
            enSuelo = true;
            vy = 0;
            aguaStickyTimer = 0;
        }
    }

    private void updateNoclip(Jugador player, componentes.Input input, double dt, boolean left, boolean right) {
        if (left && !right) vx = -NOCLIP_SPEED; else if (right && !left) vx = NOCLIP_SPEED; else vx = 0;
        
        if (input.isKeySpace() && !input.isKeyShift()) {
            vy = -NOCLIP_SPEED;
        } else if (input.isKeyShift() && !input.isKeySpace()) {
            vy = NOCLIP_SPEED;
        } else {
            vy = 0;
        }
        
        player.setX(player.getX() + vx * dt);
        player.setY(player.getY() + vy * dt);
    }

    private boolean checkWater(Jugador player, List<BasicBlock> bloques) {
        Rectangle2D pbActual = player.getBounds();
        Rectangle2D piesJugador = new Rectangle2D.Double(pbActual.getX(), pbActual.getY() + pbActual.getHeight() * 0.4,
                pbActual.getWidth(), pbActual.getHeight() * 0.6);
        
        for (BasicBlock b : bloques) {
            if (b.getType() == BlockType.WATER) {
                Rectangle2D wb = b.getBounds();
                Rectangle2D wbDetect = new Rectangle2D.Double(wb.getX(), wb.getY() - WATER_DETECT_TOL, wb.getWidth(), wb.getHeight() + WATER_DETECT_TOL);
                if (piesJugador.intersects(wbDetect)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void updateTimers(double dt, boolean enAgua, boolean soportadoPre, boolean wasEnSuelo, boolean spacePressed) {
        // Sticky water
        if (enAgua && !soportadoPre) {
            aguaStickyTimer = WATER_STICKY_TIME;
        } else if (aguaStickyTimer > 0) {
            aguaStickyTimer -= dt;
            if (aguaStickyTimer < 0) aguaStickyTimer = 0;
        }

        // Coyote
        if (wasEnSuelo) {
            coyoteTimer = COYOTE_TIME;
        } else {
            coyoteTimer -= dt;
            if (coyoteTimer < 0) coyoteTimer = 0;
        }

        // Jump Buffer
        if (spacePressed) {
            jumpBufferTimer = JUMP_BUFFER_TIME;
        } else if (jumpBufferTimer > 0) {
            jumpBufferTimer -= dt;
            if (jumpBufferTimer < 0) jumpBufferTimer = 0;
        }
    }

    private void updateHorizontalMovement(double dt, boolean left, boolean right, boolean enAgua, boolean soportadoPre) {
        boolean usarAguaHorizontal = (enAgua || aguaStickyTimer > 0) && !soportadoPre;
        double acel = usarAguaHorizontal ? WATER_VX_ACEL : VX_ACEL;
        double fric = usarAguaHorizontal ? WATER_VX_FRICTION : VX_FRICTION;
        double vmax = usarAguaHorizontal ? WATER_VX_MAX : VX_MAX;

        if (left && !right) {
            vx -= acel * dt;
        } else if (right && !left) {
            vx += acel * dt;
        } else {
            if (vx > 0) {
                vx -= fric * dt;
                if (vx < 0) vx = 0;
            } else if (vx < 0) {
                vx += fric * dt;
                if (vx > 0) vx = 0;
            }
        }

        if (vx > vmax) vx = vmax;
        if (vx < -vmax) vx = -vmax;
    }

    private void updateVerticalMovement(double dt, componentes.Input input, boolean enAgua, boolean soportadoPre) {
        if (!enAgua || soportadoPre) {
            if (jumpBufferTimer > 0 && (enSuelo || coyoteTimer > 0 || soportadoPre)) {
                vy = -VY_SALTO;
                enSuelo = false;
                jumpBufferTimer = 0;
                coyoteTimer = 0;
            }
            if (!enSuelo) {
                vy += GRAVEDAD * dt;
                if (vy > VY_MAX_CAIDA) vy = VY_MAX_CAIDA;
            }
        } else {
            jumpBufferTimer = 0;
            if (input.isKeySpace()) {
                vy -= WATER_UP_ACCEL * dt;
                if (vy < -SWIM_UP_SPEED) vy = -SWIM_UP_SPEED;
            } else {
                vy += Math.max(WATER_GRAVITY, WATER_DOWN_ACCEL) * dt;
                if (vy > SWIM_DOWN_SPEED) vy = SWIM_DOWN_SPEED;
            }
        }
    }

    private void integrateAndCollide(Jugador player, double dt, List<BasicBlock> bloques) {
        // X Axis
        double nuevoX = player.getX() + vx * dt;
        Rectangle2D futuroX = new Rectangle2D.Double(nuevoX, player.getY(), player.getAnchoPx(), player.getAltoPx());
        
        for (BasicBlock b : bloques) {
            if (b.getType() == BlockType.WATER) continue;
            if (futuroX.intersects(b.getBounds())) {
                Rectangle2D br = b.getBounds();
                if (vx > 0) {
                    nuevoX = br.getX() - player.getAnchoPx();
                } else if (vx < 0) {
                    nuevoX = br.getX() + br.getWidth();
                }
                vx = 0;
                break;
            }
        }
        player.setX(nuevoX);

        // Y Axis
        double nuevoY = player.getY() + vy * dt;
        Rectangle2D futuroY = new Rectangle2D.Double(player.getX(), nuevoY, player.getAnchoPx(), player.getAltoPx());
        enSuelo = false;
        
        for (BasicBlock b : bloques) {
            if (b.getType() == BlockType.WATER) continue;
            if (futuroY.intersects(b.getBounds())) {
                Rectangle2D br = b.getBounds();
                if (vy < 0) {
                    nuevoY = br.getY() + br.getHeight();
                    vy = 0;
                } else if (vy > 0) {
                    nuevoY = br.getY() - player.getAltoPx();
                    vy = 0;
                    enSuelo = true;
                }
                break;
            }
        }
        player.setY(nuevoY);
    }

    private boolean isSupportedBySolid(Jugador player, List<BasicBlock> bloques) {
        double feet = player.getY() + player.getAltoPx();
        for (BasicBlock b : bloques) {
            if (b.getType() == BlockType.WATER) continue;
            Rectangle2D br = b.getBounds();
            boolean overlapX = (player.getX() + player.getAnchoPx()) > br.getX() + 0.001 && player.getX() < (br.getX() + br.getWidth()) - 0.001;
            if (!overlapX) continue;
            double top = br.getY();
            if (feet >= top - GROUND_EPS && feet <= top + GROUND_EPS) {
                return true;
            }
        }
        return false;
    }

    private void snapToGround(Jugador player, List<BasicBlock> bloques) {
        double feet = player.getY() + player.getAltoPx();
        Double bestTop = null;
        double bestAbs = GROUND_EPS + 1;
        
        for (BasicBlock b : bloques) {
            if (b.getType() == BlockType.WATER) continue;
            Rectangle2D br = b.getBounds();
            boolean overlapX = (player.getX() + player.getAnchoPx()) > br.getX() + 0.001 && player.getX() < (br.getX() + br.getWidth()) - 0.001;
            if (!overlapX) continue;
            double top = br.getY();
            double diff = Math.abs(feet - top);
            if (diff <= GROUND_EPS && diff < bestAbs) {
                bestAbs = diff;
                bestTop = top;
            }
        }
        
        if (bestTop != null) {
            player.setY(bestTop - player.getAltoPx());
        }
    }
}
