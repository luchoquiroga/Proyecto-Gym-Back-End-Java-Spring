> **La copia viva de este documento está en el repo del front**, en
> `Gym-Project-Front-End\specs\TICKETS.md`. Este archivo queda como registro de
> cómo se decidió el backlog; si hay que tocar un ticket, se toca allá.

# Tickets de UI: la web

Fecha: 2026-09-15
Estado: backlog
Repo: `C:\Users\lucia\Project Visual Studio Code\Gym-Project-Front-End`
(React 19 + Vite + TS, react-query, zustand, axios, tailwind; sin repositorio git
todavía)

## 1. Por qué existe este documento

El replanteo del backend cerró cinco fases y el contrato quedó quieto
(`2026-09-15-replanteo-backend.md`). Estos son los tickets de la web contra ese
contrato.

**Corrección a §10 del replanteo:** ahí dice que la web "todavía no existe" y
que por eso "nace directamente contra el contrato nuevo, así que no acumula
ticket de migración". Es falso. La web existe, es del 3 al 5 de septiembre —
anterior a todo el replanteo— y está escrita contra el contrato viejo. Sí
acumula migración, y de las tres apps es la que más superficie rota tiene.

**Alcance:** el de `ARQUITECTURA-APPS.md` §2.1, definido el 2026-09-15. Los
tickets de abajo lo cubren; lo que no está en ese alcance no está acá.

## 2. Qué hay hoy

Una maqueta navegable con autenticación real de staff:

- **Auth que funciona:** login contra `/api/v1/usuarios/login`, access token solo
  en memoria (zustand, nunca en localStorage), silent refresh en
  `AuthInitializer` y reintento automático del 401 con cola de peticiones en el
  interceptor de axios. Es lo mejor que tiene y no hay que tocarlo.
- **Pantallas:** Login, Home, AdminDashboard, ClientesPage, PlanesPage,
  PagosPage, ClientePortal, AccesoRestringido.
- **Datos:** cuatro llamadas de lectura (`/dashboard/ganancias-mensuales`,
  `/clientes`, `/pagos`, `/planes`). **Ninguna de escritura**: los botones
  "Registrar Socio" y "Registrar Pago" no hacen nada.

## 3. La decisión que ordena todo el resto

Hoy la web **le prohíbe entrar a GERENCIA**: `RoleRoute` la manda a
`/acceso-restringido`, y `Login` también, con un comentario que dice que es a
propósito. Eso venía del diseño viejo, donde GERENCIA se quedaba en la app
Swing.

Ese supuesto se cae con el objetivo actual —que la web reemplace al escritorio—
porque **GERENCIA es exactamente el rol que usa el escritorio**: opera socios y
cobra. Una web sin área de GERENCIA no puede reemplazar nada.

**Entonces: la web tiene tres áreas, no dos.**

| Área | Quién entra | Para qué |
|---|---|---|
| staff-gerencia | GERENCIA (y ADMIN, que puede todo) | mostrador: socios y cobrar |
| staff-admin | ADMIN | lo anterior + plata y cuentas de staff |
| socio | CLIENTE | su propia ficha |

La división entre las dos primeras no es "lectura vs escritura": GERENCIA
escribe (da de alta socios, cobra) y aun así no ve ningún dato monetario. Es
**operar vs ver** (§2.2 del replanteo). Lo que cambia entre ADMIN y GERENCIA no
son las pantallas de socios, que son las mismas: es que ADMIN además tiene
dashboard, pagos y staff.

**El canal nunca es un rol.** La web no decide permisos: los pide y el backend
responde 403. Si una pantalla se esconde, se esconde por el rol del principal,
igual que hoy hace `RoleRoute`.

---

## 4. Tickets

Ordenados: primero lo que está roto o miente, después lo que falta.

### W1 — Sacar los mocks de los `catch` (bloqueante, hacer primero)

Las cuatro queries tienen la misma forma:

```ts
try { const res = await api.get('/api/v1/clientes'); return res.data; }
catch { return [ /* datos inventados */ ]; }
```

