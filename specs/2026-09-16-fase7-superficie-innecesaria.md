# Spec: Fase 7 — sacar la superficie que no se usa

Fecha: 2026-09-16
Estado: implementado

## 1. Qué resuelve

Con el contrato quieto y el alcance escrito (`ARQUITECTURA-APPS.md` §2.1), recién
ahora se puede decir qué sobra. Esta fase **no agrega nada**: saca endpoints y
métodos que ninguna app del alcance va a llamar.

El motivo no es estético. Cada endpoint vivo es una regla de autorización que
alguien tiene que releer cada vez que cambia un rol, y los tres incidentes del
historial de `AUTHZ-MATRIX.md` salieron exactamente de ahí: una rama cuyo
supuesto dejó de valer sin que nadie la mirara. Superficie sin consumidor es
superficie que nadie prueba y nadie revisa.

## 2. Alcance

Solo borrado, más una restricción. **No incluye** cambiar el comportamiento de
nada que se conserve, ni tocar autenticación.

## 3. Modelo de autorización

### Lo que se saca

| Método | Ruta | Por qué |
|---|---|---|
| GET | `/api/v1/planes/{id}` | el listado devuelve los planes enteros; son un puñado de filas |
| GET | `/api/v1/planes/buscar` | buscar entre diez filas que ya se trajeron completas |
| GET | `/api/v1/pagos/buscar` | busca por nombre de socio: ambiguo entre homónimos y sin paginar. El caso real —encontrar el pago mal cargado— se resuelve desde la ficha del socio con `/pagos/cliente/{id}` |
| DELETE | `/api/v1/usuarios/{id}` | no borra: desactiva. Lo mismo hace `PATCH /{id}/activo`, que además reactiva |
| DELETE | `/api/v1/clientes/{id}` | no borra: pone INACTIVO. Lo mismo hace `PATCH /{id}/estado` |

**Regla que queda:** después de esto **no existe ningún DELETE en la API**, y es
correcto, porque no se borra ninguna fila en ningún caso. Un `DELETE` que no
borra es un contrato que miente, y obliga a explicar en cada pantalla que "dar
de baja" no elimina. Las dos bajas quedan con la misma forma:

```
PATCH /api/v1/usuarios/{id}/activo   {"activo": false}
PATCH /api/v1/clientes/{id}/estado   {"estado": "INACTIVO"}
```

La excepción real al borrado —`DELETE /api/v1/planes/{id}`— sí borra de verdad,
y se conserva: un plan que nunca se vendió se elimina, y si tiene pagos
asociados el service lo rechaza con un mensaje de negocio propio.

### Lo que se restringe

| Método | Ruta | Cambio |
|---|---|---|
| PATCH | `/api/v1/clientes/{id}/estado` | solo acepta `INACTIVO`. Ya rechazaba `ACTIVO` (Fase 6); ahora también `MOROSO` |
| GET | `/api/v1/pagos/{id}` | pasa a ADMIN: deja de aceptar al CLIENTE dueño |
| GET | `/api/v1/pagos/cliente/{clienteId}` | ídem |

**Por qué `MOROSO` tampoco:** es un estado que el sistema calcula solo, en las
dos direcciones. Un pago válido activa al socio; anular ese pago le recalcula el
estado en el momento; y el scheduler lo escala a MOROSO o INACTIVO según los
días vencidos. Dejar que alguien lo escriba a mano es pisar un cálculo
automático con un valor que la próxima corrida puede contradecir. Lo único que
el estado necesita a mano es "este socio ya no viene más", o sea INACTIVO.

**Por qué se cierra la rama del CLIENTE en pagos:** el portal del socio es de
solo lectura y muestra estado, días que faltan y sus datos — no pagos
(`ARQUITECTURA-APPS.md` §2.1). Esos dos chequeos de ownership son entonces
superficie abierta sin ningún consumidor, y son justo el tipo de rama que
después nadie relee. Se cierra hasta que el portal muestre pagos de verdad; el
día que lo haga, se reabre con su test.

Efecto secundario bienvenido: los dos endpoints dejan de necesitar chequeo a
mano y pasan a resolverse con una regla de ruta. Menos código y menos lugares
donde equivocarse.

### Pregunta de sanity check

*¿Qué pasa si un CLIENTE llama a estos endpoints con un id que no es el suyo?*
403 en todos, por regla de ruta. Después de esta fase, **un CLIENTE no puede
leer ningún pago**, ni el suyo, así que no queda ninguna comparación de ids
entre tablas distintas — la trampa de la Fase 2 desaparece de `PagoController`.

## 4. Código muerto que se borra

- **`ClienteService.obtenerTodos()`** y su impl: cero llamadores, ni en `main`
  ni en tests. Quedó huérfano en la Fase 3, cuando el listado pasó a
  `obtenerTodosConVencimiento(Pageable)`. Devuelve entidades JPA sin paginar,
  que es exactamente lo que esa fase existió para eliminar.
- **`ClienteService.obtenerPorId`** sale de la interfaz y queda privado en el
  impl, que es su único llamador. Exponía la entidad `Cliente` como contrato del
  service sin que nadie lo consumiera.
- Los métodos de service y repositorio que quedan sin uso al sacar los endpoints
  (`PlanService.buscarPorNombre`, `PagoService.buscarPagosPorNombreCliente`,
  `ClienteService.darDeBaja`, `UsuarioService.darDeBaja` y sus consultas).

## 5. Qué NO se saca, y por qué

Anotado para no rediscutirlo:

- **`GET /pagos/{id}`** se conserva (solo ADMIN): es la confirmación antes de
  anular. Es el más débil de la lista; si al construir esa pantalla resulta que
  el listado ya trae todo, se saca ahí.
- **`GET /pagos/cliente/{clienteId}`** se conserva (solo ADMIN): es "los pagos de
  este socio" desde su ficha, y es donde se encuentra el pago mal cargado. El id
  no lo escribe nadie: la web lo tiene de la fila en la que se hizo clic.
- **`POST /planes`**: el alcance no lo nombraba, pero sin él no se puede ofrecer
  un plan nuevo nunca. Confirmado que se queda.
- **`/ping`**: lo usa `DESPLIEGUE.md` para verificar el deploy y como ping
  externo que evita que Render duerma el servicio (dormido, el scheduler de
  vencimientos no corre).

## 6. Tests

No hay tests nuevos de funcionalidad: la fase saca cosas. Lo que sí hace falta:

- Los tests de los endpoints eliminados se borran.
- Un test por cada rama de autorización que se cierra: un CLIENTE contra
  `GET /pagos/{id}` y `GET /pagos/cliente/{id}` ahora recibe **403**, incluso
  siendo el dueño. Es el reemplazo del test de ownership que se elimina, y deja
  escrito que el 403 es deliberado y no una regresión.
- `PATCH /clientes/{id}/estado` con `MOROSO` devuelve 400 y no cambia nada.

## 7. Checklist antes de mergear

- [x] `AUTHZ-MATRIX.md` actualizado (filas eliminadas, restricciones, la regla
      de que nada se borra, y la entrada de historial).
- [x] `ARQUITECTURA-APPS.md` §4 actualizado.
- [x] `2026-09-15-tickets-web.md` actualizado: W6, W7 y W10.
- [x] Tests corridos y en verde (146).
- [x] `/security-review` corrido: sin hallazgos. Verificó que el único
      `@DeleteMapping` que queda es el de planes y sigue cubierto por su regla,
      y que el comodín `GET /pagos/**` no pisa ningún POST por estar acotado por
      método.
