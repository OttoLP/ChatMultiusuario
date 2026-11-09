package com.mycompany.chatmultiusuario;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ManejadorClientes implements Runnable {
    // El indice del arreglo tambien determina el color de la camisa.
    private static final ManejadorClientes[] clientes = new ManejadorClientes[4];
    // El escenario mundo.txt mide 50 x 50 tiles de 48 px.
    private static final int MAX_POSICION_MUNDO = (50 - 1) * 48;
    private static final Monstruos monstruos = new Monstruos();
    // Todos los jugadores cayeron: se espera Combate.REINICIO_MS y se reinicia.
    private static boolean finPartida;
    private static long reinicioEn;

    static {
        var reloj = Executors.newSingleThreadScheduledExecutor(tarea -> {
            Thread hilo = new Thread(tarea, "partida");
            hilo.setDaemon(true);
            return hilo;
        });
        reloj.scheduleAtFixedRate(ManejadorClientes::actualizarPartida, 0, 50, TimeUnit.MILLISECONDS);
    }

    private final Socket socket;
    private BufferedReader entrada;
    private BufferedWriter salida;
    private String usuario;
    private int id = -1;
    private int x, y;
    // Combate (solo se tocan dentro de synchronized (clientes)).
    private int vida = Combate.VIDA_JUGADOR;
    private long invulnerableHasta;
    private long proximoAtaque;

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
                colocarEnInicio();
                vida = Combate.VIDA_JUGADOR;
                invulnerableHasta = System.currentTimeMillis() + Combate.PROTECCION_ENTRADA_MS;
                responderLogin(true, "Bienvenido");
                clientes[id] = this;
                // Si la partida habia terminado, quien entra la reinicia para todos.
                if (finPartida) reiniciarPartida();
                else enviarPosiciones();
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
                    // Un jugador caido no se mueve.
                    if (vida <= 0) continue;
                    // Se usa el id de esta conexion, nunca un id enviado por el cliente.
                    if (monstruos.choca(mensaje.x, mensaje.y)) {
                        enviarPosicionActual();
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
                    if (Arrays.stream(clientes).allMatch(cliente -> cliente == null)) {
                        monstruos.reiniciar();
                        finPartida = false;
                    }
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

    // Lugar de salida segun el numero de jugador, esquivando monstruos.
    private void colocarEnInicio() {
        x = 100 + id * 96;
        y = 100;
        while (monstruos.choca(x, y) && x < 900) {
            x += 48;
        }
    }

    // Le dice al cliente donde esta el jugador segun el servidor.
    private void enviarPosicionActual() {
        Mensaje correccion = new Mensaje("correccion");
        correccion.x = x;
        correccion.y = y;
        try { enviar(correccion); }
        catch (IOException e) { cerrarSocket(); }
    }

    // ---- Combate ----

    // Un monstruo toca al jugador: pierde vida y queda protegido un momento.
    private boolean recibirGolpes(long ahora) {
        if (ahora < invulnerableHasta || !monstruos.toca(x, y)) return false;
        vida = Math.max(0, vida - Combate.DANIO_CONTACTO);
        invulnerableHasta = ahora + Combate.INVULNERABLE_MS;
        if (vida == 0) System.out.println(usuario + " cayo.");
        return true;
    }

    // Ataque automatico al monstruo mas cercano dentro del alcance.
    private boolean atacar(long ahora, List<Mensaje> disparos) {
        if (ahora < proximoAtaque) return false;
        int objetivo = monstruos.masCercano(x, y, Combate.ALCANCE_ATAQUE);
        if (objetivo < 0) return false;
        proximoAtaque = ahora + Combate.ATAQUE_ESPERA_MS;
        Mensaje disparo = new Mensaje();
        disparo.id = id;
        disparo.x = monstruos.posicion(objetivo).x;
        disparo.y = monstruos.posicion(objetivo).y;
        disparos.add(disparo);
        monstruos.danar(objetivo, Combate.DANIO_ATAQUE, ahora);
        return true;
    }

    // Se llama cada 50 ms.
    private static void actualizarPartida() {
        synchronized (clientes) {
            if (Arrays.stream(clientes).allMatch(cliente -> cliente == null)) return;
            long ahora = System.currentTimeMillis();
            if (finPartida) {
                if (ahora >= reinicioEn) reiniciarPartida();
                return;
            }
            boolean cambio = monstruos.update(posicionesVivos(), ahora);
            List<Mensaje> disparos = new ArrayList<>();
            boolean alguienVivo = false;
            for (ManejadorClientes jugador : clientes) {
                if (jugador == null || jugador.vida <= 0) continue;
                cambio |= jugador.recibirGolpes(ahora);
                if (jugador.vida > 0) {
                    cambio |= jugador.atacar(ahora, disparos);
                    alguienVivo = true;
                }
            }
            if (!alguienVivo) {
                finPartida = true;
                reinicioEn = ahora + Combate.REINICIO_MS;
                cambio = true;
                System.out.println("Todos cayeron. Reinicio en " + (Combate.REINICIO_MS / 1000) + " s.");
            }
            if (cambio) enviarPosiciones(disparos);
        }
    }

    // Todos vuelven al inicio con la vida completa. Siempre dentro de synchronized(clientes).
    private static void reiniciarPartida() {
        finPartida = false;
        monstruos.reiniciar();
        long ahora = System.currentTimeMillis();
        for (ManejadorClientes cliente : clientes) {
            if (cliente == null) continue;
            cliente.vida = Combate.VIDA_JUGADOR;
            cliente.invulnerableHasta = ahora + Combate.PROTECCION_ENTRADA_MS;
            cliente.proximoAtaque = 0;
            cliente.colocarEnInicio();
            cliente.enviarPosicionActual();
        }
        enviarPosiciones();
        System.out.println("Partida reiniciada.");
    }

    // Se llama siempre dentro de synchronized(clientes).
    private static void enviarPosiciones() {
        enviarPosiciones(List.of());
    }

    private static void enviarPosiciones(List<Mensaje> disparos) {
        Mensaje estado = new Mensaje("posiciones");
        estado.jugadores = posicionesJugadores();
        estado.monstruos = monstruos.posiciones();
        if (finPartida) estado.fin = true;
        if (!disparos.isEmpty()) estado.disparos = disparos.toArray(new Mensaje[0]);
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
                jugador.vida = cliente.vida;
                jugador.vidaMax = Combate.VIDA_JUGADOR;
                jugadores[i] = jugador;
            }
        }
        return jugadores;
    }

    // Igual que posicionesJugadores, pero los caidos aparecen como null.
    private static Mensaje[] posicionesVivos() {
        Mensaje[] jugadores = posicionesJugadores();
        for (int i = 0; i < clientes.length; i++) {
            if (clientes[i] != null && clientes[i].vida <= 0) jugadores[i] = null;
        }
        return jugadores;
    }

    private void cerrarSocket() {
        try { socket.close(); } catch (IOException ignored) {}
    }
}
