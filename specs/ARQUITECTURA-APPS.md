# Arquitectura de apps: quién consume qué del API

Fecha: 2026-09-15
Estado: vigente — actualizado con la Fase 2 del replanteo; decisiones
abiertas al final

Este documento delimita las tres apps que van a pegarle a este backend y qué
superficie del API le corresponde a cada una. Es el paso previo a escribir la
spec de mobile: sin esto, "agregar un endpoint para mobile" no tiene forma de
responderse bien, porque no está definido qué es mobile.

Complementa a `AUTHZ-MATRIX.md`, no lo reemplaza. La matriz responde **"quién
tiene permiso"** (es la fuente de verdad de seguridad). Este archivo responde
**"quién lo llama"** (es un mapa de producto). Un endpoint puede estar
permitido para un rol y aun así no ser consumido por ninguna app todavía.

---

## 1. La foto de hoy (no la que se planea)

| App | Estado real | Principal | Cómo habla con el API |
|---|---|---|---|
| Escritorio (Swing) | **En producción**, único consumidor real | `Usuario` (ADMIN/GERENCIA) | HTTP directo contra Render (`ApiConfig.BASE_URL`) |
| Web | No existe todavía | — | — |
| Mobile | No existe todavía | — | — |

Endpoints que la app Swing consume hoy (leídos de su código, no de un plan):
`/usuarios/login`, `/usuarios`, `/clientes`, `/clientes/{id}`, `/planes`,
`/planes/{id}`, `/pagos`, `/dashboard/ganancias-mensuales`.

Consecuencia a tener presente: el principal `CLIENTE` y todo el flujo de
registro/login de socios ya está implementado y desplegado, pero **ninguna app
lo usa**. Es superficie de ataque viva sin producto encima. No es motivo para
borrarlo, sí para no olvidar que existe.

---

## 2. Las tres apps y sus audiencias

Decisiones tomadas:

- **Mobile → exclusivamente para el rol `ADMIN`.** No es una app de socios y
  tampoco es para GERENCIA. La razón es de negocio, no de ergonomía: mobile
  existe para que el dueño mire **métricas del negocio** (cuánto entra y de
  dónde) desde el celular, y eso es justamente lo que GERENCIA no tiene por
  qué ver. GERENCIA controla clientes; no necesita saber cuánta plata hace el
  gimnasio ni con qué planes.
- **Escritorio → a futuro, un envoltorio de la web** (el modelo Discord: una
  ventana de escritorio que carga la app web, no una UI nativa aparte).

Eso lleva a una conclusión que conviene decir explícita porque es el punto de
todo el documento:

> **Tres apps no son tres consumidores del API.**
> Si el escritorio pasa a ser un shell de la web, deja de hablar con el API
> por su cuenta: el consumidor pasa a ser la web. El destino tiene **dos**
> consumidores (web y mobile), no tres.

| App | Audiencia | ¿Consumidor del API? | Superficie propia |
|---|---|---|---|
| Web | Staff **y** socios (dos áreas, un solo frontend) | Sí | La suya |
| Escritorio | Staff (ADMIN y GERENCIA) | No — hereda la de la web | **Ninguna** |
| Mobile | **Solo ADMIN** | Sí | Subconjunto, orientado a métricas |

Mientras la migración no ocurra, la Swing actual sigue siendo un consumidor
directo y este documento describe un destino, no el presente.

---

## 3. Regla de oro: el canal no es un rol

La autorización se decide por **principal** (`Usuario` ADMIN/GERENCIA vs
`Cliente`), nunca por la app desde la que llega el request.

- Mobile **no** obtiene ningún permiso que la web-staff no tenga. Es un
  subconjunto de la misma superficie, recortado por audiencia (solo ADMIN) y
  por ergonomía de pantalla chica — nunca ampliado.
- Ninguna de las tres apps justifica un rol nuevo en la matriz. Si en algún
  momento parece que sí, eso es la señal de escribir una spec completa, no de
  agregar el rol.
- El backend no debe tener un `if (esMobile)`. Un header de canal sería un
  control de acceso del lado del cliente, es decir, ninguno.

Esto no es teórico: los incidentes del `AUTHZ-MATRIX.md` § Historial salieron
de agregar una audiencia nueva sin releer el modelo de permisos entero.

### 3.1 Qué separa a ADMIN de GERENCIA

La intención de negocio, escrita para que no se pierda:

- **GERENCIA opera clientes.** Da de alta, edita, busca, activa, cobra en el
  mostrador. Es la operación diaria del gimnasio.
- **ADMIN además ve el negocio.** Cuánto entra, con qué planes, en qué meses.
  Es información del dueño.

