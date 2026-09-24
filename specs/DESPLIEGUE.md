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

El contrato completo queda en `https://<tu-servicio>.onrender.com/swagger-ui/index.html`.

---

## 6. Cosas que conviene saber

**El vencimiento automático depende de que el servicio esté despierto.**
`VencimientoScheduler` corre con `@Scheduled(cron = "0 0 0 * * *")`, es decir a
medianoche del huso horario del servidor. En el plan gratuito de Render el
servicio **se duerme por inactividad**, y un servicio dormido no ejecuta tareas
programadas: si nadie usa el sistema de noche, los socios no pasan a MOROSO ni a
INACTIVO ese día. No es un bug del código, es el plan de hosting. Si importa,
las salidas son un plan que no duerma, un ping externo que lo mantenga vivo, o
recalcular el estado al consultarlo en vez de por tarea programada.

**La zona horaria del contenedor es UTC.** El `Dockerfile` no la fija, así que
"medianoche" para el scheduler es medianoche UTC, no de Argentina.

**Cambiar `CORS_ALLOWED_ORIGINS` importa solo para el navegador.** La app de
escritorio no es un browser y no aplica CORS; la web sí, y si el origen no está
en la lista el login falla de una forma poco obvia (el request ni sale).

---

## 7. Pendiente: la web publicada en Vercel (ticket B8 del front)

La web se va a publicar en Vercel pidiéndole el API a su propio dominio, y
`vercel.json` reenvía `/api/*` a Render (así la cookie de refresh es del
dominio de la web y Safari no la bloquea). **Está en pausa hasta que el dueño
decida el hosting.** Lo que ya está hecho y lo que falta:

**Hecho (2026-09-24): el límite de intentos de login por IP.** Antes usaba
`getRemoteAddr()`, que detrás del balanceador de Render es la IP del
balanceador: todos los usuarios compartían un solo cupo de 5 intentos por
minuto, y un socio que se equivocaba cinco veces le bloqueaba el login al
mostrador. Ahora `RateLimitFilter` toma la IP de `X-Forwarded-For`, contando
`RATE_LIMIT_TRUSTED_PROXIES` entradas desde la derecha (las de la izquierda las
puede escribir cualquiera). Limitación aceptada: con `2`, quien llame directo a
`*.onrender.com` salteando Vercel elige la IP que se toma y puede cambiar de
cupo en cada intento.

**Falta al publicar la web:**

1. **Confirmar el valor de `RATE_LIMIT_TRUSTED_PROXIES` contra el header real.**
   El `1` asume que Render agrega una sola entrada al final. No se verificó
   contra producción: si Render (o Cloudflare delante de Render) agrega más de
   una, el valor correcto es otro. La prueba: seis logins fallidos desde el
   celular con datos móviles no tienen que bloquear el login desde la PC en
   otra red. Con Vercel delante, pasar a `2` y repetir la prueba.
2. **`CORS_ALLOWED_ORIGINS`:** agregar el origen de la web publicada
   (`https://<proyecto>.vercel.app`, y el dominio propio si se usa) **sin sacar**
   `http://localhost:5173`, separados por coma. Aunque con el proxy el navegador
   no hace un pedido cruzado, Vercel reenvía el header `Origin` y Spring rechaza
   un origen desconocido con **403 "Invalid CORS request"**. Si el login desde la
   web publicada da ese 403, es esto.
3. **`REFRESH_COOKIE_SAMESITE=Lax`.** `None` era para web y API en dominios
   distintos; detrás del proxy son el mismo sitio, y `Lax` suma protección
   contra CSRF. `REFRESH_COOKIE_SECURE` sigue en `true`.
4. **Cold start (decisión del dueño).** En el plan gratis, la primera petición
   después de dormir tardó más de 90 segundos (medido el 2026-09-24) y detrás de
   Vercel puede cortarse antes. Opciones: un cron externo que pegue a `/ping`
   cada ~10 minutos, o el plan pago. Anotar acá la que se elija. Resuelve también
   el problema del scheduler dormido de §6.
