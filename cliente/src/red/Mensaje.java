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

    public Mensaje() {}
    public Mensaje(String tipo) { this.tipo = tipo; }
}
