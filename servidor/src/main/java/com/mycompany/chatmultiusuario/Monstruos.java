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

    private static final class Enemigo {
        final Rectangle cuerpo;
        final int vidaMax;
        int vida;

        Enemigo(Rectangle cuerpo, int vida) {
            this.cuerpo = cuerpo;
            this.vida = vida;
            this.vidaMax = vida;
        }
    }

    // Solo hay enemigos vivos: al morir se quitan de la lista.
    private final List<Enemigo> enemigos = new ArrayList<>();

    Monstruos() {
        InputStream recurso = getClass().getResourceAsStream("/escenarios/mundo.txt");
        if (recurso == null) throw new IllegalStateException("Falta escenarios/mundo.txt");
        try (Scanner lector = new Scanner(recurso, StandardCharsets.UTF_8)) {
            for (int[] fila : mapa) {
                for (int col = 0; col < fila.length; col++) fila[col] = lector.nextInt();
            }
        }
    }

    void reiniciar() {
        enemigos.clear();
    }

    int vivos() {
        return enemigos.size();
    }

    // jugadores: solo los que estan vivos (los caidos no atraen ni estorban).
    boolean update(Mensaje[] jugadores) {
        boolean cambio = false;
        for (Enemigo enemigo : enemigos) {
            Rectangle monstruo = enemigo.cuerpo;
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
            cambio |= mover(enemigo, dx, 0, jugadores);
            cambio |= mover(enemigo, 0, dy, jugadores);
        }
        return cambio;
    }

    private boolean mover(Enemigo enemigo, int dx, int dy, Mensaje[] jugadores) {
        if (dx == 0 && dy == 0) return false;
        Rectangle monstruo = enemigo.cuerpo;
        Rectangle siguiente = cuerpo(monstruo.x + dx, monstruo.y + dy);
        for (int fila = siguiente.y / TILE; fila <= (siguiente.y + CUERPO - 1) / TILE; fila++) {
            for (int col = siguiente.x / TILE; col <= (siguiente.x + CUERPO - 1) / TILE; col++) {
                if (bloquea(mapa[fila][col])) return false;
            }
        }
        for (Mensaje jugador : jugadores) {
            if (jugador != null && siguiente.intersects(cuerpo(jugador.x, jugador.y))) return false;
        }
        for (Enemigo otro : enemigos) {
            if (otro != enemigo && siguiente.intersects(otro.cuerpo)) return false;
        }
        monstruo.setLocation(siguiente.x, siguiente.y);
        return true;
    }

    // Hace aparecer un enemigo en un lugar libre, lejos de los jugadores vivos.
    // Devuelve false si no encontro lugar (se intentara de nuevo en el siguiente ciclo).
    boolean aparecer(int vida, Mensaje[] jugadores) {
        ThreadLocalRandom azar = ThreadLocalRandom.current();
        for (int intento = 0; intento < 40; intento++) {
            int col = azar.nextInt(mapa[0].length);
            int fila = azar.nextInt(mapa.length);
            if (bloquea(mapa[fila][col])) continue;
            Rectangle candidato = cuerpo(col * TILE, fila * TILE);
            if (!lejosDeJugadores(candidato, jugadores) || chocaConEnemigo(candidato)) continue;
            enemigos.add(new Enemigo(candidato, vida));
            return true;
        }
        return false;
    }

    private boolean lejosDeJugadores(Rectangle punto, Mensaje[] jugadores) {
        for (Mensaje jugador : jugadores) {
            if (jugador == null) continue;
            if (Math.hypot(jugador.x - punto.x, jugador.y - punto.y) < Combate.DISTANCIA_REAPARICION) return false;
        }
        return true;
    }

    private boolean chocaConEnemigo(Rectangle punto) {
        for (Enemigo enemigo : enemigos) {
            if (punto.intersects(enemigo.cuerpo)) return true;
        }
        return false;
    }

    // Arbol (1) y muro (3) bloquean el paso, igual que en el cliente.
    private static boolean bloquea(int tile) {
        return tile == 1 || tile == 3;
    }

    // Un monstruo ocupa el lugar del jugador (se usa para no dejarlo entrar en el).
    boolean choca(int x, int y) {
        return chocaConEnemigo(cuerpo(x, y));
    }

    // Un monstruo esta tocando al jugador: cuenta como golpe.
    boolean toca(int x, int y) {
        Rectangle jugador = cuerpo(x, y);
        jugador.grow(CONTACTO, CONTACTO);
        return chocaConEnemigo(jugador);
    }

    // Indice del monstruo mas cercano dentro del alcance, o -1.
    int masCercano(int x, int y, int alcance) {
        int mejor = -1;
        long menor = (long) alcance * alcance;
        int cx = x + CUERPO / 2;
        int cy = y + CUERPO / 2;
        for (int i = 0; i < enemigos.size(); i++) {
            Rectangle cuerpo = enemigos.get(i).cuerpo;
            long dx = cuerpo.x + CUERPO / 2 - cx;
            long dy = cuerpo.y + CUERPO / 2 - cy;
            long distancia = dx * dx + dy * dy;
            if (distancia <= menor) {
                menor = distancia;
                mejor = i;
            }
        }
        return mejor;
    }

    // Los indices solo valen hasta el siguiente danar(): usalos de inmediato.
    Rectangle posicion(int i) {
        return enemigos.get(i).cuerpo;
    }

    // Resta vida. Devuelve true si el monstruo murio (y lo quita de la lista).
    boolean danar(int i, int cantidad) {
        Enemigo enemigo = enemigos.get(i);
        enemigo.vida -= cantidad;
        if (enemigo.vida > 0) return false;
        enemigos.remove(i);
        return true;
    }

    Mensaje[] posiciones() {
        Mensaje[] posiciones = new Mensaje[enemigos.size()];
        for (int i = 0; i < posiciones.length; i++) {
            Enemigo enemigo = enemigos.get(i);
            Mensaje posicion = new Mensaje();
            posicion.x = enemigo.cuerpo.x;
            posicion.y = enemigo.cuerpo.y;
            posicion.vida = enemigo.vida;
            posicion.vidaMax = enemigo.vidaMax;
            posiciones[i] = posicion;
        }
        return posiciones;
    }

    private static Rectangle cuerpo(int x, int y) {
        return new Rectangle(x, y, CUERPO, CUERPO);
    }
}
