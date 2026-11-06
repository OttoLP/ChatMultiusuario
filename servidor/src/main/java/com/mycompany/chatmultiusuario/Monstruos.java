package com.mycompany.chatmultiusuario;

import java.awt.Rectangle;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

final class Monstruos {
    private static final int TILE = 48;
    private static final int CUERPO = 36;
    private static final int PASO = 5;
    private final int[][] mapa = new int[50][50];
    private final Rectangle[] grupo = new Rectangle[20];

    Monstruos() {
        InputStream recurso = getClass().getResourceAsStream("/escenarios/mundo.txt");
        if (recurso == null) throw new IllegalStateException("Falta escenarios/mundo.txt");
        try (Scanner lector = new Scanner(recurso, StandardCharsets.UTF_8)) {
            for (int[] fila : mapa) {
                for (int col = 0; col < fila.length; col++) fila[col] = lector.nextInt();
            }
        }
        reiniciar();
    }

    void reiniciar() {
        for (int i = 0; i < grupo.length; i++) {
            grupo[i] = cuerpo((10 + i * 2) * TILE, 2 * TILE);
        }
    }

    boolean update(Mensaje[] jugadores) {
        boolean cambio = false;
        for (Rectangle monstruo : grupo) {
            Mensaje objetivo = null;
            int menorDistancia = Integer.MAX_VALUE;
            for (Mensaje jugador : jugadores) {
                if (jugador == null) continue;
                int dx = jugador.x - monstruo.x;
                int dy = jugador.y - monstruo.y;
                int distancia = dx * dx + dy * dy;
                if (distancia < menorDistancia) {
                    menorDistancia = distancia;
                    objetivo = jugador;
                }
            }
            if (objetivo == null) continue;
            int dx = Math.max(-PASO, Math.min(PASO, objetivo.x - monstruo.x));
            int dy = Math.max(-PASO, Math.min(PASO, objetivo.y - monstruo.y));
            cambio |= mover(monstruo, dx, 0, jugadores);
            cambio |= mover(monstruo, 0, dy, jugadores);
        }
        return cambio;
    }

    private boolean mover(Rectangle monstruo, int dx, int dy, Mensaje[] jugadores) {
        if (dx == 0 && dy == 0) return false;
        Rectangle siguiente = cuerpo(monstruo.x + dx, monstruo.y + dy);
        for (int fila = siguiente.y / TILE; fila <= (siguiente.y + CUERPO - 1) / TILE; fila++) {
            for (int col = siguiente.x / TILE; col <= (siguiente.x + CUERPO - 1) / TILE; col++) {
                int tile = mapa[fila][col];
                if (tile == 1 || tile == 3) return false;
            }
        }
        for (Mensaje jugador : jugadores) {
            if (jugador != null && siguiente.intersects(cuerpo(jugador.x, jugador.y))) return false;
        }
        for (Rectangle otro : grupo) {
            if (otro != monstruo && siguiente.intersects(otro)) return false;
        }
        monstruo.setLocation(siguiente.x, siguiente.y);
        return true;
    }

    boolean choca(int x, int y) {
        Rectangle jugador = cuerpo(x, y);
        for (Rectangle monstruo : grupo) {
            if (monstruo.intersects(jugador)) return true;
        }
        return false;
    }

    Mensaje[] posiciones() {
        Mensaje[] posiciones = new Mensaje[grupo.length];
        for (int i = 0; i < grupo.length; i++) {
            Mensaje posicion = new Mensaje();
            posicion.x = grupo[i].x;
            posicion.y = grupo[i].y;
            posiciones[i] = posicion;
        }
        return posiciones;
    }

    private static Rectangle cuerpo(int x, int y) {
        return new Rectangle(x, y, CUERPO, CUERPO);
    }
}
