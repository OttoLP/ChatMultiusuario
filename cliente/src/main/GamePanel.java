package main;

import javax.swing.*;
import entidad.Jugador;
import entidad.Monstruo;
import tile.ManejadorTiles;
import red.Cliente;
import red.Mensaje;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

public class GamePanel extends JPanel implements Runnable {
    final int originalTileSize = 16;
    final int scale = 3;
    final int tileSize = originalTileSize * scale;
    final int worldCols = 50;
    final int worldRows = 50;
    final int maxCols = 26;
    final int maxRows = 15;
    final int widthScreen = tileSize * maxCols;
    final int heightScreen = tileSize * maxRows;
    volatile Thread gameThread;
    KeyManager2 kM = new KeyManager2();
    Jugador[] jugadores = new Jugador[4];
    private final Monstruo monstruo = new Monstruo();
    private Mensaje[] monstruos;
    ManejadorTiles mTi = new ManejadorTiles(this);
    DetectorColisiones dC = new DetectorColisiones(this);
    Cliente cliente;
    int FPS = 60;

    public GamePanel(Cliente cliente) {
        this.cliente = cliente;
        int id = cliente.getId();
        jugadores[id] = new Jugador(this, kM, id, cliente.getUsuario());
        jugadores[id].setX(cliente.getX());
        jugadores[id].setY(cliente.getY());
        setPreferredSize(new Dimension(widthScreen, heightScreen));
        setBackground(Color.BLACK);
        setDoubleBuffered(true);
        addKeyListener(kM);
        addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { kM.soltarTeclas(); }
        });
        setFocusable(true);
    }

    public void startGameThread() {
        gameThread = new Thread(this, "juego");
        gameThread.start();
    }

    @Override
    public void run() {
        while (gameThread != null && cliente.estaConectado()) {
            update();
            repaint();
            try { Thread.sleep(1000 / FPS); }
            catch (InterruptedException e) { break; }
        }
        gameThread = null;
        repaint();
    }

    public void detener() {
        gameThread = null;
        cliente.cerrar();
    }

    public synchronized void update() {
        Mensaje mensaje = cliente.getEstado();
        Mensaje[] estado = mensaje.jugadores;
        if (estado == null) return;
        monstruos = mensaje.monstruos;
        for (int i = 0; i < jugadores.length; i++) {
            if (i == cliente.getId()) continue;
            if (estado[i] == null) {
                jugadores[i] = null;
            } else {
                if (jugadores[i] == null || !jugadores[i].getUsuario().equals(estado[i].usuario)) {
                    jugadores[i] = new Jugador(this, null, i, estado[i].usuario);
                    jugadores[i].setX(estado[i].x);
                    jugadores[i].setY(estado[i].y);
                } else {
                    jugadores[i].recibirPosicion(estado[i].x, estado[i].y);
                }
                if (estado[i].vida != null && estado[i].vidaMax != null) {
                    jugadores[i].setVida(estado[i].vida, estado[i].vidaMax);
                }
            }
        }
        Jugador local = jugadores[cliente.getId()];
        Mensaje yo = estado[cliente.getId()];
        if (yo != null && yo.vida != null && yo.vidaMax != null) {
            local.setVida(yo.vida, yo.vidaMax);
        }
        if (yo != null && yo.nivelDanio != null && yo.nivelVelocidad != null && yo.velocidad != null) {
            local.setMejoras(yo.nivelDanio, yo.nivelVelocidad, yo.velocidad);
        }
        Mensaje correccion = cliente.tomarCorreccion();
        if (correccion != null) {
            local.setX(correccion.x);
            local.setY(correccion.y);
        }
        if (local.isMuerto()) return; // un jugador caido ya no se mueve
        int antesX = local.getX(), antesY = local.getY();
        local.update();
        if (monstruo.choca(local.getX(), local.getY(), monstruos)) {
            local.setX(antesX);
            local.setY(antesY);
        }
        if (antesX != local.getX() || antesY != local.getY()) {
            cliente.enviarPosicion(local.getX(), local.getY());
        }
    }

    @Override
    public synchronized void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        Jugador local = jugadores[cliente.getId()];
        int camaraX = Math.max(0, Math.min(local.getX() - local.getPantallaX(),
                getWidthWorld() - widthScreen));
        int camaraY = Math.max(0, Math.min(local.getY() - local.getPantallaY(),
                getHeightWorld() - heightScreen));
        mTi.draw(g2, camaraX, camaraY);
        monstruo.draw(g2, monstruos, camaraX, camaraY, tileSize);
        dibujaDisparos(g2, camaraX, camaraY);
        for (Jugador jugador : jugadores) {
            if (jugador != null) jugador.draw(g2, camaraX, camaraY);
        }
        dibujaInterfaz(g2, local);
        if (!cliente.estaConectado()) {
            g2.setColor(new Color(0, 0, 0, 190));
            g2.fillRect(0, 0, widthScreen, 50);
            g2.setColor(Color.WHITE);
            g2.drawString(cliente.getError() + " Cierra y vuelve a abrir el cliente.", 20, 30);
        }
        g2.dispose();
    }

    // Linea breve desde el jugador que ataca hasta su objetivo.
    private void dibujaDisparos(Graphics2D g2, int camaraX, int camaraY) {
        int mitad = tileSize / 2;
        g2.setColor(new Color(255, 245, 200, 220));
        g2.setStroke(new BasicStroke(3));
        for (Cliente.Disparo disparo : cliente.getDisparos()) {
            if (disparo.jugador() < 0 || disparo.jugador() >= jugadores.length) continue;
            Jugador origen = jugadores[disparo.jugador()];
            if (origen == null) continue;
            g2.drawLine(origen.getX() + mitad - camaraX, origen.getY() + mitad - camaraY,
                    disparo.x() + mitad - camaraX, disparo.y() + mitad - camaraY);
        }
        g2.setStroke(new BasicStroke(1));
    }

    // Barra de vida del jugador local y avisos de caida o fin de partida.
    private void dibujaInterfaz(Graphics2D g2, Jugador local) {
        int ancho = 220, alto = 22, margen = 16;
        int vida = Math.max(0, local.getVida());
        g2.setColor(new Color(20, 20, 20, 200));
        g2.fillRect(margen - 2, margen - 2, ancho + 4, alto + 4);
        g2.setColor(new Color(190, 40, 40));
        g2.fillRect(margen, margen, ancho * vida / Math.max(1, local.getVidaMax()), alto);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2.drawString("VIDA  " + vida + " / " + local.getVidaMax(), margen + 8, margen + 16);
        // Niveles de mejora, debajo de la barra de vida (\u00D1 = la letra enie).
        g2.drawString("DA\u00D1O  " + local.getNivelDanio() + "     VELOCIDAD  " + local.getNivelVelocidad(),
                margen, margen + alto + 22);
        Mensaje estado = cliente.getEstado();
        dibujaOleada(g2, estado);
        Mensaje[] jugadoresEstado = estado.jugadores;
        Mensaje yo = jugadoresEstado == null ? null : jugadoresEstado[cliente.getId()];
        if (estado.descanso != null && yo != null && yo.contenido != null
                && !Boolean.TRUE.equals(estado.fin) && !local.isMuerto()) {
            dibujaAviso(g2, yo.contenido);
        }
        if (Boolean.TRUE.equals(estado.fin)) {
            int oleada = estado.oleada == null ? 0 : estado.oleada;
            mensajeCentral(g2, "FIN DEL JUEGO", oleada > 0
                    ? "Llegaron a la oleada " + oleada + ". Reiniciando..."
                    : "Todos cayeron. Reiniciando...");
        } else if (local.isMuerto()) {
            mensajeCentral(g2, "HAS CAIDO", "La partida se reinicia cuando todos caigan.");
        }
    }

    // Numero de oleada y enemigos que faltan, o cuenta regresiva del descanso (arriba al centro).
    private void dibujaOleada(Graphics2D g2, Mensaje estado) {
        int oleada = estado.oleada == null ? 0 : estado.oleada;
        String texto;
        if (estado.descanso != null) {
            texto = oleada == 0
                    ? "Preparense: la oleada 1 empieza en " + estado.descanso + " s"
                    : "Oleada " + oleada + " superada. La siguiente empieza en " + estado.descanso + " s";
        } else if (estado.restantes != null) {
            texto = "OLEADA " + oleada + "   -   quedan " + estado.restantes;
        } else {
            return;
        }
        g2.setFont(new Font("SansSerif", Font.BOLD, 18));
        int ancho = g2.getFontMetrics().stringWidth(texto) + 24;
        int x = (widthScreen - ancho) / 2;
        g2.setColor(new Color(20, 20, 20, 200));
        g2.fillRect(x, 12, ancho, 30);
        g2.setColor(Color.WHITE);
        g2.drawString(texto, x + 12, 33);
    }

    // Mejora recibida al superar la oleada (solo durante el descanso).
    private void dibujaAviso(Graphics2D g2, String aviso) {
        g2.setFont(new Font("SansSerif", Font.BOLD, 26));
        int ancho = g2.getFontMetrics().stringWidth(aviso) + 40;
        int x = (widthScreen - ancho) / 2;
        g2.setColor(new Color(20, 20, 20, 210));
        g2.fillRect(x, 60, ancho, 44);
        g2.setColor(new Color(255, 245, 200));
        g2.drawString(aviso, x + 20, 90);
    }

    private void mensajeCentral(Graphics2D g2, String titulo, String detalle) {
        g2.setColor(new Color(0, 0, 0, 130));
        g2.fillRect(0, 0, widthScreen, heightScreen);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 48));
        FontMetrics grande = g2.getFontMetrics();
        g2.drawString(titulo, (widthScreen - grande.stringWidth(titulo)) / 2, heightScreen / 2);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 20));
        FontMetrics chico = g2.getFontMetrics();
        g2.drawString(detalle, (widthScreen - chico.stringWidth(detalle)) / 2, heightScreen / 2 + 40);
    }

    // Getters
    public int getTileSize() { return tileSize; }
    public int getWorldCols() { return worldCols; }
    public int getWorldRows() { return worldRows; }
    public int getWidthWorld() { return tileSize * worldCols; }
    public int getHeightWorld() { return tileSize * worldRows; }
    public int getWidthScreen() { return widthScreen; }
    public int getHeightScreen() { return heightScreen; }
    public DetectorColisiones getDetectorColisiones(){ return this.dC; }
}
