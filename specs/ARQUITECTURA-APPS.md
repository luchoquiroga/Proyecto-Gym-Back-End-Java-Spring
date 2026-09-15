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
| Web | **Maqueta navegable** (React 19 + Vite + TS), escrita el 3–5/09 contra el contrato viejo; auth de staff real, cuatro lecturas, ninguna escritura | `Usuario` (solo ADMIN: a GERENCIA la bloquea a propósito, y el portal del socio no se puede alcanzar) | axios con access token en memoria + silent refresh por cookie |
| Mobile | No existe todavía | — | — |

Corregido el 2026-09-15: hasta ese día este documento decía que la web no
existía. Existe, en `C:\Users\lucia\Project Visual Studio Code\Gym-Project-Front-End`
(sin git todavía), y sus tickets de migración al contrato nuevo están en
`2026-09-15-tickets-web.md`. Lo que no existe es un área para GERENCIA: la web
la manda a "acceso restringido", decisión del diseño viejo que hay que revertir
para que la web pueda reemplazar al escritorio.

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
| Mobile | **Solo ADMIN** — *congelada el 2026-09-15, ver §2.1* | Sí, cuando se retome | Subconjunto, orientado a métricas |

Mientras la migración no ocurra, la Swing actual sigue siendo un consumidor
directo y este documento describe un destino, no el presente.

---

## 2.1 Alcance definido de cada área (2026-09-15)

Definido por el dueño del proyecto en la conversación del 2026-09-15, cuando la
web pasó a ser también el futuro reemplazo del escritorio. Esto es **el alcance
comprometido**, no una lista de deseos: lo que no está acá no se construye sin
volver a esta sección.

**La web tiene dos portales**, y el de staff tiene adentro dos niveles:

### Portal del socio (`CLIENTE`) — solo lectura

Ve el estado general de su registro: si está al día, **cuántos días le faltan
para pagar la cuota** (se calcula restando `fechaVencimiento` contra hoy) y sus
datos, por ejemplo qué teléfono tiene asociado.

**No modifica nada, por ahora.** Ni sus datos de contacto ni su email. Si un
teléfono está mal cargado, lo corrige el staff. Consecuencia a tener presente:
el email tampoco lo edita nadie —es la identidad de login del socio— así que un
socio que pierde el acceso a su casilla hoy no tiene salida. Anotado, sin
resolver.

### Portal de staff, nivel GERENCIA (y ADMIN, que puede todo)

Es el trabajo de mostrador, o sea lo que hoy hace la app de escritorio:

- Socios: **listar, buscar, crear, modificar e inhabilitar**.
- **Crear pagos** (cobrar).
- **Consultar** planes.

Nada de esto muestra montos históricos ni agregados: GERENCIA cobra y opera,
pero no lee la caja. Para saber si un socio está al día usa estado +
`fechaVencimiento`.

### Portal de staff, nivel ADMIN

Todo lo anterior, más:

- **Dashboard**, con el desglose de las ganancias — listando los pagos del mes,
  no solo el total agregado.
- Planes: **modificar y eliminar**.
- **Anular pagos** cargados por error (ver más abajo: anular, no borrar).
- **Crear cuentas de staff** (otros administradores o gerentes) y
  **desactivarlas**.

### Decisiones que salieron de definir esto

1. **Un pago mal cargado se anula, no se borra ni se edita.** El pago queda en
   la tabla marcado como anulado, con quién lo anuló, cuándo y por qué; el
   correcto se carga de nuevo. Borrarlo tenía tres efectos invisibles desde la
   pantalla: perdía `registrado_por` (la auditoría de la Fase 1), movía hacia
   atrás la `fechaVencimiento` del socio —que se calcula del último pago— sin
   que nada lo avisara, y cambiaba retroactivamente las ganancias de un mes ya
   cerrado. Editar el monto es peor: cambia la plata sin dejar rastro de cuál
   era antes.
2. **Anular es solo de ADMIN.** Es la misma línea de siempre: GERENCIA cobra,
   pero no toca la caja ya registrada. Si GERENCIA se equivoca, avisa.
3. **El estado ACTIVO de un socio lo determina únicamente un pago válido.**
   `PATCH /clientes/{id}/estado` deja hoy que ADMIN o GERENCIA lo pongan ACTIVO
   a mano, que es la puerta de atrás de la regla "no hay pago parcial" de la
   Fase 1. Pasa a servir solo para inhabilitar.
4. **Mobile queda congelada.** No aparece en el alcance, y con la web
   absorbiendo además el trabajo del escritorio son demasiados frentes
   abiertos a la vez. La decisión de §2 (mobile es del dueño, solo consulta)
   sigue en pie para cuando se retome; lo que se congela es construirla ahora.

Los tickets de backend que hacen falta para sostener este alcance están en
`2026-09-15-fase6-backend-para-la-web.md`; los de la web, en
`2026-09-15-tickets-web.md`.

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

Cerradas el 2026-09-15 (ver §2.1), se dejan anotadas para no reabrirlas:

- ~~¿ADMIN opera desde mobile o solo consulta?~~ Mobile queda congelada.
- ~~GERENCIA ve todos los pagos.~~ Cerrado por la Fase 2 del replanteo: las
  cuatro lecturas de pagos son de ADMIN.
- ~~¿La web es un frontend con dos áreas?~~ Sí: dos portales, y el de staff con
  dos niveles adentro.

Siguen abiertas:

1. **¿Cuándo migra el escritorio a shell de la web?** La condición no es de
   calendario sino de alcance: recién cuando la web cubra el mostrador (socios
   + cobro) tiene sentido, porque es lo que el escritorio hace hoy.
2. **El socio no puede cambiar su email**, y es su identidad de login. Si
   pierde el acceso a la casilla, hoy no hay salida. No bloquea el alcance
   definido, pero va a aparecer en cuanto el portal tenga uso real.
3. **Refresh token fuera del browser** — §6. Deja de ser urgente al congelar
   mobile, pero vuelve con ella.

---

## 8. Cómo usar este documento

- Al escribir la spec de un feature nuevo, indicar **qué apps lo consumen**
  usando los nombres de acá (web-staff, web-socio, mobile-admin).
- Al agregar un endpoint, agregar la fila en `AUTHZ-MATRIX.md` (obligatorio,
  es seguridad) y la columna de consumo acá (recomendado, es producto).
- Si una app necesita algo que este mapa le niega, eso es una conversación de
  producto antes que un cambio de código: puede que la respuesta correcta sea
  que esa app no debería hacer eso.
