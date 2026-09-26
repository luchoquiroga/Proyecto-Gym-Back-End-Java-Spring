# Despliegue: qué configurar antes del primer arranque

Fecha: 2026-09-15

Guía para levantar este backend desde cero en Render + Neon. Escrita para el caso
de **recrear producción completa** (base vacía, servicio nuevo), que es el
escenario donde más cosas pueden salir mal en silencio.

---

## 1. Neon (base de datos)

Creá la base y guardá la cadena de conexión. Dos cosas que importan:

**La base tiene que estar COMPLETAMENTE vacía.** No es un capricho: este proyecto
tiene `spring.flyway.baseline-on-migrate=true` con `baseline-version=1`. Si el
esquema tiene aunque sea una tabla y no existe todavía `flyway_schema_history`,
Flyway asume que esa base ya está "en la versión 1" y **se saltea la migración
`V1__baseline.sql`**, que es justamente la que crea todas las tablas. Después
`ddl-auto=validate` no encuentra nada y la app no arranca. Si Neon te crea algo
por defecto en el esquema `public`, borralo antes del primer deploy.

**La URL de Neon no es una URL JDBC.** Neon te da algo como:

```
postgresql://usuario:clave@ep-xxx.neon.tech/neondb?sslmode=require
```

y Spring necesita el prefijo `jdbc:` y la contraseña por separado:

```
DB_URL=jdbc:postgresql://ep-xxx.neon.tech/neondb?sslmode=require
DB_USER=usuario
DB_PASSWORD=clave
```

El `?sslmode=require` no es opcional con Neon: sin él, la conexión falla.

---

## 2. Render: variables de entorno

| Variable | ¿Obligatoria? | Qué poner |
|---|---|---|
| `DB_URL` | **Sí** | La URL JDBC de Neon, como arriba |
| `DB_USER` | **Sí** | Usuario de Neon |
| `DB_PASSWORD` | **Sí** | Contraseña de Neon |
| `JWT_SECRET` | **Sí** | Cadena aleatoria de **32 caracteres o más** (ver abajo) |
| `ADMIN_INITIAL_PASSWORD` | **Sí en el primer arranque** | Contraseña del admin inicial, mínimo 12 caracteres |
| `ADMIN_INITIAL_USERNAME` | No (default `admin`) | Nombre de usuario del admin inicial |
| `CORS_ALLOWED_ORIGINS` | Sí, cuando exista la web | Origen del frontend, ej. `https://mi-gym.vercel.app`. Default: `http://localhost:5173` |
| `PORT` | No | La define Render sola |
| `JWT_ACCESS_EXPIRATION_MS` | No | Default 30 min |
| `JWT_REFRESH_EXPIRATION_MS` | No | Default 30 días |
| `REFRESH_COOKIE_SECURE` | No | Default `true`, correcto en producción |
| `REFRESH_COOKIE_SAMESITE` | No | Default `None`, para que la cookie viaje entre dominios distintos |
| `RATE_LIMIT_TRUSTED_PROXIES` | No (default `1`) | Cuántos proxies propios hay delante de la app, para que el límite de intentos de login tome la IP real del usuario de `X-Forwarded-For`. `1` = solo Render; `2` = Vercel + Render. Ver §7 |
| `SWAGGER_ENABLED` | No (default `false`) | `true` publica Swagger (`/swagger-ui/index.html`, `/v3/api-docs`). Apagado en producción a propósito: muestra el mapa completo de la API. Prenderlo solo un rato si hace falta consultar el contrato desplegado |

### Sobre `JWT_SECRET`

`JwtService` hace `Keys.hmacShaKeyFor(secreto.getBytes())`: **usa los bytes
crudos del string, no lo decodifica de base64**, aunque el parámetro se llame
`secretoBase64`. En la práctica: el valor tiene que tener **al menos 32
caracteres**, o la librería rechaza la clave por débil y la app no arranca.

Generar uno:

```bash
openssl rand -base64 48
```

Cambiar este valor invalida todos los tokens emitidos: todo el mundo tiene que
volver a loguearse. En un despliegue nuevo no importa.

### Sobre `ADMIN_INITIAL_PASSWORD`

Es la contraseña del primer administrador, el que vas a usar para entrar y crear
al resto del staff. Antes había un `admin123` escrito en el código; ahora sale de
esta variable y **no tiene valor por defecto**.

Solo se usa cuando la tabla `usuarios` está vacía. Concretamente:

- Base vacía + variable configurada → crea el admin y sigue.
- Base vacía + variable ausente → **la app no arranca**, con un mensaje que dice
  exactamente qué falta. Es deliberado: es preferible no levantar a levantar con
  una credencial adivinable.
- Base con usuarios → la variable se ignora por completo.

