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
    public Mensaje[] disparos;

    public Mensaje() {}
    public Mensaje(String tipo) { this.tipo = tipo; }
}
