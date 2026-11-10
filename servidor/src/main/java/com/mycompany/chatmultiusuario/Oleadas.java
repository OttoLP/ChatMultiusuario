package com.mycompany.chatmultiusuario;

// Director de oleadas: cuenta regresiva, aparicion de enemigos por tandas y fin de oleada.
// Se usa siempre dentro de synchronized (clientes) de ManejadorClientes.
final class Oleadas {

    enum Resultado { SIN_CAMBIO, CAMBIO, INICIADA, SUPERADA }

    private int numero;                  // oleada en curso (0 = todavia no empieza la primera)
    private boolean enCurso;             // false = descanso
    private long terminaDescansoEn = -1; // -1 = aun no se fijo (primer ciclo tras reiniciar)
    private int porAparecer;             // enemigos de esta oleada que faltan por salir
    private long proximaTanda;
    private int vidaMonstruo = Combate.VIDA_MONSTRUO;
    private int ultimoSegundo = -1;      // para avisar a los clientes cada segundo del descanso

    void reiniciar() {
        numero = 0;
        enCurso = false;
        terminaDescansoEn = -1;
        porAparecer = 0;
        ultimoSegundo = -1;
        vidaMonstruo = Combate.VIDA_MONSTRUO;
    }

    int numero() { return numero; }
    boolean enCurso() { return enCurso; }

    // Enemigos que faltan por derrotar: los que estan vivos y los que aun no salen.
    int restantes(Monstruos monstruos) {
        return porAparecer + monstruos.vivos();
    }

    int segundosDescanso(long ahora) {
        if (terminaDescansoEn < 0) return (int) (Combate.DESCANSO_INICIAL_MS / 1000);
        return (int) Math.ceil(Math.max(0, terminaDescansoEn - ahora) / 1000.0);
    }

    Resultado update(long ahora, int conectados, Monstruos monstruos, Mensaje[] jugadoresVivos) {
        if (!enCurso) {
            if (terminaDescansoEn < 0) {
                terminaDescansoEn = ahora + Combate.DESCANSO_INICIAL_MS;
                return Resultado.CAMBIO;
            }
            if (ahora >= terminaDescansoEn) {
                numero++;
                enCurso = true;
                porAparecer = cantidadEnemigos(numero, conectados);
                vidaMonstruo = vidaMonstruo(numero);
                proximaTanda = ahora;
                aparecerTanda(ahora, monstruos, jugadoresVivos);
                return Resultado.INICIADA;
            }
            int segundos = segundosDescanso(ahora);
            if (segundos == ultimoSegundo) return Resultado.SIN_CAMBIO;
            ultimoSegundo = segundos;
            return Resultado.CAMBIO;
        }
        boolean cambio = false;
        if (porAparecer > 0 && ahora >= proximaTanda) {
            cambio = aparecerTanda(ahora, monstruos, jugadoresVivos);
        }
        if (porAparecer == 0 && monstruos.vivos() == 0) {
            enCurso = false;
            terminaDescansoEn = ahora + Combate.DESCANSO_MS;
            ultimoSegundo = -1;
            return Resultado.SUPERADA;
        }
        return cambio ? Resultado.CAMBIO : Resultado.SIN_CAMBIO;
    }

    private boolean aparecerTanda(long ahora, Monstruos monstruos, Mensaje[] jugadoresVivos) {
        int aparecidos = 0;
        while (aparecidos < Combate.TANDA_ENEMIGOS && aparecidos < porAparecer
                && monstruos.vivos() < Combate.MAX_ENEMIGOS_VIVOS
                && monstruos.aparecer(vidaMonstruo, jugadoresVivos)) {
            aparecidos++;
        }
        porAparecer -= aparecidos;
        proximaTanda = ahora + Combate.TANDA_MS;
        return aparecidos > 0;
    }

    static int cantidadEnemigos(int oleada, int jugadores) {
        double porJugadores = 1 + Combate.ENEMIGOS_POR_JUGADOR * (Math.max(1, jugadores) - 1);
        return (int) Math.round((Combate.ENEMIGOS_BASE + Combate.ENEMIGOS_POR_OLEADA * oleada) * porJugadores);
    }

    static int vidaMonstruo(int oleada) {
        return (int) Math.round(Combate.VIDA_MONSTRUO * (1 + Combate.CRECIMIENTO_VIDA * (oleada - 1)));
    }
}
