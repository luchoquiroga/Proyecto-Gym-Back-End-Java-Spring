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
| POST | `/registro` | Público | completa credenciales de un cliente ya cargado por staff; matchea por nombre+apellido+teléfono (ver Gaps conocidos) |
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

## Gaps conocidos (no corregidos aún — no son parte del alcance de este documento, quedan anotados para no perderlos)

1. **`ClienteController.registro` verifica identidad solo con nombre+apellido+teléfono.**
   No hay un paso de verificación de posesión (SMS/email de confirmación)
   antes de dejar que alguien reclame una cuenta de cliente existente. Si esos
   tres datos son adivinables/conocidos por un tercero, ese tercero puede
   apropiarse de la cuenta del cliente real. Pendiente: agregar una
   verificación fuera de banda antes de habilitar la carga de contraseña, o
   al menos limitar/loguear intentos fallidos repetidos por IP.

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
