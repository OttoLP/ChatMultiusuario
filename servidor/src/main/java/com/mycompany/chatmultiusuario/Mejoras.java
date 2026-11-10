package com.mycompany.chatmultiusuario;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

// Mejoras de un jugador: se ganan al superar una oleada y, si el jugador cayo,
// pierde la ultima que habia ganado. Nunca baja del valor inicial.
final class Mejoras {
    private static final int DANIO = 1, VELOCIDAD = 2;   // banderas del historial

    private int nivelDanio;
    private int nivelVelocidad;
    private final Deque<Integer> historial = new ArrayDeque<>(); // lo ultimo que se gano, al frente

    int nivelDanio() { return nivelDanio; }
    int nivelVelocidad() { return nivelVelocidad; }

    int danioAtaque() {
        return Combate.DANIO_ATAQUE + Combate.DANIO_POR_NIVEL * nivelDanio;
    }

    // Pixeles por cuadro con los que se mueve el jugador (el cliente los aplica).
    double velocidad() {
        return Combate.VELOCIDAD_BASE + Combate.VELOCIDAD_POR_NIVEL * nivelVelocidad;
    }

    void reiniciar() {
        nivelDanio = 0;
        nivelVelocidad = 0;
        historial.clear();
    }

    // Una mejora al azar (o las dos, segun Combate.AMBAS_MEJORAS). Devuelve el aviso para el jugador.
    String subir(Random azar) {
        boolean puedeVelocidad = nivelVelocidad < Combate.MAX_NIVEL_VELOCIDAD;
        int banderas;
        if (Combate.AMBAS_MEJORAS) {
            banderas = DANIO | (puedeVelocidad ? VELOCIDAD : 0);
        } else {
            banderas = (puedeVelocidad && azar.nextBoolean()) ? VELOCIDAD : DANIO;
        }
        aplicar(banderas, 1);
        historial.push(banderas);
        return texto(banderas, "+");
    }

    // Quita la ultima mejora ganada. Devuelve el aviso, o null si no tenia ninguna.
    String penalizar() {
        Integer banderas = historial.poll();
        if (banderas == null) return null;
        aplicar(banderas, -1);
        return texto(banderas, "-");
    }

    private void aplicar(int banderas, int cambio) {
        if ((banderas & DANIO) != 0) nivelDanio += cambio;
        if ((banderas & VELOCIDAD) != 0) nivelVelocidad += cambio;
    }

    private static String texto(int banderas, String signo) {
        List<String> partes = new ArrayList<>();
        if ((banderas & DANIO) != 0) partes.add(signo + "1 DA\u00D1O");
        if ((banderas & VELOCIDAD) != 0) partes.add(signo + "1 VELOCIDAD");
        return String.join(", ", partes);
    }
}
