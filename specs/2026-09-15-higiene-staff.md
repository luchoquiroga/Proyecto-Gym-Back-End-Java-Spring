# Spec: higiene de la gestión de staff

Fecha: 2026-09-15
Estado: implementado

## 1. Qué resuelve

Dos cosas que quedaron fuera de las cuatro fases del replanteo
(`2026-09-15-replanteo-backend.md`) y que hacen a la administración de las
cuentas de staff:

1. **Dar de baja a un empleado no funciona.** `DELETE /api/v1/usuarios/{id}`
   devuelve 409 para cualquiera que se haya logueado alguna vez, porque
   `fk_refresh_tokens_usuario` no borra en cascada y las filas de token no se
   eliminan nunca (`RefreshTokenService.revocar` solo marca `revocado = true`).
2. **Nadie puede cambiar su propia contraseña.**
   `PUT /api/v1/usuarios/cambiar-contrasena` es solo-ADMIN, identifica a quién
   le cambia la clave por un `nombre` que viene **en el body**, y no pide la
   contraseña actual. O sea: es un reset administrativo disfrazado de cambio de
   contraseña, y GERENCIA no tiene forma de cambiar la suya.

El punto 2 es el mismo patrón que cerró la Fase 1 con `registrado_por`: la
identidad del que llama sale del `AuthPrincipal`, nunca del body (§5.5 del
replanteo).

## 2. Alcance

Incluye: baja lógica de staff (+ reactivación), revocación de las sesiones del
usuario afectado, y separar "cambio de mi propia contraseña" de "reset
administrativo".

**No incluye:**
- Tocar el login de clientes ni `cliente_refresh_tokens`. La baja de clientes
  ya es lógica (`ClienteServiceImpl.darDeBaja` pone `INACTIVO`) y funciona.
- Una tarea de limpieza de refresh tokens vencidos. Sigue sin existir; deja de
  ser urgente porque ya no bloquea ninguna baja.
- Separar `Usuario.nombre` (login) del nombre real — sigue abierto en §8 del
  replanteo.
- Recuperación de contraseña por email. No hay email de staff en el modelo.

## 3. Modelo de autorización

### Por qué baja lógica y no borrado

La nota original dejaba dos caminos (cascada en la FK, o borrado explícito en
el service). Los dos se descartaron por lo mismo: `V3` creó
`pagos.registrado_por ... ON DELETE SET NULL`, así que **borrar a un empleado
pone en NULL el autor de todos los pagos que cobró** — justo la auditoría de
caja que la Fase 1 existe para garantizar. Una baja no puede destruir el
histórico de quién tocó plata.

Con baja lógica la fila nunca se borra: `registrado_por` sigue apuntando a
alguien, el 409 desaparece de raíz (no hay DELETE contra la base) y queda
igual que la baja de clientes.

### Endpoints nuevos o modificados

| Método | Ruta | Quién puede | Qué hace |
|---|---|---|---|
| DELETE | `/api/v1/usuarios/{id}` | ADMIN | baja lógica: `activo = false` + revoca los refresh tokens de ese usuario |
| PATCH | `/api/v1/usuarios/{id}/activo` | ADMIN | reactiva (o desactiva) una cuenta |
| PUT | `/api/v1/usuarios/cambiar-contrasena` | ADMIN, GERENCIA — **solo la propia** | exige contraseña actual; identidad del `AuthPrincipal` |
| PUT | `/api/v1/usuarios/{id}/contrasena` | ADMIN | reset administrativo de la clave de otro |

Reglas de negocio que se conservan y se agregan:
- No podés darte de baja a vos mismo (ya existía).
- No se puede dar de baja al último ADMIN **activo** (antes contaba todos).
- El reset administrativo **no se puede usar contra uno mismo**: si pudiera, un
  ADMIN esquivaría la exigencia de contraseña actual sobre su propia cuenta.

### Dónde va cada chequeo

- Rol → `SecurityConfig`. **`PUT /cambiar-contrasena` pasa de `hasRole("ADMIN")`
  a `hasAnyRole("ADMIN", "GERENCIA")`, no a `.authenticated()`.** La diferencia
  importa: bajo el catch-all, un principal CLIENTE llegaría al endpoint y
  `principal.id()` sería un id de la tabla `clientes`, que se solapa con los de
  `usuarios` (ambas secuencias arrancan en 1). Es exactamente la trampa que
  documenta la Fase 2.
- Ownership → no hace falta chequeo a mano: el endpoint propio no recibe id, lo
  toma del token.

### Pregunta de sanity check

*¿Qué pasa si un CLIENTE recién registrado llama a estos endpoints con un id
que no es el suyo?* **403 en los cuatro**, por la regla de rol de
`SecurityConfig`. Ninguno queda bajo `.anyRequest().authenticated()`.