O sea que **cuando el backend falla, la pantalla muestra datos falsos sin avisar
nada**. Con el contrato cambiando debajo, esto es lo peor que puede pasar: el
dashboard muestra `$485.000` de ganancias inventadas y parece que funciona. Un
error tiene que verse como un error.

Sacar los `catch` con mock y usar los estados que react-query ya da (`isError`,
`error`), con un mensaje en pantalla. Si hace falta una maqueta sin backend, va
detrás de un flag explícito (`VITE_USE_MOCKS`), nunca de un `catch`.

**Hasta que esto no esté hecho, ninguno de los tickets siguientes se puede
verificar**: los errores de contrato quedan tapados por el mock.

### W2 — Los listados ahora vienen paginados

`GET /clientes` y `GET /pagos` ya no devuelven un array. Devuelven
(`PaginaResponse`):

```json
{ "contenido": [...], "pagina": 0, "tamanio": 20, "totalElementos": 137, "totalPaginas": 7 }
```

Rompe en tres lugares, y **no** lo salva el `catch` de W1 porque la respuesta es
200: el error aparece al renderizar, cuando se llama `.filter`/`.length` sobre
un objeto.

- `ClientesPage`: `clientes?.filter(...)`
- `PagosPage`: itera el array directo
- `AdminDashboard`: `clientes?.length` y `clientes?.filter(...)` para los
  contadores → hoy contaría mal aunque no reviente.

Los parámetros son los de Spring: `?page=0&size=20&sort=nombre,asc`. Conviene un
tipo `PaginaResponse<T>` compartido y un hook de paginación, porque es la misma
forma para los dos listados.

Ojo con `AdminDashboard`: hoy cuenta socios trayendo **la lista entera**. Con
paginación, `totalElementos` es el número que quiere; los activos no salen de
ahí y necesitan otra cosa (ver W9).

`GET /planes` **no** se pagina (son un puñado de filas): `PlanesPage` sigue
funcionando igual.

### W3 — El socio ahora trae vencimiento, y `VENCIDO` no existe

`ClienteResponse` es hoy: `id`, `nombre`, `apellido`, `telefono`, `documento`,
`email`, `estado`, `fechaVencimiento`, `planVigente`.

- **`documento` es obligatorio y único** (Fase 8), y es lo que distingue a dos
  socios que se llaman igual. Tiene que aparecer en el listado y en la ficha: es
  la columna que hace que dos "Juan Pérez" dejen de ser dos filas idénticas.
  Viene siempre normalizado (sin puntos), aunque se haya cargado con puntos.
- **`planVigente`** (id + nombre, sin precio) es el plan del último pago del
  socio. Null si nunca pagó, igual que `fechaVencimiento`.

- **`fechaVencimiento` es el dato nuevo de la Fase 2** y es la razón por la que
  GERENCIA puede trabajar sin ver pagos: responde "¿este socio está al día?" sin
  ningún monto. Hay que mostrarlo en la ficha y en el listado.
- El tipo del front declara `estado: 'ACTIVO' | 'INACTIVO' | 'VENCIDO'`.
  **`VENCIDO` no existe en el backend**, nunca existió: el enum es `ACTIVO`,
  `MOROSO`, `INACTIVO`. Hoy un socio moroso no matchea ningún caso y cae en el
  estilo por defecto.
- `email` es nuevo en la respuesta y sirve para saber si el socio ya activó su
  cuenta del portal.

### W4 — Abrirle la web a GERENCIA

Implementa la decisión de §3.

- Sacar el corte por `GERENCIA` de `RoleRoute` y de la redirección de `Login`.
- Rutas de socios (`/clientes` y sus acciones) accesibles a `['ADMIN', 'GERENCIA']`.
- `/admin/dashboard` y `/admin/pagos` siguen siendo solo `['ADMIN']` — es la
  regla del backend, que devuelve 403 a GERENCIA en las cuatro lecturas de pagos.
- La navegación de `AdminLayout` tiene que ocultar lo que el rol no puede usar,
  para que GERENCIA no vea ítems que la llevan a un 403.
- `/acceso-restringido` deja de ser el destino de GERENCIA (sigue sirviendo para
  un rol sin área).