Como vas a recrear la base, **configurala antes del primer deploy**. Cambiarla
después no cambia la contraseña del admin ya creado: eso se hace desde el API
(`PUT /api/v1/usuarios/cambiar-contrasena`).

---

## 3. Orden de los pasos

1. Crear la base en Neon y verificar que el esquema `public` esté vacío.
2. Cargar **todas** las variables de la tabla en Render.
3. Recién ahí, desplegar.

Invertir 2 y 3 es el error que cuesta caro: el primer arranque contra base vacía
sin `ADMIN_INITIAL_PASSWORD` falla, y sin `DB_*` ni siquiera conecta.

---

## 4. Qué pasa en el primer arranque

1. **Flyway** corre las migraciones en orden: `V1` crea todas las tablas, `V2`
   agrega el código de activación de clientes, `V3` el autor de cada pago, `V4`
   la unicidad del nombre de usuario.
2. **`DataInitializer`** siembra, solo si las tablas están vacías:
   - el usuario administrador, con las variables de arriba;
   - dos planes base: "Pase Mensual" ($32.500, 30 días) y "Pase Diario / Clase"
     ($1.000, 1 día). **Revisá que esos precios sean los actuales**: quedaron
     escritos en el código hace tiempo y se pueden editar después desde el API,
     pero conviene no arrancar con precios viejos.

---

## 5. Verificación post-deploy

```bash
curl https://<tu-servicio>.onrender.com/ping

curl -X POST https://<tu-servicio>.onrender.com/api/v1/usuarios/login \
  -H "Content-Type: application/json" \
  -d '{"nombre":"admin","contrasena":"<la que configuraste>"}'
```

Swagger está apagado en producción (ver `SWAGGER_ENABLED`): el contrato se consulta en
local, en `http://localhost:8080/swagger-ui/index.html`.

---

## 6. Cosas que conviene saber

**El vencimiento automático depende de que el servicio esté despierto.**
`VencimientoScheduler` corre con `@Scheduled(cron = "0 0 0 * * *", zone = ZONA_GIMNASIO)`,
es decir a medianoche de Argentina. En el plan gratuito de Render el
servicio **se duerme por inactividad**, y un servicio dormido no ejecuta tareas
programadas: si nadie usa el sistema de noche, los socios no pasan a MOROSO ni a
INACTIVO ese día. No es un bug del código, es el plan de hosting. Si importa,
las salidas son un plan que no duerma, un ping externo que lo mantenga vivo, o
recalcular el estado al consultarlo en vez de por tarea programada.

**Render despierto no mantiene despierta a Neon.** El pool de conexiones está configurado
(`spring.datasource.hikari.*` en `application.properties`) para cerrar todas sus
conexiones un minuto después del último uso y no mandar keepalives; con eso Neon ve
5 minutos sin actividad y suspende el cómputo aunque el servicio de Render siga prendido.
Para que esto se sostenga, lo que mantenga vivo a Render tiene que pegarle a `/ping`, que
no toca la base: un monitor apuntado a un endpoint con consultas (o un health check que
chequee la base) vuelve a despertar a Neon en cada pasada. El job de medianoche la
despierta una vez por día, y eso está bien.

**La zona horaria del contenedor es UTC, pero la app no depende de eso (desde la Fase 9).**
El `Dockerfile` no la fija y no hace falta: el cron declara su zona
(`America/Argentina/Buenos_Aires`, en `ZonaHorariaConfig.ZONA_GIMNASIO`) y todo "hoy" sale
del `Clock` de `ZonaHorariaConfig`, no de la JVM. Por eso no hay que agregar `TZ` en Render.
Lo que sí rompe esto es un `LocalDate.now()` sin argumento en código nuevo: vuelve a
preguntarle la fecha a la JVM, y desde las 21:00 de Argentina eso ya es el día siguiente.

**Cambiar `CORS_ALLOWED_ORIGINS` importa solo para el navegador.** La app de
escritorio no es un browser y no aplica CORS; la web sí, y si el origen no está
en la lista el login falla de una forma poco obvia (el request ni sale).

**Refresh tokens: dos pendientes conocidos, decididos el 2026-09-25.**
Ninguno se hace por ahora, a propósito:
- *No se detecta el reuso de un refresh token ya rotado.* Cerrar todas las sesiones de la
  cuenta cuando llega uno revocado tiene un falso positivo concreto: dos pestañas que
  refrescan casi a la vez (el front serializa el refresh dentro de una pestaña, no entre
  pestañas), y la segunda desloguearía al usuario de todo.
- *Las tablas `refresh_tokens` y `cliente_refresh_tokens` no se limpian.* Crecen unas pocas
  filas por login; con el volumen del gimnasio no molesta. Limpiarlas es un `DELETE` de datos
  y necesita autorización explícita antes de programarse.

---

## 7. La web publicada en Vercel (ticket B8 del front)

