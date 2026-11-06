package entidad;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.awt.Rectangle;

import javax.imageio.ImageIO;

import main.GamePanel;
import main.KeyManager2;

public class Jugador extends Entidad {
    private GamePanel gp;
    private KeyManager2 mT;
    private int id;
    private String usuario;
    private final int xPantalla, yPantalla;

    public Jugador(GamePanel gp, KeyManager2 mT, int id, String usuario){
        this.gp = gp;
        this.mT = mT;
        this.id = id;
        this.usuario = usuario;
        this.xPantalla = gp.getWidthScreen() / 2 - (gp.getTileSize() / 2);
        this.yPantalla = gp.getHeightScreen() / 2 - (gp.getTileSize() / 2);
        this.hitbox = new Rectangle(8, 16, 32, 32);
        configuracionInicial();
        getSpritesJugador();
    }

    public void configuracionInicial(){
        this.x = gp.getWidthScreen() / 2;
        this.y = gp.getHeightScreen() / 2;
        this.velocidad = 4;
        this.setDireccion("abajo");
    }

    public void getSpritesJugador(){
        try{
            this.arriba1 = colorear(ImageIO.read(getClass().getResourceAsStream("/spritesjugador/moverArriba1.png")));
            this.arriba2 = colorear(ImageIO.read(getClass().getResourceAsStream("/spritesjugador/moverArriba2.png")));
            this.abajo1 = colorear(ImageIO.read(getClass().getResourceAsStream("/spritesjugador/moverAbajo1.png")));
            this.abajo2 = colorear(ImageIO.read(getClass().getResourceAsStream("/spritesjugador/moverAbajo2.png")));
            this.izquierda1 = colorear(ImageIO.read(getClass().getResourceAsStream("/spritesjugador/moverIzquierda1.png")));
            this.izquierda2 = colorear(ImageIO.read(getClass().getResourceAsStream("/spritesjugador/moverIzquierda2.png")));
            this.derecha1 = colorear(ImageIO.read(getClass().getResourceAsStream("/spritesjugador/moverDerecha1.png")));
            this.derecha2 = colorear(ImageIO.read(getClass().getResourceAsStream("/spritesjugador/moverDerecha2.png")));
        }catch(IOException e){
            throw new IllegalStateException("No se pudieron cargar los sprites", e);
        }
    }

    private BufferedImage colorear(BufferedImage imagen) {

        
        Color[] colores = {new Color(237, 28, 36), new Color(40, 110, 235),
                new Color(35, 180, 70), new Color(245, 205, 35)};
        Color color = colores[id];
        for (int fila = 7; fila <= 12; fila++) {
            for (int col = 0; col < imagen.getWidth(); col++) {
                int pixel = imagen.getRGB(col, fila);
                boolean claro = pixel == 0xFFED1C24;
                boolean oscuro = pixel == 0xFF880015;
                // En las vistas de espalda, fila 7 aun tiene pelo en el centro.
                if (fila == 7 && col >= 4 && col <= 11) continue;
                if (claro || oscuro) {
                    imagen.setRGB(col, fila, (oscuro ? color.darker() : color).getRGB());
                }
            }
        }
        return imagen;
    }

    // Los otros jugadores deducen direccion y animacion a partir del desplazamiento.
    public void recibirPosicion(int nuevoX, int nuevoY) {
        if (nuevoY < y) setDireccion("arriba");
        else if (nuevoY > y) setDireccion("abajo");
        else if (nuevoX < x) setDireccion("izquierda");
        else if (nuevoX > x) setDireccion("derecha");
        if (nuevoX != x || nuevoY != y) {
            contadorSprites++;
            if (contadorSprites > cambiaSprite) {
                numeroSprites = numeroSprites == 1 ? 2 : 1;
                contadorSprites = 0;
            }
        }
        x = nuevoX;
        y = nuevoY;
    }

    public void update(){
        boolean moviendo = false;

        if (mT.getKeyUp()) {
            setDireccion("arriba"); moviendo = true;
        } else if (mT.getKeyDown()) {
            setDireccion("abajo"); moviendo = true;
        } else if (mT.getKeyLeft()) {
            setDireccion("izquierda"); moviendo = true;
        } else if (mT.getKeyRight()) {
            setDireccion("derecha"); moviendo = true;
        }

        if (moviendo) {
            this.colisionActivada = false;
            gp.getDetectorColisiones().revisaTile(this);

            if (!this.colisionActivada) {
                switch (this.direccion) {
                    case "arriba":    setY(getY() - getVelocidad()); break;
                    case "abajo":     setY(getY() + getVelocidad()); break;
                    case "izquierda": setX(getX() - getVelocidad()); break;
                    case "derecha":   setX(getX() + getVelocidad()); break;
                }
            }
            contadorSprites++;
        }

        x = Math.max(0, Math.min(x, gp.getWidthWorld() - gp.getTileSize()));
        y = Math.max(0, Math.min(y, gp.getHeightWorld() - gp.getTileSize()));

        if (contadorSprites > cambiaSprite) {
            numeroSprites = (numeroSprites == 1) ? 2 : 1;
            contadorSprites = 0;
        }
    }

    public void draw(Graphics2D g2, int camaraX, int camaraY){
        BufferedImage sprite = null;
        switch (this.getDireccion()) {
            case "arriba":
                if(this.numeroSprites == 1)
                    sprite = this.arriba1;
                if (this.numeroSprites == 2)
                    sprite = this.arriba2;
            break;
            case "abajo":
                if(this.numeroSprites == 1)
                    sprite = this.abajo1;
                if (this.numeroSprites == 2)
                    sprite = this.abajo2;
            break;
            case "izquierda":
                if(this.numeroSprites == 1)
                    sprite = this.izquierda1;
                if (this.numeroSprites == 2)
                    sprite = this.izquierda2;
            break;
            case "derecha":
                if(this.numeroSprites == 1)
                    sprite = this.derecha1;
                if (this.numeroSprites == 2)
                    sprite = this.derecha2;
            break;
        }
        int pantallaX = getX() - camaraX;
        int pantallaY = getY() - camaraY;
        g2.drawImage(sprite, pantallaX, pantallaY, gp.getTileSize(), gp.getTileSize(), null);
        
        /* 
        g2.setColor(Color.BLACK);
        g2.drawString(usuario, x + 1, Math.max(13, y - 3) + 1);
        g2.setColor(Color.WHITE);
        g2.drawString(usuario, x, Math.max(13, y - 3));
        */
    }

    // Setters
    public void setX(int valor){
        this.x = valor;
    }
    public void setY(int valor){ this.y = valor; }

    // Getters
    public int getX(){
        return this.x;
    }
    public int getY(){ return this.y; }
    public String getUsuario() { return usuario; }
    public int getVelocidad(){
        return this.velocidad;
    }
    public int getPantallaX(){ return this.xPantalla; }
    public int getPantallaY(){ return this.yPantalla; }
    public int getAreaSolidaAncho(){ return this.hitbox.width; }
    public int getAreaSolidaAlto(){ return this.hitbox.height; }
    public int getMundoX(){ return this.x; }
    public int getMundoY(){ return this.y; }
    public int getAreaSolidaX(){ return this.hitbox.x; }
    public int getAreaSolidaY(){ return this.hitbox.y; }
}