*¿Y si un GERENCIA llama al reset administrativo de otro?* 403:
`/{id}/contrasena` es solo ADMIN. Lo único que GERENCIA puede hacer es cambiar
la propia clave sabiendo la anterior.

## 4. Datos

- **`V5__usuario_activo.sql`**: `usuarios.activo BOOLEAN NOT NULL DEFAULT TRUE`.
  Las cuentas existentes quedan activas, que es el estado correcto.
- `activo` **nunca** viene de un body de alta ni de cambio de contraseña: solo
  lo mueven `DELETE /{id}` y `PATCH /{id}/activo`. `UsuarioRequest` no lo
  expone, igual que no expone `id`.
- `UsuarioResponse` suma `activo` (dato no sensible, y la UI lo necesita para
  mostrar una cuenta dada de baja).
- `CambioContrasenaRequest` pierde `nombre` y gana `contrasenaActual`. Que
  `nombre` desaparezca del body es el punto del cambio, no un efecto colateral.

## 5. Validación

- `contrasenaActual` y `nuevaContrasena`: `@NotBlank`; la nueva `@Size(min = 8)`
  como hoy.
- La nueva no puede ser igual a la actual → 400.
- Contraseña actual incorrecta → **400 con mensaje genérico** (`"La contraseña
  actual no es correcta"`). No 401: el token es válido, lo que falla es el dato
  del body; un 401 haría que el frontend crea que se venció la sesión.

## 6. Casos de error / borde

| Caso | Respuesta |
|---|---|
| Baja de un id inexistente | 404 (`RecursoNoEncontradoException`) |
| Baja de uno mismo | 400 "No podés eliminar tu propio usuario" |
| Baja del último ADMIN activo | 400 |
| Baja de una cuenta ya inactiva | 204, idempotente |
| Reset administrativo contra uno mismo | 400, remite al endpoint propio |
| Contraseña actual incorrecta | 400 |
| Nueva contraseña igual a la actual | 400 |
| GERENCIA intentando resetear la de otro | 403 |
| CLIENTE en cualquiera de los cuatro | 403 |

**Límite conocido y aceptado:** `JwtAuthenticationFilter` no consulta la base,
así que el access token de un usuario recién dado de baja **sigue siendo válido
hasta que expira** (30 min). Lo que se cierra es la renovación: se le revocan
los refresh tokens y `RefreshTokenService.validar` rechaza los de una cuenta
inactiva, así que no puede estirar la sesión. Validar contra la base en cada
request sería una consulta por request; no se justifica para una ventana de 30
minutos. Mismo criterio para el cambio de contraseña.

**Consecuencia de `uk_usuarios_nombre` (V4):** un usuario dado de baja sigue
ocupando su nombre, así que no se puede crear otro con el mismo. Es correcto
—dos filas con el mismo login romperían `findByNombre`— y es el motivo por el
que existe `PATCH /{id}/activo`: la salida es reactivar, no crear un duplicado.
El alta lo dice con un mensaje propio en vez de un 409 de la constraint.

## 7. Tests a agregar

Integración (`UsuarioControllerIntegrationTest`):
- Un usuario con refresh tokens en la base se da de baja **sin 409** (el caso
  que hoy falla), queda `activo = false` y su login posterior da 401.
- Su refresh token deja de servir después de la baja.
- GERENCIA cambia su propia contraseña y se puede loguear con la nueva.
- Contraseña actual incorrecta → 400, y la contraseña **no** cambió.
- GERENCIA contra `PUT /{id}/contrasena` → 403.
- Un token de rol CLIENTE contra `/cambiar-contrasena` → 403.
- Ninguna respuesta trae `contrasena` (`jsonPath("$.contrasena").doesNotExist()`).

Unit (`UsuarioServiceTest`): último ADMIN activo no se puede dar de baja
(incluyendo el caso "hay dos ADMIN pero uno ya está inactivo", que con el
`countByRol` viejo pasaría de largo); reset contra uno mismo rechazado.

## 8. Checklist antes de mergear

- [x] `AUTHZ-MATRIX.md` actualizado (4 filas, gaps reescritos e historial).
- [x] Tests corridos y en verde (129, incluidos 6 de integración y 8 unitarios
      nuevos para esta fase).
- [x] `/security-review` corrido (se tocan `SecurityConfig`, un controller y un
      DTO de request).

**Corrección pendiente en `AUTHZ-MATRIX.md`:** la sección "Gaps conocidos"
afirma que `DELETE /api/v1/clientes/{id}` choca contra `pagos` con un 409. Es
falso: ese endpoint llama a `darDeBaja`, que es baja lógica y nunca borra la
fila. Se corrige en el mismo commit.
