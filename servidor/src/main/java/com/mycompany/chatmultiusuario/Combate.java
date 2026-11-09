package com.mycompany.chatmultiusuario;

// Reglas del combate. Todo se decide en el servidor; para balancear el juego
// basta con cambiar estos numeros.
final class Combate {
    private Combate() {}

    // Jugadores
    static final int VIDA_JUGADOR = 100;
    static final int DANIO_CONTACTO = 10;       // golpe de un monstruo que toca al jugador
    static final long INVULNERABLE_MS = 700;    // tiempo sin recibir golpes despues de uno
    static final long PROTECCION_ENTRADA_MS = 1500; // proteccion al entrar o reaparecer

    // Ataque automatico: golpea al monstruo mas cercano dentro del alcance
    static final int DANIO_ATAQUE = 10;
    static final long ATAQUE_ESPERA_MS = 600;
    static final int ALCANCE_ATAQUE = 220;      // pixeles, medido entre centros

    // Monstruos
    static final int VIDA_MONSTRUO = 30;
    static final long REAPARICION_MS = 3000;    // un monstruo muerto vuelve tras este tiempo
    static final int DISTANCIA_REAPARICION = 700; // minimo a cualquier jugador vivo (fuera de pantalla)

    // Cuando todos los jugadores caen, la partida se reinicia sola
    static final long REINICIO_MS = 8000;
}
