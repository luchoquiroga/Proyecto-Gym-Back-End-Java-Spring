# Spec: Fase 6 — lo que el backend le debe a la web

Fecha: 2026-09-15
Estado: borrador

## 1. Qué resuelve

El alcance de las tres apps quedó definido (`ARQUITECTURA-APPS.md` §2.1). Al
mapearlo contra el API aparecieron cuatro cosas que el backend todavía no sabe
hacer, más una regla que hay que cerrar. Sin esto, tres pantallas del alcance
comprometido no se pueden construir.

No es una fase "de limpieza" como la 4 o la 5: son capacidades nuevas que salen
de un alcance, no huecos encontrados leyendo el código.

## 2. Alcance

Incluye los cinco puntos de §4. **No incluye** nada de mobile (congelada), ni
tocar el flujo de autenticación, ni el portal del socio más allá de exponerle su
plan vigente.

## 3. Modelo de autorización

Ningún rol nuevo. Lo que se agrega cae de un lado ya existente de la línea:

| Método | Ruta | Quién puede | Por qué |
|---|---|---|---|
| GET | `/api/v1/usuarios` | ADMIN | gestionar cuentas de staff es de ADMIN, igual que crearlas y darlas de baja |
| POST | `/api/v1/pagos/{id}/anulacion` | ADMIN | es un dato monetario ya registrado; GERENCIA cobra pero no toca la caja cerrada |
| GET | `/api/v1/pagos?desde=&hasta=` | ADMIN | mismo endpoint y mismo rol de hoy, solo se le agregan filtros |
| PATCH | `/api/v1/clientes/{id}/estado` | ADMIN, GERENCIA | **se restringe**: deja de poder poner ACTIVO |

**Pregunta de sanity check.** *¿Qué pasa si un CLIENTE recién registrado llama a
estos endpoints con un id que no es el suyo?* 403 en todos: `GET /usuarios`,
`POST /pagos/{id}/anulacion` y el listado de pagos son de ADMIN por regla de
ruta, y `/clientes/{id}/estado` ya está restringido a staff. Ninguno queda bajo
`.anyRequest().authenticated()`, y ninguno resuelve identidad comparando un id
suelto contra `principal.id()` — la trampa de la Fase 2.

---

## 4. Los cinco tickets

### F6.1 — Anular un pago (el grande)

**Qué pide el alcance:** que un pago cargado por error se pueda sacar.

**Por qué no es un DELETE.** Un pago es el registro de que entró plata y de
quién la cobró. Borrarlo tiene tres efectos que no se ven desde la pantalla:

1. Se pierde `registrado_por`, la auditoría que agregó la Fase 1.
2. **La `fechaVencimiento` del socio se calcula del último pago** (decisión de
   la Fase 2: es un dato derivado, no una columna). Borrar el último pago le
   corre el vencimiento hacia atrás en silencio, y `VencimientoScheduler` solo
   escala estados —nunca los revierte—, así que el socio queda ACTIVO con una
   fecha que ya no existe hasta la próxima corrida.
3. Las ganancias de un mes ya cerrado cambian sin que quede nada que diga que
   cambiaron.

Editar el monto es peor que borrar: cambia la plata sin registro del valor
anterior. **Por eso no hay `PUT /pagos/{id}`**, ni ahora ni después; corregir un
importe es anular y volver a cobrar.

**Cómo:**
- Migración `V6`: `pagos.anulado BOOLEAN NOT NULL DEFAULT FALSE`,
  `anulado_por INTEGER` → `usuarios(id)` `ON DELETE SET NULL` (mismo criterio
  que `registrado_por`), `fecha_anulacion TIMESTAMP`, `motivo_anulacion VARCHAR`.
- `POST /api/v1/pagos/{id}/anulacion` con `{ motivo }`. El motivo es
  obligatorio: un pago anulado sin explicación no sirve como auditoría.
  Devuelve el pago anulado. Es POST y no DELETE a propósito: no se borra nada,
  se agrega un hecho.
- Quien anula sale del `AuthPrincipal`, nunca del body (§5.5 del replanteo).
- **Anular dos veces no hace nada** (idempotente, o 400 explícito: decidir al
  implementar; prefiero 400, porque anular un pago ya anulado casi siempre
  significa que el operador está mirando datos viejos).

**Lo que hay que acordarse de tocar, y es la parte que rompe si se olvida:**
todo lo que hoy suma o lee pagos tiene que ignorar los anulados.
- `PagoRepository.sumarMontoAbonadoEntre` y `countByFechaPagoBetween`
  (dashboard).
- `findTopByClienteIdOrderByFechaVencimientoDesc` y
  `findUltimoPagoPorCadaCliente` (la fecha de vencimiento del socio): si el
  último pago se anuló, el vencimiento vigente pasa a ser el del anterior.
- Los listados: un pago anulado **se sigue viendo** (esa es la idea), marcado
  como tal. `PagoResponse` suma `anulado`.

