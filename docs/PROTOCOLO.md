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
| `contenido` | texto | Mensaje legible del servidor. En un jugador: la mejora recibida (o perdida) durante el descanso |
| `id` | entero | Lugar del jugador, 0 a 3 (define el color). En un disparo: quién ataca |
| `x`, `y` | enteros | Posición en píxeles. En un disparo: posición del objetivo |
| `jugadores` | arreglo de 4 | Estado de todos los lugares (`null` si está vacío) |
| `monstruos` | arreglo | Monstruos **vivos** (`x`, `y`, `vida`, `vidaMax`) |
| `vida`, `vidaMax` | enteros | Vida actual y máxima de un jugador o un monstruo |
| `fin` | booleano | `true` mientras la partida está terminada (todos cayeron) |
| `oleada` | entero | Número de la oleada en curso; 0 si todavía no empieza la primera |
| `restantes` | entero | Enemigos que faltan por derrotar (vivos y los que aún no salen). Solo durante una oleada |
| `descanso` | entero | Segundos que faltan para la siguiente oleada. Solo durante el descanso |
| `nivelDanio`, `nivelVelocidad` | enteros | Niveles de mejora de un jugador |
| `velocidad` | decimal | Píxeles por cuadro con los que debe moverse ese jugador (el cliente la aplica) |
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
{"tipo":"posiciones","jugadores":[{"usuario":"Jugador1","id":0,"x":148,"y":100,"vida":80,"vidaMax":100,"nivelDanio":2,"nivelVelocidad":1,"velocidad":4.3},{"usuario":"Jugador2","id":1,"x":196,"y":100,"vida":100,"vidaMax":100,"nivelDanio":1,"nivelVelocidad":3,"velocidad":4.9},null,null],"monstruos":[{"x":480,"y":96,"vida":27,"vidaMax":35}],"oleada":2,"restantes":9,"disparos":[{"id":0,"x":480,"y":96}]}
```

- Un jugador con `vida` 0 está **caído**: el servidor ignora sus posiciones.
- Solo se envían los monstruos vivos, así que el arreglo `monstruos` cambia de tamaño.
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

## 3. Combate, oleadas y mejoras

Todas las cifras están en `servidor/.../Combate.java`; para balancear el juego se cambian ahí.

### Combate

| Regla | Valor actual |
|-------|--------------|
| Vida del jugador | 100 |
| Daño de un monstruo al tocar al jugador | 10, y el jugador queda protegido 0.7 s |
| Protección al entrar | 1.5 s |
| Ataque automático | 10 de daño (más las mejoras) al monstruo vivo más cercano, cada 0.6 s, alcance 220 px |
| Vida del monstruo | 30 en la oleada 1 (3 ataques) |

### Oleadas

- Al empezar la partida hay una cuenta regresiva de 5 s (`oleada` 0 y `descanso`). Entre oleadas el descanso dura 8 s.
- La oleada N trae `(8 + 4 × N)` enemigos, multiplicados por `1 + 0.4 × (jugadores − 1)`.
  Ejemplo: la oleada 1 trae 12 enemigos con 1 jugador y 26 con 4.
- La vida de cada enemigo sube 15 % por oleada (30, 35, 40, 44, 48...).
- Salen por tandas de 4 cada segundo, en un lugar libre a más de 700 px de los jugadores vivos,
  y nunca hay más de 60 vivos a la vez; el resto espera su turno.
- La oleada termina cuando mueren todos. Entonces:
  - los jugadores vivos recuperan 30 % de su vida máxima;
  - los **caídos reviven** con 50 % de vida;
  - cada jugador recibe una mejora (ver abajo).

### Mejoras

Al terminar cada oleada, **cada jugador recibe una mejora al azar**, sin elegir: más daño o más velocidad.

| Mejora | Efecto por nivel |
|--------|------------------|
| Daño | +2 al daño del ataque (base 10) |
| Velocidad | +0.3 px por cuadro (base 4.0), hasta el nivel 8 |

- **Penalización:** un jugador que **cayó** durante la oleada pierde la última mejora que había ganado
  (nunca baja del valor inicial) y después recibe la de esa oleada.
- Durante el descanso, `contenido` del jugador trae el aviso: `+1 VELOCIDAD`, o
  `+1 DAÑO   |   Caiste: -1 VELOCIDAD` si había caído. Se borra cuando empieza la siguiente oleada.
- Para que las dos mejoras suban a la vez en lugar de una al azar, cambia `AMBAS_MEJORAS` a `true`.
- El servidor manda `velocidad` ya calculada, pero el movimiento lo calcula el cliente: el servidor no la verifica.

Ejemplo del estado durante el descanso, con un jugador que cayó y fue penalizado:

```json
{"tipo":"posiciones","jugadores":[{"usuario":"Jugador1","contenido":"+1 DAÑO   |   Caiste: -1 VELOCIDAD","id":0,"x":148,"y":100,"vida":50,"vidaMax":100,"nivelDanio":2,"nivelVelocidad":1,"velocidad":4.3},{"usuario":"Jugador2","id":1,"x":196,"y":100,"vida":100,"vidaMax":100,"nivelDanio":1,"nivelVelocidad":3,"velocidad":4.9},null,null],"monstruos":[],"oleada":2,"descanso":5}
```

### Fin de la partida

Cuando todos los jugadores conectados están caídos, el servidor envía `"fin":true` y en `oleada` la
oleada alcanzada. Pasados 8 s reinicia todo: vida completa, posición inicial (con `correccion` para cada
cliente), sin enemigos, mejoras en 0 y la cuenta regresiva desde la oleada 1.
Si alguien entra mientras la partida está terminada, la reinicia para todos.
Si todos se van, la partida también se reinicia.

```json
{"tipo":"posiciones","jugadores":[{"usuario":"Jugador2","id":1,"x":196,"y":100,"vida":0,"vidaMax":100,"nivelDanio":0,"nivelVelocidad":2,"velocidad":4.6},null,null,null],"monstruos":[{"x":576,"y":96,"vida":35,"vidaMax":35}],"fin":true,"oleada":2,"restantes":5}
```

## 4. Salida

No hay mensaje de salida: cuando el cliente cierra el socket, el servidor libera el lugar y
envía el estado actualizado a los demás.

## Cómo ampliar el protocolo

1. Agrega los campos nuevos a `Mensaje` en **cliente y servidor**.
2. Define un nuevo valor de `tipo` y manéjalo en `ManejadorClientes` (servidor) y `Cliente` (cliente).
3. Documenta el mensaje aquí en el mismo Pull Request.