Publicada el 2026-09-26 en `https://gimnasioathletics.vercel.app` (plan Hobby,
producción desde `master` del front). La web le pide el API a su propio dominio
y `vercel.json` reenvía `/api/*` a Render, así la cookie de refresh es del
dominio de la web y Safari no la bloquea. Vercel solo sirve la web: todos los
datos siguen yendo a Render y a Neon. **Es un MVP**: el plan Hobby de Vercel es
para uso no comercial.

**Hecho (2026-09-24): el límite de intentos de login por IP.** Antes usaba
`getRemoteAddr()` a secas, y se asumía que detrás de Render eso era la IP del
balanceador, compartida por todos. Ahora `RateLimitFilter` toma la IP de
`X-Forwarded-For`, contando `RATE_LIMIT_TRUSTED_PROXIES` entradas desde la
derecha (las de la izquierda las puede escribir cualquiera), y si no hay header
usa `getRemoteAddr()`.

**Medido en producción el 2026-09-26**, con logins fallidos contra un email que
no existe:

| Prueba | Resultado | Qué quiere decir |
|---|---|---|
| Directo a Render, `X-Forwarded-For` falso distinto en cada intento | 429 al sexto | La clave es la IP real; el header del cliente no la cambia |
| Por Vercel, `X-Forwarded-For` falso | Nunca 429, aunque sea fijo | Vercel pisa el header: tampoco se puede elegir cupo |
| Por Vercel, sin header | Casi nunca 429 | La clave es la IP de salida de Vercel, que rota entre unas pocas |
| Por Vercel, `Origin` de la web | 401 (pasa el CORS) | Spring ve el pedido como del mismo origen (ver punto 2) |
| Directo a Render, `Origin` de la web | 403 "Invalid CORS request" | La web todavía no estaba en `CORS_ALLOWED_ORIGINS` |

Que el CORS vea el mismo origen detrás de Vercel dice que en Render se procesan
los `X-Forwarded-*` aunque la app no lo configure: Spring Boot detecta la
plataforma y activa solo `server.forward-headers-strategy`. Todo indica que eso
también explica el rate limit: Tomcat ya consume la entrada de `X-Forwarded-For`
que corresponde al cliente directo de Render, y detrás de Vercel ese cliente es
Vercel. La IP del usuario no llega a la posición que lee el filtro.

**Pendiente, para cuando se suba en serio** (decisión del dueño el 2026-09-26: no
bloquea el MVP):

1. **El rate limit detrás de Vercel agrupa por IP de Vercel, no por usuario.** Los
   usuarios de la web comparten unos pocos cupos de 5 intentos por minuto: un
   socio que se equivoca cinco veces puede bloquear un minuto el login de otro.
   `RATE_LIMIT_TRUSTED_PROXIES` **queda en `1`**: pasarlo a `2` (lo que decía
   este punto antes de medir) no se probó, pero por lo de arriba lo esperable es
   que no cambie nada. Si se quiere confirmar, se cambia y se repite la prueba
   "por Vercel, sin header". El arreglo:
   - un log temporal en `RateLimitFilter` para ver qué headers le llegan de
     verdad por Vercel (`X-Real-IP`, `X-Vercel-Forwarded-For`, `Forwarded`...);
   - leer la IP del usuario del que corresponda;
   - fijar `server.forward-headers-strategy` a mano, para no depender de la
     detección automática.

   Limitación que sigue aceptada: un header que Render deja pasar tal cual
   también lo puede escribir quien llame directo a `*.onrender.com`, salteando
   Vercel.
2. **`CORS_ALLOWED_ORIGINS=http://localhost:5173,https://gimnasioathletics.vercel.app`**
   (al 2026-09-26 todavía sin cargar). Hoy el login desde la web anda sin esto,
   por lo de arriba, pero depende de esa detección automática: si Spring deja de
   procesar `X-Forwarded-Host`, el login da **403 "Invalid CORS request"**. Si
   aparece ese 403, es esto. `localhost:5173` queda para `pnpm dev:prod` (la web
   local contra producción).
3. **`REFRESH_COOKIE_SAMESITE=Lax`** (al 2026-09-26 todavía sin cargar). `None`
   era para web y API en dominios distintos; detrás del proxy son el mismo sitio,
   y `Lax` suma protección contra CSRF. `REFRESH_COOKIE_SECURE` sigue en `true`.
4. **Cold start: decidido el 2026-09-25**, un cron externo que le pegue a `/ping`
   cada ~10 minutos, directo a `*.onrender.com` y no a la web. Lo arma el dueño.
   En el plan gratis la primera petición después de dormir tardó más de 90
   segundos (medido el 2026-09-24), y detrás de Vercel puede cortarse antes. Con
   el servicio despierto también corre el job de medianoche (§6), y como `/ping`
   no toca la base, Neon igual se duerme.
