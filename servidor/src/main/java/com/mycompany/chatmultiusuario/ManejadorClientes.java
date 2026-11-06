package com.mycompany.chatmultiusuario;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ManejadorClientes implements Runnable {
    // El indice del arreglo tambien determina el color de la camisa.
    private static final ManejadorClientes[] clientes = new ManejadorClientes[4];
    // El escenario mundo.txt mide 50 x 50 tiles de 48 px.
    private static final int MAX_POSICION_MUNDO = (50 - 1) * 48;
    private static final Monstruos monstruos = new Monstruos();

    static {
        var reloj = Executors.newSingleThreadScheduledExecutor(tarea -> {
            Thread hilo = new Thread(tarea, "monstruos");
            hilo.setDaemon(true);
            return hilo;
        });
        reloj.scheduleAtFixedRate(ManejadorClientes::actualizarMonstruos, 0, 50, TimeUnit.MILLISECONDS);
    }

    private final Socket socket;
    private BufferedReader entrada;
    private BufferedWriter salida;
    private String usuario;
    private int id = -1;
    private int x, y;

    public ManejadorClientes(Socket socket) { this.socket = socket; }

    @Override
    public void run() {
        try {
            entrada = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            salida = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8));
            socket.setSoTimeout(30000);
            String linea = entrada.readLine();
            if (linea == null) return;
            Mensaje login = ConversorJSON.desdeJson(linea);
            if (!"autenticar".equals(login.tipo) || login.usuario == null
                    || login.usuario.isBlank() || login.password == null
                    || login.password.isEmpty()) {
                responderLogin(false, "Faltan usuario y password.");
                return;
            }
            boolean valido = new UsuarioDAO().autenticar(login.usuario, login.password);
            login.password = null;
            if (!valido) {
                responderLogin(false, "No se pudo autenticar. Revisa credenciales y consola del servidor.");
                return;
            }

            // Una sola cerradura protege lugares, coordenadas y envios.
            // Asi dos conexiones simultaneas no ocupan el mismo lugar.
            synchronized (clientes) {
                for (ManejadorClientes cliente : clientes) {
                    if (cliente != null && cliente.usuario.equalsIgnoreCase(login.usuario)) {
                        responderLogin(false, "Ese usuario ya esta conectado.");
                        return;
                    }
                }
                int libre = -1;
                for (int i = 0; i < clientes.length; i++) {
                    if (clientes[i] == null) { libre = i; break; }
                }
                if (libre == -1) {
                    responderLogin(false, "Sala llena: maximo 4 jugadores.");
                    return;
                }
                id = libre;
                usuario = login.usuario;
                x = 100 + id * 96;
                y = 100;
                while (monstruos.choca(x, y) && x < 900) {
                    x += 48;
                }
                responderLogin(true, "Bienvenido");
                clientes[id] = this;
                enviarPosiciones();
                System.out.println(usuario + " entro como jugador " + (id + 1));
            }
            socket.setSoTimeout(0);
            while ((linea = entrada.readLine()) != null) {
                Mensaje mensaje = ConversorJSON.desdeJson(linea);
                if (!"posiciones".equals(mensaje.tipo) || mensaje.x == null || mensaje.y == null) {
                    return; // Cliente fuera del protocolo: cerrar su conexion.
                }
                // Solo limites del mundo. El movimiento se calcula en el cliente.
                if (mensaje.x < 0 || mensaje.x > MAX_POSICION_MUNDO
                        || mensaje.y < 0 || mensaje.y > MAX_POSICION_MUNDO) {
                    return;
                }
                synchronized (clientes) {
                    // Se usa el id de esta conexion, nunca un id enviado por el cliente.
                    if (monstruos.choca(mensaje.x, mensaje.y)) {
                        Mensaje correccion = new Mensaje("correccion");
                        correccion.x = x;
                        correccion.y = y;
                        enviar(correccion);
                        continue;
                    }
                    x = mensaje.x;
                    y = mensaje.y;
                    enviarPosiciones();
                }
            }
        } catch (IOException | RuntimeException e) {
            System.out.println("Conexion cerrada: " + (usuario == null ? "sin autenticar" : usuario)
                    + " (" + e.getMessage() + ")");
        } finally {
            cerrarSocket();
            synchronized (clientes) {
                if (id >= 0 && clientes[id] == this) {
                    clientes[id] = null;
                    if (Arrays.stream(clientes).allMatch(cliente -> cliente == null)) monstruos.reiniciar();
                    enviarPosiciones();
                    System.out.println(usuario + " salio. Lugar disponible.");
                }
            }
        }
    }

    private void responderLogin(boolean ok, String texto) throws IOException {
        Mensaje respuesta = new Mensaje("autenticar");
        respuesta.ok = ok;
        respuesta.contenido = texto;
        if (ok) {
            respuesta.id = id;
            respuesta.usuario = usuario;
            respuesta.x = x;
            respuesta.y = y;
        }
        enviar(respuesta);
    }

    private void enviar(Mensaje mensaje) throws IOException {
        salida.write(ConversorJSON.aJson(mensaje));
        salida.newLine();
        salida.flush();
    }

    private static void actualizarMonstruos() {
        synchronized (clientes) {
            if (Arrays.stream(clientes).allMatch(cliente -> cliente == null)) return;
            if (monstruos.update(posicionesJugadores())) enviarPosiciones();
        }
    }

    private static void enviarPosiciones() {
        Mensaje estado = new Mensaje("posiciones");
        estado.jugadores = posicionesJugadores();
        estado.monstruos = monstruos.posiciones();
        for (ManejadorClientes cliente : clientes) {
            if (cliente != null) {
                try { cliente.enviar(estado); }
                catch (IOException e) { cliente.cerrarSocket(); }
            }
        }
    }

    private static Mensaje[] posicionesJugadores() {
        Mensaje[] jugadores = new Mensaje[clientes.length];
        for (int i = 0; i < clientes.length; i++) {
            ManejadorClientes cliente = clientes[i];
            if (cliente != null) {
                Mensaje jugador = new Mensaje();
                jugador.id = i;
                jugador.usuario = cliente.usuario;
                jugador.x = cliente.x;
                jugador.y = cliente.y;
                jugadores[i] = jugador;
            }
        }
        return jugadores;
    }
    private void cerrarSocket() {
        try { socket.close(); } catch (IOException ignored) {}
    }
}
