package red;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

public class Cliente {
    private final Socket socket;
    private BufferedReader entrada;
    private BufferedWriter salida;
    private volatile boolean conectado;
    private volatile Mensaje estado = new Mensaje("posiciones");
    private final AtomicReference<Mensaje> correccion = new AtomicReference<>();
    private volatile String error = "";
    private int id;
    private int x, y;
    private String usuario;

    // La conexion ya fue abierta por ConexionServidor.
    public Cliente(Socket socket) {
        this.socket = socket;
    }

    public void autenticar() throws IOException {
        try {
            Properties datos = new Properties();
            String archivo = System.getProperty("config", "cliente.properties");
            try (Reader lector = new FileReader(archivo, StandardCharsets.UTF_8)) {
                datos.load(lector);
            }
            usuario = datos.getProperty("usuario");
            String password = datos.getProperty("password");
            if (usuario == null || usuario.isBlank() || password == null || password.isEmpty()) {
                throw new IOException("Faltan usuario/password en " + archivo);
            }
            socket.setSoTimeout(30000);
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            salida = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            Mensaje login = new Mensaje("autenticar");
            login.usuario = usuario;
            login.password = password;
            enviar(login);
            login.password = null;
            datos.remove("password");
            String linea = entrada.readLine();
            if (linea == null) throw new IOException("El servidor cerro la conexion.");
            Mensaje respuesta = ConversorJSON.desdeJson(linea);
            if (!"autenticar".equals(respuesta.tipo) || !Boolean.TRUE.equals(respuesta.ok)) {
                throw new IOException(respuesta.contenido == null ? "Acceso rechazado" : respuesta.contenido);
            }
            if (respuesta.id == null || respuesta.id < 0 || respuesta.id > 3
                    || respuesta.x == null || respuesta.y == null) {
                throw new IOException("Respuesta de autenticacion incompleta.");
            }
            id = respuesta.id;
            x = respuesta.x;
            y = respuesta.y;
            socket.setSoTimeout(0);
            conectado = true;
            new Thread(() -> recibeMensaje(), "recepcion-json").start();
        } catch (IOException | RuntimeException e) {
            cerrar();
            throw new IOException("No se pudo entrar: " + e.getMessage(), e);
        }
    }

    private void recibeMensaje() {
        try {
            String linea;
            while ((linea = entrada.readLine()) != null) {
                Mensaje mensaje = ConversorJSON.desdeJson(linea);
                if ("correccion".equals(mensaje.tipo) && mensaje.x != null && mensaje.y != null) {
                    correccion.set(mensaje);
                    continue;
                }
                if (!"posiciones".equals(mensaje.tipo) || mensaje.jugadores == null
                        || mensaje.jugadores.length != 4) throw new IOException("Estado invalido");
                estado = mensaje;
            }
            if (conectado) error = "El servidor cerro la conexion.";
        } catch (IOException | RuntimeException e) {
            if (conectado) error = "Se perdio la conexion con el servidor.";
        } finally { cerrar(); }
    }

    public void enviarPosicion(int x, int y) {
        Mensaje mensaje = new Mensaje("posiciones");
        mensaje.x = x;
        mensaje.y = y;
        try { enviar(mensaje); }
        catch (IOException e) {
            error = "No se pudo enviar la posicion.";
            cerrar();
        }
    }
    private void enviar(Mensaje mensaje) throws IOException {
        salida.write(ConversorJSON.aJson(mensaje));
        salida.newLine();
        salida.flush();
    }
    public void cerrar() {
        conectado = false;
        if (socket != null) try { socket.close(); } catch (IOException ignored) {}
    }
    public boolean estaConectado() { return conectado; }
    public Mensaje getEstado() { return estado; }
    public Mensaje tomarCorreccion() { return correccion.getAndSet(null); }
    public String getError() { return error; }
    public int getId() { return id; }
    public int getX() { return x; }
    public int getY() { return y; }
    public String getUsuario() { return usuario; }
}