Conviene renombrar las rutas `/admin/*` a algo neutro (`/staff/*`): dejarlas
como están hace que la mitad de las pantallas de GERENCIA vivan bajo una URL que
dice "admin".

### W5 — Cobrar sin ver la tabla de pagos

Es el ticket que desbloquea el reemplazo del escritorio, y **no es mover una
pantalla de lugar**: hoy el cobro vive dentro de la pantalla de pagos, que
GERENCIA no puede ver.

Una acción "Registrar pago" **desde la ficha del socio**, disponible para ADMIN
y GERENCIA: elegir plan (`GET /planes`) e importe, y `POST /api/v1/pagos`.
Al volver, refrescar el socio para que se vea el `fechaVencimiento` nuevo.

Dos errores del backend que la pantalla tiene que mostrar bien:
- **400 por monto menor al precio del plan.** No hay pago parcial: se rechaza
  entero y no se registra nada. El mensaje viene en `mensaje` del `ErrorResponse`
  y hay que mostrarlo tal cual, no un "error al guardar" genérico.
- **400 por pago retroactivo ya vencido**: se registra pero no activa al socio.

La respuesta **no** trae quién cobró (`registradoPor` no está en `PagoResponse`,
a propósito), así que no intentes mostrarlo.

### W6 — Alta y edición de socios

Hoy el botón "Registrar Socio" no hace nada. ADMIN y GERENCIA.

- **Alta** (`POST /clientes`): nombre, apellido, telefono y **documento**. El
  socio nace `INACTIVO` hasta su primer pago; el estado no se manda.
- **El documento es obligatorio** y el backend devuelve **409** si ya pertenece
  a otro socio, con un mensaje que dice cuál es. Ese 409 hay que mostrarlo tal
  cual: es el caso de "esta persona ya está cargada", que el mostrador necesita
  entender, no un error genérico. Se puede escribir con o sin puntos: el backend
  lo normaliza antes de guardarlo, así que no hace falta validar el formato en
  el front (sí conviene no dejar mandar vacío).
- **Devuelve un dato de un solo uso:** `ClienteAltaResponse` trae el
  `codigoActivacion`, que el socio necesita para crearse la cuenta del portal.
  **Es la única vez que ese código viaja**: hay que mostrarlo en pantalla de
  forma que se pueda copiar o imprimir, porque si se pierde no se puede volver a
  consultar.
- **Edición** (`PUT /clientes/{id}`): datos de contacto **y documento**. Un
  documento mal tipeado en el alta hay que poder corregirlo, y el único que
  puede es el staff; también devuelve 409 si choca con otro socio. El email, en
  cambio, no se edita desde el mostrador porque es la identidad de login del
  socio en el portal y cambiárselo le sacaría el acceso sin que se entere. El
  formulario no debe ofrecer ni email ni contraseña.
- **Baja** (`PATCH /clientes/{id}/estado` con `{"estado":"INACTIVO"}`): pone al
  socio en INACTIVO y nunca borra la fila. La UI tiene que decir "dar de baja",
  no "eliminar" — y no hay ningún DELETE que llamar, porque no existe. El mismo
  endpoint rechaza `ACTIVO` y `MOROSO`: esos los determina el sistema.
- **Buscar** (`GET /clientes/buscar?nombre=`) ahora devuelve una **lista** con
  coincidencia parcial insensible a mayúsculas, no un único resultado. Hoy
  `ClientesPage` filtra en memoria sobre la lista completa: con paginación eso
  pasa a filtrar solo la página actual, así que el buscador tiene que pegarle al
  endpoint.

### W7 — Gestión de cuentas de staff (solo ADMIN)

No existe ninguna pantalla. Es lo que cierra la Fase 5.

- Alta (`POST /usuarios`: nombre, contrasena, rol).
- **Baja y reactivación, un solo endpoint** (`PATCH /usuarios/{id}/activo` con
  `{"activo": false}` o `true`). Desactivar no borra: la cuenta deja de poder
  entrar y pierde sus sesiones. No hay `DELETE` que llamar.
