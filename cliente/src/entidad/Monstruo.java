package entidad;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import red.Mensaje;

public final class Monstruo {
    // Mismo tamano de colision que usa el servidor.
    private static final int CUERPO = 36;
    private final BufferedImage sprite;

    public Monstruo() {
        try (var recurso = getClass().getResourceAsStream("/spritesmonstruo/monstruo.png")) {
            if (recurso == null) throw new IOException("Falta monstruo.png");
            sprite = ImageIO.read(recurso);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo cargar el monstruo", e);
        }
    }

    public boolean choca(int x, int y, Mensaje[] posiciones) {
        if (posiciones == null) return false;
        var jugador = new Rectangle2D.Double(x, y, CUERPO, CUERPO);
        for (Mensaje monstruo : posiciones) {
            if (jugador.intersects(monstruo.x, monstruo.y, CUERPO, CUERPO)) return true;
        }
        return false;
    }

    public void draw(Graphics2D g2, Mensaje[] posiciones, int camaraX, int camaraY, int size) {
        if (posiciones == null) return;
        for (Mensaje monstruo : posiciones) {
            g2.drawImage(sprite, monstruo.x - camaraX, monstruo.y - camaraY, size, size, null);
        }
    }
}
