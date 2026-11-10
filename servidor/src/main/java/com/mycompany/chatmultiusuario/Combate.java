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
    static final int VIDA_MONSTRUO = 30;        // vida en la oleada 1
    static final int DISTANCIA_REAPARICION = 700; // minimo a cualquier jugador vivo (fuera de pantalla)

    // Oleadas
    static final long DESCANSO_INICIAL_MS = 5000; // antes de la primera oleada
    static final long DESCANSO_MS = 8000;         // entre oleadas
    static final int ENEMIGOS_BASE = 8;           // enemigos de la oleada N: BASE + POR_OLEADA * N
    static final int ENEMIGOS_POR_OLEADA = 4;
    static final double ENEMIGOS_POR_JUGADOR = 0.4; // cada jugador extra suma 40 % de enemigos
    static final double CRECIMIENTO_VIDA = 0.15;    // la vida del monstruo sube 15 % por oleada
    static final int MAX_ENEMIGOS_VIVOS = 60;     // el resto espera su turno
    static final int TANDA_ENEMIGOS = 4;          // enemigos que aparecen juntos...
    static final long TANDA_MS = 1000;            // ...cada tanta milisegundos

    // Al superar una oleada
    static final int CURACION_PORCENTAJE = 30;    // vida que recuperan los jugadores vivos
    static final int REVIVIR_PORCENTAJE = 50;     // vida con la que reviven los caidos

    // Mejoras: al superar una oleada cada jugador gana una al azar (daño o velocidad).
    // Si cayo durante la oleada, antes pierde la ultima que habia ganado.
    static final boolean AMBAS_MEJORAS = false;   // true: cada oleada sube daño y velocidad a la vez
    static final int DANIO_POR_NIVEL = 2;         // se suma a DANIO_ATAQUE
    static final double VELOCIDAD_BASE = 4.0;     // pixeles por cuadro (igual que el cliente)
    static final double VELOCIDAD_POR_NIVEL = 0.3;
    static final int MAX_NIVEL_VELOCIDAD = 8;

    // Cuando todos los jugadores caen, la partida se reinicia sola
    static final long REINICIO_MS = 8000;
}
