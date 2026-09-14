# Matriz de autorización

Fuente de verdad de quién puede llamar a cada endpoint. Refleja el estado de
`SecurityConfig.java` + los chequeos de ownership hechos a mano en los
controllers, al 2026-09-14. Si tocás cualquiera de los dos, actualizá esta
tabla en el mismo commit.

Roles/principales que existen hoy:
- **ADMIN** / **GERENCIA** — staff (`Usuario`), login en `/api/v1/usuarios/login`.
- **CLIENTE** — un socio del gimnasio con cuenta en el portal web, login en
  `/api/v1/clientes/login`. Es un principal completamente separado de
  `Usuario`; nunca comparte tabla ni rol con ADMIN/GERENCIA.
- **Público** — sin token.

## `/api/v1/usuarios` (staff)

| Método | Ruta | Quién puede | Notas |
|---|---|---|---|
| POST | `/login` | Público | |
| POST | `/refresh` | Público (requiere cookie `refreshToken` válida) | |
| POST | `/logout` | Público (requiere cookie, es no-op si no hay) | |
| POST | `` | ADMIN | crea un `Usuario` nuevo (alta de staff) |
| PUT | `/cambiar-contrasena` | ADMIN | ⚠️ ver Gaps conocidos |
| DELETE | `/{id}` | ADMIN | no permite auto-eliminarse ni eliminar al último ADMIN (`UsuarioServiceImpl.eliminar`) |

## `/api/v1/clientes` (staff + el propio cliente en algunos GET)

| Método | Ruta | Quién puede | Notas |
|---|---|---|---|
| POST | `/registro` | Público | completa credenciales de un cliente ya cargado por staff; se identifica con el código de activación de un solo uso que el staff le entregó en persona (no con nombre/apellido/teléfono, ver Historial 2026-09-14) |
| POST | `/login` | Público | |
| POST | `/refresh` | Público (cookie `clienteRefreshToken`) | |
| POST | `/logout` | Público | |
| GET | `` (listado) | ADMIN, GERENCIA | |
| GET | `/buscar` | ADMIN, GERENCIA | |
| GET | `/{id}` | ADMIN, GERENCIA, o el propio CLIENTE (`principal.id() == id`) | chequeo en `ClienteController.obtenerPorId`, no en `SecurityConfig` |
| POST | `` (alta) | ADMIN, GERENCIA | agregado 2026-09-14, antes cualquier autenticado |
| PUT | `/{id}` | ADMIN, GERENCIA | agregado 2026-09-14, antes cualquier autenticado (un CLIENTE podía editar cualquier registro) |
| PATCH | `/{id}/estado` | ADMIN, GERENCIA | agregado 2026-09-14, antes cualquier autenticado (un CLIENTE podía auto-activarse sin pagar) |
| DELETE | `/{id}` | ADMIN, GERENCIA | agregado 2026-09-14, antes cualquier autenticado |

## `/api/v1/pagos`

| Método | Ruta | Quién puede | Notas |
|---|---|---|---|
| GET | `` (listado) | ADMIN, GERENCIA | |
| GET | `/buscar` | ADMIN, GERENCIA | |
| GET | `/cliente/{clienteId}` | ADMIN, GERENCIA, o el propio CLIENTE dueño | chequeo en `PagoController.obtenerPagosPorCliente` |
| GET | `/{id}` | ADMIN, GERENCIA, o el propio CLIENTE dueño del pago | agregado 2026-09-14, antes cualquier autenticado; chequeo en `PagoController.obtenerPorId` vía `pago.getCliente().getId()` |
| POST | `` (registrar pago) | ADMIN, GERENCIA | agregado 2026-09-14, antes cualquier autenticado (un CLIENTE podía registrarse pagos a sí mismo o a otros) |

## `/api/v1/planes`

| Método | Ruta | Quién puede | Notas |
|---|---|---|---|
| GET | `` , `/{id}`, `/buscar` | cualquier autenticado | catálogo, lectura no sensible |
| POST / PUT / DELETE | | ADMIN | |

## `/api/v1/dashboard/**`

| Método | Ruta | Quién puede |
|---|---|---|
| todos | | ADMIN |

## `/ping`

Público.

---

## Gaps conocidos

Ninguno pendiente por el momento. El último (identidad de `/registro` basada
en datos adivinables) se cerró el 2026-09-14, ver Historial.

## Historial de incidentes (para que no se repitan)

- **2026-09-13**: al agregar el login de clientes (rol `CLIENTE`), el catch-all
  `.anyRequest().authenticated()` de `SecurityConfig` pasó de significar
  "solo staff" a "staff o cualquier cliente registrado", pero las reglas de
  POST/PUT/PATCH/DELETE de `/api/v1/clientes` no se actualizaron para
  reflejar eso. Corregido en `185432f`.
- **2026-09-13**: el filtro de autenticación (`JwtAuthenticationFilter`) no
  distinguía access token de refresh token; un refresh token filtrado (30
  días de vida) servía como Bearer token válido en cualquier endpoint sin
  restricción de rol. Corregido en `185432f` (rechaza `tipo=refresh`).
- **2026-09-14**: mismo patrón que el incidente anterior, pero en
  `/api/v1/pagos`: `POST` (registrar pago) y `GET /{id}` no tenían
  restricción de rol ni ownership, así que un CLIENTE podía registrar pagos a
  nombre de cualquier `clienteId` o leer el pago de otro socio. Corregido:
  POST restringido a ADMIN/GERENCIA en `SecurityConfig`, y `obtenerPorId`
  ahora chequea `pago.getCliente().getId() == principal.id()` igual que ya
  hacía `obtenerPagosPorCliente`.
- **2026-09-14**: `ClienteController.registro` verificaba identidad solo con
  nombre+apellido+teléfono, sin ninguna prueba de posesión — cualquiera que
  conociera/adivinara esos tres datos de un cliente real podía reclamar su
  cuenta y fijarle su propio email+contraseña. Corregido: se agregó
  `Cliente.codigoActivacion` (V2 migration), un código de un solo uso
  generado al dar de alta a un cliente sin credenciales (`ClienteServiceImpl.crear`)
  que el staff entrega en persona; `/registro` ahora identifica al cliente por
  ese código en vez de por datos personales, y el código se anula al usarse
  (`registrarCredenciales`). El código nunca viaja en respuestas salvo una vez,
  en el alta (`ClienteAltaResponse`) — la entidad `Cliente` lo marca `@JsonIgnore`.
