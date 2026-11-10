package red;


public class Mensaje {
    public String tipo;
    public String usuario;
    public String password;
    public Boolean ok;
    public String contenido;
    public Integer id;
    public Integer x, y;
    public Mensaje[] jugadores;
    public Mensaje[] monstruos;
    // Combate: vida actual y maxima (jugadores y monstruos), fin de partida
    // y ataques ocurridos en este mensaje (id = quien ataca, x/y = objetivo).
    public Integer vida, vidaMax;
    public Boolean fin;
    // Oleadas: numero de la oleada, enemigos que faltan (durante la oleada)
    // y segundos para la siguiente (durante el descanso).
    public Integer oleada, restantes, descanso;
    // Mejoras de un jugador: niveles y velocidad en pixeles por cuadro.
    // En un jugador, contenido trae el aviso de mejora durante el descanso.
    public Integer nivelDanio, nivelVelocidad;
    public Double velocidad;
    public Mensaje[] disparos;

    public Mensaje() {}
    public Mensaje(String tipo) { this.tipo = tipo; }
}
