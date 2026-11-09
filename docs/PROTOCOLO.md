# Protocolo cliente-servidor

Comunicación por **TCP** (puerto **5555**). Cada mensaje es un objeto **JSON en una sola línea**,
terminado en salto de línea, codificado en UTF-8. Los campos `null` no se envían.
El mismo formato lo definen las clases `Mensaje` y `ConversorJSON` del cliente y del servidor
(mantén ambas copias iguales).

El **servidor decide todo lo que afecta a varios jugadores**: vida, daño, muertes, movimiento de los
monstruos y reinicio de la partida. El cliente solo calcula el movimiento de su propio personaje.

## Campos de `Mensaje`

| Campo | Tipo | Uso |
|-------|------|-----|
| `tipo` | texto | `autenticar`, `posiciones` o `correccion`. Obligatorio |
| `usuario` | texto | Nombre de usuario |
| `password` | texto | Contraseña (solo en el login del cliente) |
| `ok` | booleano | Resultado del login |
| `contenido` | texto | Mensaje legible del servidor |
| `id` | entero | Lugar del jugador, 0 a 3 (define el color). En un disparo: quién ataca |
| `x`, `y` | enteros | Posición en píxeles. En un disparo: posición del objetivo |
| `jugadores` | arreglo de 4 | Estado de todos los lugares (`null` si está vacío) |
| `monstruos` | arreglo | Monstruos **vivos** (`x`, `y`, `vida`, `vidaMax`) |
| `vida`, `vidaMax` | enteros | Vida actual y máxima de un jugador o un monstruo |
| `fin` | booleano | `true` mientras la partida está terminada (todos cayeron) |
| `disparos` | arreglo | Ataques ocurridos en ese mensaje; el cliente los dibuja unos instantes |

## 1. Autenticación

El cliente abre el socket y envía **como primer mensaje**:

```json
{"tipo":"autenticar","usuario":"Jugador1","password":"Player1"}
```

El servidor responde:

```json
{"tipo":"autenticar","usuario":"Jugador1","ok":true,"contenido":"Bienvenido","id":0,"x":100,"y":100}
```

o, si no entra:

```json
{"tipo":"autenticar","ok":false,"contenido":"Ese usuario ya esta conectado."}
```

Motivos de rechazo: faltan datos, credenciales incorrectas, usuario ya conectado o sala llena
(máximo 4 jugadores). Tras un rechazo el servidor cierra la conexión.
El servidor espera el login un máximo de 30 segundos.

## 2. Juego

El cliente envía su posición cada vez que se mueve:

```json
{"tipo":"posiciones","x":148,"y":100}
```

El servidor envía a **todos** los jugadores el estado completo cada vez que algo cambia
(un jugador o un monstruo se mueve, alguien recibe daño, etc.):

```json
{"tipo":"posiciones","jugadores":[{"usuario":"Jugador1","id":0,"x":148,"y":100,"vida":80,"vidaMax":100},{"usuario":"Jugador2","id":1,"x":196,"y":100,"vida":0,"vidaMax":100},null,null],"monstruos":[{"x":480,"y":96,"vida":20,"vidaMax":30},{"x":576,"y":96,"vida":30,"vidaMax":30}],"disparos":[{"id":0,"x":480,"y":96}]}
```

- Un jugador con `vida` 0 está **caído**: el servidor ignora sus posiciones.
- `disparos` solo aparece en el mensaje donde ocurrió el ataque.

Reglas del servidor:

- Posición inicial: `x = 100 + id * 96`, `y = 100` (si hay un monstruo encima, se corre a la derecha).
- Rango válido: `x` e `y` de 0 a 2352 (mundo de 50 × 50 tiles de 48 px). Fuera de rango **cierra la conexión**.
- Usa siempre el `id` de la conexión, nunca uno enviado por el cliente.
- Un mensaje fuera del protocolo **cierra la conexión** de ese cliente.
- Si la posición enviada cae sobre un monstruo, se rechaza y el servidor responde con una corrección.

### Corrección

El servidor ordena al cliente volver a una posición (choque con un monstruo, reinicio de partida):

```json
{"tipo":"correccion","x":100,"y":100}
```

## 3. Combate

Todas las cifras están en `servidor/.../Combate.java`; para balancear el juego se cambian ahí.

| Regla | Valor actual |
|-------|--------------|
| Vida del jugador | 100 |
| Daño de un monstruo al tocar al jugador | 10, y el jugador queda protegido 0.7 s |
| Protección al entrar o reaparecer | 1.5 s |
| Ataque automático | 10 de daño al monstruo vivo más cercano, cada 0.6 s, alcance 220 px |
| Vida del monstruo | 30 (3 ataques) |
| Reaparición de un monstruo muerto | 3 s después, en un lugar libre a más de 700 px de los jugadores vivos |

**Fin de la partida:** cuando todos los jugadores conectados están caídos, el servidor envía `"fin":true`.
Pasados 8 s reinicia todo: vida completa, posición inicial (con `correccion` para cada cliente) y monstruos
en su lugar de salida. Si alguien entra mientras la partida está terminada, la reinicia para todos.
Si todos se van, la partida también se reinicia.

Ejemplo del mensaje de fin:

```json
{"tipo":"posiciones","jugadores":[{"usuario":"Jugador2","id":1,"x":196,"y":100,"vida":0,"vidaMax":100},null,null,null],"monstruos":[{"x":576,"y":96,"vida":30,"vidaMax":30}],"fin":true}
```

## 4. Salida

No hay mensaje de salida: cuando el cliente cierra el socket, el servidor libera el lugar y
envía el estado actualizado a los demás.

## Cómo ampliar el protocolo

1. Agrega los campos nuevos a `Mensaje` en **cliente y servidor**.
2. Define un nuevo valor de `tipo` y manéjalo en `ManejadorClientes` (servidor) y `Cliente` (cliente).
3. Documenta el mensaje aquí en el mismo Pull Request.