**Efecto secundario que hay que decidir al implementar:** anular el último pago
de un socio puede dejarlo con un vencimiento pasado, pero su `estado` sigue
siendo ACTIVO hasta que corra el scheduler. O la anulación recalcula el estado
en el momento, o el socio queda ACTIVO sin estarlo. Recomiendo recalcular en el
momento: es el mismo cálculo que ya hace `VencimientoService`.

*Tests:* anular descuenta del total del mes; anular el último pago devuelve el
vencimiento al pago anterior; GERENCIA recibe 403; el pago anulado sigue
apareciendo en el listado marcado; anular deja registrado quién y por qué.

### F6.2 — `GET /api/v1/usuarios` (listado de staff)

**No existe ningún endpoint de lectura de usuarios**, ni listado ni por id. La
pantalla de gestión de cuentas del alcance ADMIN no se puede construir: se
pueden crear cuentas y darlas de baja a ciegas, pero no ver cuáles hay.

- `GET /api/v1/usuarios` → `PaginaResponse<UsuarioResponse>` (ya trae `activo`,
  que es lo que distingue una cuenta vigente de una dada de baja). ADMIN.
- Incluye las inactivas: son justamente las que hay que ver para reactivarlas.
  Un filtro `?activo=true|false` es opcional y puede esperar.
- Nunca expone `contrasena` — `UsuarioResponse` ya no lo hace.

### F6.3 — Filtrar pagos por fecha

El alcance dice que el desglose de ganancias se resuelve "listando los pagos del
mes". Hoy `GET /pagos` pagina pero no filtra, y `/dashboard/ganancias-mensuales`
devuelve el total agregado, no las filas.

- `GET /api/v1/pagos?desde=2026-09-01&hasta=2026-09-30` (ambos opcionales),
  sobre `fecha_pago`, combinable con la paginación que ya existe.
- Sin parámetros se comporta igual que hoy, así que no rompe a nadie.
- Excluye o marca los anulados de forma coherente con F6.1: el desglose tiene
  que dar la misma plata que el total del dashboard, o el número de arriba y la
  suma de abajo no van a coincidir y nadie va a saber cuál está mal.

### F6.4 — El estado ACTIVO sale de un pago, no de un botón

`PATCH /clientes/{id}/estado` deja hoy que ADMIN o GERENCIA pongan cualquier
estado a mano, ACTIVO incluido. Es la puerta de atrás de dos reglas de la Fase
1: que un pago menor al precio del plan se rechaza, y que la activación solo
ocurre si el vencimiento calculado es futuro. Con este endpoint abierto, las dos
se esquivan con un clic.

- El endpoint deja de aceptar `ACTIVO`: un socio se activa pagando.
- Sigue sirviendo para inhabilitar, que es lo que pide el alcance.
- Si alguna vez hace falta un socio de cortesía, eso es un pago de importe cero
  contra un plan de cortesía —que queda registrado y auditado— y no un cambio
  de estado invisible.

*Tests:* poner ACTIVO a mano devuelve 400 y no cambia nada; inhabilitar sigue
funcionando para los dos roles de staff.

### F6.5 — El plan vigente del socio

El portal del socio tiene que mostrarle qué plan tiene. `Cliente` no referencia
a `Plan`: el plan vigente es el del último pago (hueco C1 del replanteo, anotado
y no cerrado).

Dos caminos, y conviene el primero:
- **Exponer `plan` en `ClienteResponse`**, resuelto en la misma consulta que ya
  trae el último pago para la `fechaVencimiento`. No agrega consultas: ese pago
  ya se está leyendo, hoy se le descarta el plan.
- Que el portal pegue a `GET /pagos/cliente/{id}` (ya permitido al dueño) y
  saque el plan del último pago. Funciona, pero le da al front la
  responsabilidad de decidir cuál es "el último", que es justo lo que el backend
  ya sabe hacer.

Ojo: si el socio nunca pagó, no tiene plan ni vencimiento. Los dos campos son
`null` y el portal tiene que saber mostrar ese caso.

---

## 5. Orden sugerido

F6.2 y F6.3 son chicos e independientes: habilitan pantallas enteras del alcance
ADMIN por poco código. F6.4 es un cambio de tres líneas más sus tests. F6.5 es
chico y desbloquea el portal del socio.

**F6.1 va al final y solo,** porque toca todo lo que lee pagos —dashboard,
vencimiento del socio, listados— y es donde un olvido se paga con números que no
cuadran.

## 6. Checklist antes de mergear

- [ ] `AUTHZ-MATRIX.md` actualizado (3 filas nuevas + la restricción de `/estado`).
- [ ] `ARQUITECTURA-APPS.md` §4 actualizado con las filas de consumo.
- [ ] Tests corridos y en verde.
- [ ] `/security-review` corrido: se tocan `SecurityConfig`, controllers y DTOs
      de request.
- [ ] Ninguna migración ya aplicada fue editada (`V6` es nueva).
