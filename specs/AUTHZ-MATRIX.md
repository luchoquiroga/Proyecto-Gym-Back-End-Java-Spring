# Matriz de autorización

Fuente de verdad de quién puede llamar a cada endpoint. Refleja el estado de
`SecurityConfig.java` + los chequeos de ownership hechos a mano en los
controllers, al 2026-09-15. Si tocás cualquiera de los dos, actualizá esta
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
| GET | `` (listado) | ADMIN | agregado 2026-09-15 (Fase 6): paginado, **incluye las cuentas dadas de baja** — son las que hay que ver para reactivarlas, `activo` las distingue |
| POST | `` | ADMIN | crea un `Usuario` nuevo (alta de staff) |
| PUT | `/cambiar-contrasena` | ADMIN, GERENCIA — **solo la propia** | 2026-09-15: antes era solo-ADMIN y elegía la cuenta por un `nombre` del body; ahora sale del `AuthPrincipal` y exige la contraseña actual. La regla es `hasAnyRole`, no el catch-all: bajo `.authenticated()` entraría un CLIENTE y su id se solaparía con el de un `Usuario` |
| PUT | `/{id}/contrasena` | ADMIN | reset administrativo (el ADMIN no sabe la clave anterior). El service lo rechaza contra uno mismo: para la cuenta propia hay que usar `/cambiar-contrasena`, que pide la actual |
| PATCH | `/{id}/activo` | ADMIN | **la única forma de dar de baja y de reactivar** una cuenta. Desactivar revoca sus sesiones y conserva la fila (si no, se perdería el autor de los pagos que cobró). No permite auto-darse de baja ni dar de baja al último ADMIN activo. 2026-09-16 (Fase 7): reemplaza al `DELETE /{id}`, que no borraba nada |

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
| PATCH | `/{id}/estado` | ADMIN, GERENCIA | **inhabilitar a un socio; es la única baja que existe** y nunca borra la fila. 2026-09-16 (Fase 7): solo acepta `INACTIVO`. `ACTIVO` lo determina un pago válido (Fase 6) y `MOROSO` lo calcula el vencimiento, así que fijarlos a mano era pisar un cálculo automático |

## `/api/v1/pagos`

| Método | Ruta | Quién puede | Notas |
|---|---|---|---|
| GET | `` (listado) | ADMIN | 2026-09-15: sacado a GERENCIA (ver Historial). Acepta `?desde=&hasta=` sobre la fecha de cobro, para desglosar las ganancias de un mes |
| GET | `/cliente/{clienteId}` | ADMIN | 2026-09-16 (Fase 7): dejó de aceptar al CLIENTE dueño. Ahora es regla de ruta y no un chequeo a mano |
| GET | `/{id}` | ADMIN | 2026-09-16 (Fase 7): dejó de aceptar al CLIENTE dueño. Ahora es regla de ruta y no un chequeo a mano |
| POST | `/{id}/anulacion` | ADMIN | agregado 2026-09-15 (Fase 6): marca el pago como anulado con motivo obligatorio, quien anula sale del token. **No borra la fila y no existe editar un pago**: corregir un importe es anular y volver a cobrar. GERENCIA cobra pero no toca caja ya registrada |
| POST | `` (registrar pago) | ADMIN, GERENCIA | agregado 2026-09-14, antes cualquier autenticado (un CLIENTE podía registrarse pagos a sí mismo o a otros). Desde 2026-09-15 guarda `registrado_por` tomado del token (nunca del body) y rechaza montos menores al precio del plan |

## `/api/v1/planes`

| Método | Ruta | Quién puede | Notas |
|---|---|---|---|
| GET | `` (listado) | cualquier autenticado | catálogo, lectura no sensible. 2026-09-16 (Fase 7): se sacaron `/{id}` y `/buscar` — con un puñado de planes, el listado completo ya es la pantalla |
| POST / PUT / DELETE | | ADMIN | |

## `/api/v1/dashboard/**`

| Método | Ruta | Quién puede |
|---|---|---|
| todos | | ADMIN |

## `/ping`

Público.

---

> **Nada se borra:** después de la Fase 7 el único `DELETE` de la API es el de
> planes, que sí elimina de verdad (y se rechaza con un mensaje propio si el plan
> ya tiene pagos). Las bajas de socios y de staff son cambios de estado, con la
> misma forma en los dos recursos: `PATCH /usuarios/{id}/activo` y
> `PATCH /clientes/{id}/estado`. Un `DELETE` que no borra es un contrato que
> miente, y obliga a aclarar en cada pantalla que "dar de baja" no elimina.