Durante un tiempo el código **no** sostuvo esa separación: `/dashboard` estaba
restringido a ADMIN, pero los datos crudos que lo alimentan no
(`GET /api/v1/pagos` y `/pagos/buscar` aceptaban GERENCIA), así que con el
listado completo se reconstruían los ingresos a mano. Esconder el dashboard era
una cortina, no un permiso.

**Cerrado el 2026-09-15** (Fase 2 del replanteo): toda lectura de pagos quedó en
ADMIN. GERENCIA conserva `POST /pagos`, o sea cobrar, y para saber si un socio
está al día usa el estado y la fecha de vencimiento que expone el propio socio
— un estado y una fecha, sin ningún monto adentro. La línea entre los dos roles
no pasa entre "puntual y global" sino entre **operar** y **ver**.

---

## 4. Mapa de superficie por app

`✓` = la app lo usa · `—` = no lo usa · `(hoy)` = lo usa la Swing actual

**Web-staff** = ADMIN y GERENCIA (cada uno ve lo que su rol permite).
**Mobile-admin** = solo ADMIN; un GERENCIA que se loguee en mobile no debería
tener pantallas. **Web-socio** = principal `CLIENTE`.

### `/api/v1/usuarios` (staff)

| Endpoint | Permitido a | Web-staff | Mobile-admin | Web-socio |
|---|---|---|---|---|
| POST `/login` | Público | ✓ | ✓ | — |
| POST `/refresh` | Público (cookie) | ✓ | ⚠ ver §6 | — |
| POST `/logout` | Público | ✓ | ✓ | — |
| POST `` (alta staff) | ADMIN | ✓ (hoy) | — | — |
| PUT `/cambiar-contrasena` | ADMIN | ✓ | ✓ | — |
| DELETE `/{id}` | ADMIN | ✓ | — | — |

Criterio: la administración de staff (alta y baja de usuarios) es trabajo de
escritorio, no de celular. Se deja fuera de mobile a propósito.

### `/api/v1/clientes`

| Endpoint | Permitido a | Web-staff | Mobile-admin | Web-socio |
|---|---|---|---|---|
| POST `/registro` | Público (código activación) | — | — | ✓ |
| POST `/login` | Público | — | — | ✓ |
| POST `/refresh` · `/logout` | Público | — | — | ✓ |
| GET `` (listado) | ADMIN, GERENCIA | ✓ (hoy) | ✓ | — |
| GET `/buscar` | ADMIN, GERENCIA | ✓ | ✓ | — |
| GET `/{id}` | staff, o el propio CLIENTE | ✓ (hoy) | ✓ | ✓ (el suyo) |
| POST `` (alta) | ADMIN, GERENCIA | ✓ | — | — |
| PUT `/{id}` | ADMIN, GERENCIA | ✓ | — | — |
| PATCH `/{id}/estado` | ADMIN, GERENCIA | ✓ | — | — |
| DELETE `/{id}` | ADMIN, GERENCIA | ✓ | — | — |

Criterio: mobile es la app de **consulta** del dueño, no la de mostrador. El
alta, la edición y la activación de un cliente son operación diaria de
GERENCIA, y GERENCIA no entra a mobile. Al ADMIN en el celular le alcanza con
buscar y mirar. Ver la decisión abierta #1 en §7 si querés que ADMIN también
pueda operar desde el celular.

### `/api/v1/pagos`

| Endpoint | Permitido a | Web-staff | Mobile-admin | Web-socio |
|---|---|---|---|---|
| GET `` (listado) | **ADMIN** | ✓ solo ADMIN | ✓ | — |
| GET `/buscar` | **ADMIN** | ✓ solo ADMIN | ✓ | — |
| GET `/cliente/{clienteId}` | ADMIN, o el dueño | ✓ solo ADMIN | ✓ | ✓ (los suyos) |
| GET `/{id}` | ADMIN, o el dueño | ✓ solo ADMIN | ✓ | ✓ (el suyo) |
| POST `` (registrar) | ADMIN, GERENCIA | ✓ | — | — |

Desde el 2026-09-15 ninguna lectura de pagos acepta GERENCIA: en la web-staff
estas pantallas existen solo para ADMIN. GERENCIA cobra (`POST`) y se guía por
el estado y la fecha de vencimiento del socio, que no llevan montos.

Criterio: mobile lee la plata, no la mueve. Registrar un pago es el acto de
mostrador por excelencia y pasa por la web/escritorio, donde está quien cobra.
El listado y la búsqueda de pagos sí van a mobile: son, junto con el
dashboard, **el caso de uso que justifica la app**.

### `/api/v1/planes`

