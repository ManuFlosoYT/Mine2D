package tipos;

/**
 * Punto en 2D con coordenadas en píxeles del mundo.
 *
 * <p>Se utiliza para posicionar entidades y bloques dentro del mundo.
 * Las coordenadas siguen el sistema con origen en la esquina superior izquierda
 * de la pantalla, aumentando X hacia la derecha y Y hacia abajo.</p>
 */
public record Punto(double x, double y) {
}