- **Reactivar** con ese mismo endpoint hace
  falta de verdad, no es un extra. Una cuenta dada de baja sigue ocupando su
  nombre de login, así que si se dio de baja a la persona equivocada **no se
  puede arreglar creando otra igual**: hay que reactivar esa. El backend
  devuelve un 400 que lo explica; la UI tiene que ofrecer el camino.
- `UsuarioResponse` trae `activo`: el listado tiene que distinguir cuentas
  vigentes de dadas de baja.
- **Reset de contraseña de otro** (`PUT /usuarios/{id}/contrasena`): solo ADMIN,
  y el backend lo rechaza contra la propia cuenta. Esa es la razón de W8.

Hoy no hay ningún `GET /usuarios` (ni listado ni por id) en el backend: esta
pantalla lo necesita y **es un ticket de backend**, no de acá.

### W8 — Cambiar la propia contraseña (ADMIN y GERENCIA)

No existe la pantalla, y ahora es el único camino para que un empleado cambie su
clave.

`PUT /usuarios/cambiar-contrasena` con `{ contrasenaActual, nuevaContrasena }`.
A quién se le cambia sale del token, **no del body** — el campo `nombre` que
tenía antes ya no existe.

- Contraseña actual equivocada → **400**, no 401. Es a propósito: la sesión es
  válida, lo que está mal es un dato del formulario. No dispares el logout.
- Al cambiarla, el backend **revoca todas las sesiones de esa cuenta**, así que
  el refresh siguiente va a fallar: conviene cerrar sesión y mandar al login con
  un mensaje claro, en vez de esperar a que el interceptor lo descubra solo.

### W9 — Dashboard con los números que el backend da

`/api/v1/dashboard/ganancias-mensuales` sigue existiendo y es solo ADMIN.

Lo que hay que arreglar es de dónde salen los otros números: hoy los socios
totales y activos se calculan trayendo la lista entera y contando en el
navegador, algo que ya era frágil y con paginación queda mal directamente. El
total sale de `totalElementos`; **los activos no salen de ningún endpoint
existente**, así que o se agrega al dashboard del backend (ticket de backend) o
la tarjeta se saca hasta que exista. No inventar el número en el front.

### W10 — El portal del socio no se puede alcanzar

`ClientePortal` y la ruta `/cliente/resumen` existen, pero **no hay forma de
loguearse como socio**: el front solo llama a `/api/v1/usuarios/login`, y
`/api/v1/clientes/login` y `/api/v1/clientes/registro` no se usan en ningún
lado. La pantalla además está toda hardcodeada ("Pase Libre Musculación",
"Membresía Activa") y no lee nada del backend.

Son dos flujos de autenticación **separados a propósito** (`Cliente` y `Usuario`
son entidades distintas, con tablas, cookies y endpoints propios), así que:

- Login de socio propio, contra `/clientes/login`, que deja una cookie
  `clienteRefreshToken` (el staff usa `refreshToken`; conviven sin pisarse).
- `AuthInitializer` hoy intenta el refresh de staff únicamente: para el socio
  tiene que intentar el suyo.
- Registro (`POST /clientes/registro`) con el **código de activación** que el
  staff le dio en persona (ver W6), más email y contraseña. No se identifica con
  nombre y teléfono: eso se cerró por ser adivinable.
- El portal lee sus datos reales de `GET /clientes/{id}`, que el propio socio
  puede consultar y que ya trae estado, `fechaVencimiento` y `planVigente` —
  alcanza para la pantalla completa del alcance.
- **Los pagos no**: desde la Fase 7 toda lectura de pagos es de ADMIN, así que
  un socio recibe 403 en `/pagos/{id}` y `/pagos/cliente/{id}`. Es deliberado,
  no un bug: el portal no muestra comprobantes. Si alguna vez tiene que
  mostrarlos, primero se reabre esa rama en el backend.

**Además, un arreglo conceptual:** `types/auth.ts` declara
`RolUsuario = 'ADMIN' | 'CLIENTE' | 'GERENCIA'`, mezclando en un tipo lo que el
backend mantiene deliberadamente separado. `CLIENTE` no es un `RolUsuario`: es
un principal de otra tabla que viaja como claim del token. Conviene
`RolStaff = 'ADMIN' | 'GERENCIA'` y un `Principal` que sea staff o socio, para
que el sistema de tipos no invite a tratarlos igual.