> **Regla de los dos roles de staff:** GERENCIA opera socios y cobra; ADMIN es
> el único que ve datos monetarios. Por eso GERENCIA conserva `POST /pagos`
> (cobrar) pero ninguna lectura de pagos. Para saber si un socio está al día
> sin ver plata, GERENCIA usa el estado y la fecha de vencimiento que expone el
> propio socio en `/api/v1/clientes` (ver `2026-09-15-replanteo-backend.md` §2.3).

## Gaps conocidos

**No hay limpieza de refresh tokens vencidos.** `refresh_tokens` y
`cliente_refresh_tokens` solo crecen: `revocar` marca `revocado = true` y nada
borra nunca una fila, ni siquiera cuando ya expiró. Dejó de ser urgente el
2026-09-15 —ya no bloquea ninguna baja, ver Historial— pero sigue siendo una
tabla que crece sin techo.

**Un access token sobrevive a la baja de su cuenta.**
`JwtAuthenticationFilter` no consulta la base, así que quien fue dado de baja
(o le cambiaron la contraseña) sigue entrando con el token que ya tenía hasta
que expira: hasta 30 minutos. Lo que sí se cierra de inmediato es la
renovación, porque se le revocan los refresh tokens y `RefreshTokenService.validar`
rechaza los de una cuenta inactiva. Aceptado a propósito: validar contra la
base en cada request es una consulta por request para tapar una ventana de
media hora. Si alguna vez hace falta cerrarla (una baja por conflicto, por
ejemplo), la salida es una lista de revocación en memoria, no ir a la base.

El gap anterior (identidad de `/registro` basada en datos adivinables) se cerró
el 2026-09-14, ver Historial.

## Historial de incidentes (para que no se repitan)

- **2026-09-16**: no fue un incidente sino una limpieza, pero deja una lección
  del mismo tipo. Los chequeos de ownership de `GET /pagos/{id}` y
  `/pagos/cliente/{clienteId}` dejaban leer sus pagos al CLIENTE dueño, pero el
  portal del socio nunca los mostró: era una rama de autorización sin ningún
  consumidor, o sea código que nadie ejercía y que igual había que releer cada
  vez que cambiaba un rol. Se cerró (los dos endpoints son de ADMIN por regla de
  ruta) y, de paso, desapareció de `PagoController` la última comparación de ids
  entre `usuarios` y `clientes`, que es la trampa de la Fase 2. Si el portal
  algún día muestra comprobantes, se reabre con su test.

- **2026-09-15**: dar de baja a un empleado no funcionaba contra la base. La
  matriz decía que ADMIN podía `DELETE /api/v1/usuarios/{id}` y el código lo
  permitía, pero `refresh_tokens` referencia a `usuarios` con una FK sin
  cascada y sus filas no se borran nunca, así que cualquiera que se hubiera
  logueado alguna vez daba 409. No se arregló con cascada: `V3` había creado
  `pagos.registrado_por ... ON DELETE SET NULL`, así que un borrado exitoso
  habría puesto en NULL el autor de todos los pagos que esa persona cobró,
  destruyendo la auditoría de caja que `V3` existe para garantizar. Se pasó a
  baja lógica (`usuarios.activo`, `V5`), que además es lo que ya hacía la baja
  de clientes.
- **2026-09-15**: `PUT /usuarios/cambiar-contrasena` era un reset
  administrativo disfrazado de cambio de contraseña: solo-ADMIN, elegía la
  cuenta por un `nombre` **del body** y no pedía la contraseña actual. Mismo
  patrón que `registrado_por` antes de la Fase 1 —identidad tomada del body en
  vez del token— con dos consecuencias: GERENCIA no podía cambiar su propia
  clave, y una sesión ADMIN olvidada abierta alcanzaba para quedarse con
  cualquier cuenta de staff. Separado en dos endpoints: el propio (ADMIN o
  GERENCIA, identidad del `AuthPrincipal`, exige la actual) y el reset
  administrativo (`PUT /{id}/contrasena`, solo ADMIN, no usable contra uno
  mismo).
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
- **2026-09-15**: restringir `/dashboard` a ADMIN era una cortina y no un
  permiso: las cuatro lecturas de `/api/v1/pagos` seguían abiertas a GERENCIA,
  así que con el listado completo se reconstruían los ingresos del gimnasio a
  mano. No fue un incidente reportado sino un hueco detectado al escribir el
  replanteo del backend. Corregido pasando toda lectura de pagos a ADMIN, y
  exponiendo la fecha de vencimiento del socio para que GERENCIA siga sabiendo
  quién está al día sin ver montos.
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
