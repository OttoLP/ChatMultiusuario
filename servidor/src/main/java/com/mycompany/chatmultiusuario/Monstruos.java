package com.mycompany.chatmultiusuario;

import java.awt.Rectangle;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;

final class Monstruos {
    private static final int TILE = 48;
    private static final int CUERPO = 36;
    private static final int CONTACTO = 8;   // margen alrededor del jugador para contar un golpe
    private static final int PASO = 5;
    private final int[][] mapa = new int[50][50];
    private final Rectangle[] grupo = new Rectangle[20];
    private final int[] vida = new int[grupo.length];        // 0 = muerto
    private final long[] reaparece = new long[grupo.length]; // cuando vuelve un monstruo muerto

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
            vida[i] = Combate.VIDA_MONSTRUO;
            reaparece[i] = 0;
        }
    }

    // jugadores: solo los que estan vivos (los caidos no atraen ni estorban).
    boolean update(Mensaje[] jugadores, long ahora) {
        boolean cambio = false;
        for (int i = 0; i < grupo.length; i++) {
            if (vida[i] <= 0 && ahora >= reaparece[i]) cambio |= reaparecer(i, jugadores, ahora);
        }
        for (int i = 0; i < grupo.length; i++) {
            if (vida[i] <= 0) continue;
            Rectangle monstruo = grupo[i];
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
            cambio |= mover(i, dx, 0, jugadores);
            cambio |= mover(i, 0, dy, jugadores);
        }
        return cambio;
    }

    private boolean mover(int i, int dx, int dy, Mensaje[] jugadores) {
        if (dx == 0 && dy == 0) return false;
        Rectangle monstruo = grupo[i];
        Rectangle siguiente = cuerpo(monstruo.x + dx, monstruo.y + dy);
        for (int fila = siguiente.y / TILE; fila <= (siguiente.y + CUERPO - 1) / TILE; fila++) {
            for (int col = siguiente.x / TILE; col <= (siguiente.x + CUERPO - 1) / TILE; col++) {
                if (bloquea(mapa[fila][col])) return false;
            }
        }
        for (Mensaje jugador : jugadores) {
            if (jugador != null && siguiente.intersects(cuerpo(jugador.x, jugador.y))) return false;
        }
        for (int j = 0; j < grupo.length; j++) {
            if (j != i && vida[j] > 0 && siguiente.intersects(grupo[j])) return false;
        }
        monstruo.setLocation(siguiente.x, siguiente.y);
        return true;
    }

    // Un monstruo muerto vuelve en un lugar libre, lejos de los jugadores vivos.
    // Si no encuentra uno, lo intenta de nuevo en 1 segundo.
    private boolean reaparecer(int i, Mensaje[] jugadores, long ahora) {
        ThreadLocalRandom azar = ThreadLocalRandom.current();
        for (int intento = 0; intento < 40; intento++) {
            int col = azar.nextInt(mapa[0].length);
            int fila = azar.nextInt(mapa.length);
            if (bloquea(mapa[fila][col])) continue;
            Rectangle candidato = cuerpo(col * TILE, fila * TILE);
            if (!lejosDeJugadores(candidato, jugadores) || chocaConMonstruo(candidato, i)) continue;
            grupo[i] = candidato;
            vida[i] = Combate.VIDA_MONSTRUO;
            return true;
        }
        reaparece[i] = ahora + 1000;
        return false;
    }

    private boolean lejosDeJugadores(Rectangle punto, Mensaje[] jugadores) {
        for (Mensaje jugador : jugadores) {
            if (jugador == null) continue;
            if (Math.hypot(jugador.x - punto.x, jugador.y - punto.y) < Combate.DISTANCIA_REAPARICION) return false;
        }
        return true;
    }

    private boolean chocaConMonstruo(Rectangle punto, int excepto) {
        for (int j = 0; j < grupo.length; j++) {
            if (j != excepto && vida[j] > 0 && punto.intersects(grupo[j])) return true;
        }
        return false;
    }

    // Arbol (1) y muro (3) bloquean el paso, igual que en el cliente.
    private static boolean bloquea(int tile) {
        return tile == 1 || tile == 3;
    }

    // Un monstruo vivo ocupa el lugar del jugador (se usa para no dejarlo entrar en el).
    boolean choca(int x, int y) {
        Rectangle jugador = cuerpo(x, y);
        for (int i = 0; i < grupo.length; i++) {
            if (vida[i] > 0 && grupo[i].intersects(jugador)) return true;
        }
        return false;
    }

    // Un monstruo vivo esta tocando al jugador: cuenta como golpe.
    boolean toca(int x, int y) {
        Rectangle jugador = cuerpo(x, y);
        jugador.grow(CONTACTO, CONTACTO);
        for (int i = 0; i < grupo.length; i++) {
            if (vida[i] > 0 && grupo[i].intersects(jugador)) return true;
        }
        return false;
    }

    // Indice del monstruo vivo mas cercano dentro del alcance, o -1.
    int masCercano(int x, int y, int alcance) {
        int mejor = -1;
        long menor = (long) alcance * alcance;
        int cx = x + CUERPO / 2;
        int cy = y + CUERPO / 2;
        for (int i = 0; i < grupo.length; i++) {
            if (vida[i] <= 0) continue;
            long dx = grupo[i].x + CUERPO / 2 - cx;
            long dy = grupo[i].y + CUERPO / 2 - cy;
            long distancia = dx * dx + dy * dy;
            if (distancia <= menor) {
                menor = distancia;
                mejor = i;
            }
        }
        return mejor;
    }

    Rectangle posicion(int i) {
        return grupo[i];
    }

    // Resta vida. Devuelve true si el monstruo murio.
    boolean danar(int i, int cantidad, long ahora) {
        vida[i] -= cantidad;
        if (vida[i] > 0) return false;
        vida[i] = 0;
        reaparece[i] = ahora + Combate.REAPARICION_MS;
        return true;
    }

    // Solo los monstruos vivos viajan al cliente.
    Mensaje[] posiciones() {
        List<Mensaje> vivos = new ArrayList<>();
        for (int i = 0; i < grupo.length; i++) {
            if (vida[i] <= 0) continue;
            Mensaje posicion = new Mensaje();
            posicion.x = grupo[i].x;
            posicion.y = grupo[i].y;
            posicion.vida = vida[i];
            posicion.vidaMax = Combate.VIDA_MONSTRUO;
            vivos.add(posicion);
        }
        return vivos.toArray(new Mensaje[0]);
    }

    private static Rectangle cuerpo(int x, int y) {
        return new Rectangle(x, y, CUERPO, CUERPO);
    }
}