### W11 — Limpieza de compatibilidad muerta

`LoginResponse` acepta tres formas a la vez (`accessToken` o `token`; `usuario`
anidado o `id`/`nombre`/`rol` sueltos) y `AuthInitializer`, `Login` y el
interceptor cada uno desarma las dos variantes. El backend hoy devuelve una
sola: `{ mensaje, accessToken, usuario: { id, nombre, rol, activo } }`. Tipar
esa y borrar las ramas muertas.

Nada está entregado todavía, así que no hay compatibilidad hacia atrás que
cuidar.

---

### W12 — Anular un pago cargado por error (solo ADMIN)

Depende de F6.1. **No es un botón "eliminar":** el pago no desaparece, queda
marcado como anulado con quién, cuándo y por qué. La UI tiene que reflejar eso o
va a generar la expectativa equivocada.

- Acción "Anular" en la fila del pago, **con motivo obligatorio** (el backend lo
  exige; es lo que hace que la anulación sirva como auditoría).
- El pago anulado **se sigue mostrando** en el listado, tachado o con una marca
  clara, nunca oculto: esconderlo sería volver al borrado por la ventana.
- No hay forma de editar un pago. Corregir un importe es anular y volver a
  cobrar; si la pantalla ofrece "editar", está ofreciendo algo que el API no
  hace a propósito.
- Al anular hay que refrescar el socio: su `fechaVencimiento` puede haber vuelto
  al pago anterior.
- GERENCIA no ve esta acción (403 del backend si la intenta).

### W13 — Desglose de ganancias del mes (solo ADMIN)

El dashboard hoy muestra el total agregado de `/dashboard/ganancias-mensuales`.
El alcance pide poder **abrir ese número**: ver los pagos que lo componen.

Con F6.3, es `GET /pagos?desde=&hasta=` paginado. El total de arriba y la suma
del listado tienen que coincidir —incluido el tratamiento de los anulados— o el
usuario no va a saber cuál de los dos creer.

**Mostrá el documento en cada fila.** `PagoResponse.cliente` trae
`{id, nombre, apellido, documento}`, y el documento está ahí por este ticket:
sin él, dos socios homónimos aparecen como dos cobros idénticos y es imposible
saber a cuál de los dos hay que anularle el pago (W12). El id no sirve para eso:
no se muestra y nadie lo reconoce.

## 5. Orden sugerido

1. **W1** (sacar los mocks) — sin esto no se puede verificar nada de lo demás.
2. **W2, W3, W11** — poner las lecturas que ya existen a tono con el contrato.
   Hasta acá la web hace lo mismo que hoy, pero de verdad.
3. **W4** — abrirle la puerta a GERENCIA.
4. **W6 y W5** — socios y cobro: con esto la web ya cubre el mostrador, que es
   el trabajo del escritorio.
5. **W9, W13, W7, W8, W12** — lo de ADMIN (W13 y W12 esperan a la Fase 6).
6. **W10** — el portal del socio, que es la única área que no bloquea el
   reemplazo del escritorio.

Recién después de 4 tiene sentido volver a hablar del escritorio como envoltorio
de la web (`ARQUITECTURA-APPS.md` §5).

## 6. Tickets que caen del lado del backend

Los que aparecieron al escribir esto están especificados en
`2026-09-15-fase6-backend-para-la-web.md`:

- **F6.2 `GET /api/v1/usuarios`** — sin esto, W7 no se puede hacer.
- **F6.3 filtro por fechas en `GET /pagos`** — sin esto, W13 no se puede hacer.
- **F6.1 anulación de pagos** — sin esto, W12 no se puede hacer.
- **F6.5 el plan vigente del socio** — sin esto, W10 muestra el plan hardcodeado.
- **F6.4** restringe `PATCH /clientes/{id}/estado`: la UI de socios no debe
  ofrecer "poner en ACTIVO" a mano.

La cantidad de socios activos del dashboard (W9) **sigue sin endpoint** y no
entró a la Fase 6: hasta que exista, esa tarjeta se saca en vez de calcularla
mal en el navegador.