| Endpoint | Permitido a | Web-staff | Mobile-admin | Web-socio |
|---|---|---|---|---|
| GET `` · `/{id}` · `/buscar` | cualquier autenticado | ✓ (hoy) | ✓ | ✓ |
| POST · PUT · DELETE | ADMIN | ✓ (hoy) | — | — |

Criterio: el catálogo se lee en todos lados; se edita solo desde la web.

### `/api/v1/dashboard`

| Endpoint | Permitido a | Web-staff | Mobile-admin | Web-socio |
|---|---|---|---|---|
| GET `/ganancias-mensuales` | ADMIN | ✓ (hoy) | ✓ | — |

**Es la razón de ser de mobile.** Ya está restringido a ADMIN en
`SecurityConfig`, que coincide exactamente con la audiencia de la app — pero
ver §3.1: la restricción es efectiva para este endpoint y no para los datos
que lo alimentan.

### `/ping`

Público, salud del servicio. Cualquier app puede usarlo para detectar backend
caído antes de mostrar un error feo de login.

---

## 5. Qué NO cambia en el backend por este documento

Ordenar quién llama a qué **no requiere endpoints nuevos**. La superficie que
mobile necesita ya existe y ya está permitida para ADMIN/GERENCIA. El trabajo
que este documento genera es de las apps, no del API.

Dicho de otro modo: no hay que codear nada del backend para "soportar mobile",
salvo lo de §6.

---

## 6. El problema real que mobile va a destapar: el refresh token

`UsuarioController.refresh` devuelve el refresh token en una **cookie
HttpOnly**. Eso es correcto para un browser y no funciona igual fuera de uno.

La app Swing de hoy directamente **no implementa refresh**: guarda el access
token en memoria (`ApiClient.setJwtToken`) y cuando expira a los 30 minutos
devuelve un 401 con "token expirado". O sea, el problema ya existe hoy y está
resuelto haciendo que el usuario se vuelva a loguear.

Antes de mobile hay que decidir esto, y **es un cambio al esquema de
autenticación, así que lleva spec propia y `/security-review` obligatorio**:

- Opción A — mobile también se re-loguea al expirar. Cero cambios en el
  backend, peor experiencia de uso.
- Opción B — devolver el refresh token en el body para clientes no-browser.
  Cómodo, y exactamente el terreno donde ya hubo un incidente (2026-09-13: un
  refresh token filtrado servía como access token por 30 días). Si se toma
  este camino, el refresh token tiene que seguir siendo rechazado fuera de
  `/refresh` por `JwtAuthenticationFilter`, y hay que pensar dónde lo guarda
  la app.

Recomendación: empezar por A. Es gratis, no toca seguridad, y deja la decisión
para cuando la app exista y se sepa si realmente molesta.

---

## 7. Decisiones abiertas

1. **¿ADMIN opera desde mobile, o solo consulta?** El mapa de §4 lo dejó como
   app de solo lectura (buscar, mirar, métricas), sin alta ni cobro, porque
   quien opera el mostrador es GERENCIA y GERENCIA no entra a mobile. Si el
   dueño igual quiere poder cobrar o activar un cliente desde el celular, se
   agregan esas filas — no requiere cambios de backend, los permisos ya
   existen.
2. **GERENCIA ve todos los pagos (§3.1).** Hoy `GET /pagos` y `GET
   /pagos/buscar` están abiertos a GERENCIA, así que esconderle `/dashboard`
   no le esconde la información del negocio. Si la intención es que GERENCIA
   no vea el agregado, hace falta separar "pagos de un cliente puntual"
   (GERENCIA sí) de "listado global" (solo ADMIN). Es cambio de modelo de
   autorización: spec propia + `AUTHZ-MATRIX.md` + `/security-review`.
3. **¿Cuándo migra el escritorio a shell de la web?** Hasta que eso pase, la
   Swing sigue siendo un consumidor directo y con superficie propia. El
   documento describe el destino; el presente es §1.
4. **¿La web es un solo frontend con dos áreas, o dos frontends?** Afecta al
   escritorio: si el shell carga la web entera, staff y socios comparten
   build, y hay que asegurar que el área de staff no sea alcanzable por un
   socio logueado (eso es autorización de frontend, y el backend igual tiene
   que sostenerla por su cuenta).
5. **Refresh token en mobile** — §6.

---

## 8. Cómo usar este documento

- Al escribir la spec de un feature nuevo, indicar **qué apps lo consumen**
  usando los nombres de acá (web-staff, web-socio, mobile-admin).
- Al agregar un endpoint, agregar la fila en `AUTHZ-MATRIX.md` (obligatorio,
  es seguridad) y la columna de consumo acá (recomendado, es producto).
- Si una app necesita algo que este mapa le niega, eso es una conversación de
  producto antes que un cambio de código: puede que la respuesta correcta sea
  que esa app no debería hacer eso.
