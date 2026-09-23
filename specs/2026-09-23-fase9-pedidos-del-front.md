# Spec: Fase 9 — lo que el front encontró al usar el backend

Fecha: 2026-09-23
Estado: implementado

## 1. Qué resuelve

Al construir el mostrador y el portal del socio, el front encontró cinco cosas
del backend (tickets B1–B5 del repo del front). Dos son bugs de negocio que
cuestan plata o días (la zona horaria y el cobro anticipado), una es de
seguridad (largo de contraseña), una es de codificación y una es un número que
el dashboard no podía mostrar.

## 2. Alcance

Incluye B1 a B5, con estos ajustes al ticket original después de verificarlo
contra el código:

- **B1** también pega en `VencimientoServiceImpl` (la corrida diaria y
  `recalcularEstadoDe`) y en `fechaAnulacion`, no solo en los cuatro lugares
  del ticket.
- **B3**: el ticket decía que la contraseña del staff ya pedía 8 caracteres. No
  era así: solo los pedían el cambio y el reset. El alta de staff
  (`UsuarioRequest`) y la contraseña opcional que pone el staff al dar de alta
  a un socio (`ClienteRequest`) tampoco tenían mínimo.
- **B4**: los que escriben JSON a mano sin charset son cuatro, no dos: también
  `JwtAuthenticationFilter` (el de "Sesión expirada") y `RateLimitFilter`.

No incluye: los timestamps de `GlobalExceptionHandler` (informativos, no
deciden nada), ni recalcular cadenas de pagos al anular (ver §2.2).

### 2.1 B1 — El "hoy" del backend es el de Argentina

Un único `Clock` con zona `America/Argentina/Buenos_Aires`
(`config/ZonaHorariaConfig`) que se inyecta en todo service que pregunte la
fecha. El cron del scheduler lleva la misma zona. Se eligió `Clock` y no
`-Duser.timezone` porque además deja testear "son las 22:00 del 30/09" con un
reloj fijo, y porque no depende de que alguien se acuerde de un flag en Render.

### 2.2 B2 — Cobrar por adelantado encadena el período

Decidido por el dueño: **se encadena siempre**, sea cual sea el plan.

```
vigente  = MAX(fechaVencimiento) de los pagos válidos del socio con fechaPago <= fechaPago del cobro
inicio   = max(fechaPago, vigente)
vence    = inicio + plan.duracion
```

`fechaPago` sigue siendo el día en que entró la plata: auditoría y dashboard no
cambian. El `fechaPago <=` hace que un pago **retroactivo** se calcule como si
se hubiera cargado a tiempo: registrar hoy un pago olvidado de agosto no se
encadena a la cobertura de septiembre, que en agosto no existía.

**Anulación.** Si se anulara un pago del medio de la cadena, el siguiente
conservaría el vencimiento corrido y el socio se quedaría con días que no pagó.
Decidido: **se rechaza la anulación** mientras exista otro pago válido del socio
**registrado después** (id mayor) y cobrado durante el período de este
(`fechaPago` entre la de este pago y su vencimiento). Para anularlo, primero se
anulan los que vienen después. No se recalcula nada: recalcular sería editar
pagos registrados, y un pago no se edita.

El "registrado después" importa: un pago cargado antes no pudo encadenarse a
uno que todavía no existía. Sin esa condición, dos cobros del mismo día
bloquearían anular el segundo, que es justo el que se encadenó.

Es un poco más fino que "solo se puede anular el último pago": un pago viejo
cuyo período no tocó a ningún otro se puede anular aunque haya pagos
posteriores. Es conservador en un caso: un pago anticipado cargado antes de
esta fase (que no se encadenó) también bloquea la anulación del anterior. La
salida es la misma: anular primero el posterior.

### 2.3 B3 — Mínimo de 8 caracteres en toda contraseña que se fija

`@Size(min = 8)` en `ClienteRegistroRequest`, `UsuarioRequest` y
`ClienteRequest` (en este último es opcional: `@Size` no valida `null`). Las
cuentas existentes no se tocan; los logins no validan largo.

### 2.4 B4 — UTF-8 en las respuestas escritas a mano

Los cuatro escritores usan `application/json;charset=UTF-8`.

### 2.5 B5 — Conteo de socios por estado

Endpoint **aparte** y no campos nuevos en `/ganancias-mensuales` (decidido por
el dueño): aquel responde por un mes elegido, y "socios activos" es una foto de
hoy. Mezclarlos haría que pedir marzo de 2025 devuelva los activos de hoy.

## 3. Modelo de autorización

- **Quién puede llamar:** `GET /api/v1/dashboard/socios` — ADMIN. Ya lo cubre la
  regla de ruta `/api/v1/dashboard/**`; no cambia `SecurityConfig`.
- **Ownership:** no aplica; es un agregado sin datos de ningún socio puntual.
- **Sanity check:** un CLIENTE recibe 403 (regla de ruta). GERENCIA también: el
  dashboard entero es de ADMIN, aunque este número no sea monetario. Si algún
  día GERENCIA lo necesita en el mostrador, se reabre acá.
- `POST /pagos/{id}/anulacion` no cambia de rol; suma un 400 nuevo (§6).

## 4. Datos

Sin migraciones. `SociosPorEstadoResponse { activos, morosos, inactivos }`. El
estado contado es el persistido, que la corrida diaria mantiene al día.

## 5. Validación

Contraseña corta → 400 con "La contraseña debe tener al menos 8 caracteres".

## 6. Casos de error / borde

- Anular un pago con otro cobrado durante su período → 400 que nombra al pago
  posterior y dice que hay que anularlo primero.
- Pago a las 22:00 de Argentina → `fechaPago` de ese día, no del siguiente.
- Cron a medianoche de Argentina, no a las 21:00.

## 7. Tests

- Unit (`Clock` fijo en `2026-10-01T01:00Z` = 30/09 22:00 en Argentina): el
  pago sin fecha queda el 30/09; el dashboard sin parámetros informa septiembre;
  aunque en UTC ya sea octubre; un socio que vence ese día no pasa a MOROSO.
- Unit B2: cobro anticipado se encadena, cobro con el socio vencido arranca en
  `fechaPago`, retroactivo no se encadena; anulación rechazada con pago
  encadenado.
- Integración: registro de socio con contraseña corta → 400; `GET
  /dashboard/socios` con ADMIN (200), GERENCIA (403) y CLIENTE (403).

## 8. Impacto en el front

- `preverCobro` (`features/pagos/schemas.ts`) copia la regla de §2.2 y el aviso
  de días superpuestos desaparece.
- zod de `portal-socio/schemas.ts` (y del alta de staff) con mínimo 8.
- La tarjeta de socios activos vuelve, leyendo `GET /dashboard/socios`.
- La pantalla de anulación muestra el 400 nuevo tal cual viene.
- `CONTRATO-API.md` del front se actualiza con todo lo anterior.

## 9. Checklist antes de mergear

- [x] `AUTHZ-MATRIX.md` actualizado.
- [x] Tests corridos y en verde (162, 2026-09-23).
- [x] `/security-review` sobre el diff: sin hallazgos.
