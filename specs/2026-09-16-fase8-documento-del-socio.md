# Spec: Fase 8 — el documento del socio

Fecha: 2026-09-16
Estado: implementado

## 1. Qué resuelve

Dos socios que se llaman igual son hoy **indistinguibles** en el API.

El caso concreto: el ADMIN revisa el desglose de ganancias del mes, ve un pago
cargado por error y tiene que anularlo. `PagoResponse.ClienteResumen` expone
`id`, `nombre` y `apellido`, nada más, así que dos Juan Pérez se renderizan como
dos filas idénticas carácter por carácter. La única diferencia real es el id, y
un id no es un dato que una persona recuerde ni reconozca: sirve para que la web
arme la URL, no para que alguien decida cuál de los dos Juan Pérez pagó.

El teléfono no alcanza como reemplazo. Es nullable y no tiene ninguna validación
en `ClienteRequest`, así que puede venir vacío; y el caso realista de dos socios
homónimos en un gimnasio es **padre e hijo**, que muy probablemente comparten el
teléfono de la casa. Justo en la colisión que genera el problema, el teléfono
colisiona también.

## 2. Por qué ahora y no después

`DESPLIEGUE.md` deja decidido que producción se borra y se recrea. Agregar una
columna `NOT NULL UNIQUE` contra una base vacía no cuesta nada. La misma columna
dentro de seis meses, con cientos de socios ya cargados sin documento, es una
columna nullable, un backfill a mano socio por socio, y una restricción de
unicidad que en la práctica nunca se termina de activar.

Es la ventana más barata que va a existir, y se cierra sola.

## 3. Alcance

- `clientes.documento`, obligatorio y único.
- Se pide en el alta, se puede corregir, y se expone donde hace falta para
  desambiguar.

**No incluye:** buscar socios por documento. `GET /clientes/buscar` sigue siendo
por nombre. Es un agregado obvio y barato, pero no es lo que esta fase resuelve y
no hay ninguna pantalla pidiéndolo todavía.

## 4. Datos

### Normalización: sin esto, el UNIQUE es decorativo

`12.345.678` y `12345678` son la misma persona, y una restricción de unicidad
sobre el texto crudo las deja entrar a las dos. Con eso, el problema que esta
fase viene a resolver reaparece con otra cara: dos filas del mismo socio, ahora
con documentos "distintos".

Entonces el documento se **normaliza antes de guardarse**: se le quitan puntos,
espacios y guiones, y se pasa a mayúsculas (los pasaportes llevan letras). Lo que
se guarda y lo que se compara es siempre la forma normalizada. Lo que el staff
escriba con puntos o sin puntos da igual.

### Migración `V7`

La base de producción se recrea vacía, pero las de desarrollo y `gimnasio_test`
ya tienen socios, así que la columna no puede nacer `NOT NULL` de una. Va en tres
pasos: se agrega nullable, se rellenan las filas existentes con un placeholder
visiblemente falso derivado del id, y recién ahí se le ponen `NOT NULL` y
`UNIQUE`.

El placeholder es a propósito feo (`PENDIENTE-<id>`): si alguna base real llegara
a pasar por acá, tiene que cantar en pantalla que ese socio no tiene documento
cargado, no disimularse como un dato válido.

### Dónde se expone

| DTO | Por qué |
|---|---|
| `ClienteResponse` | el listado y la ficha del socio: es donde el staff distingue entre homónimos |
| `ClienteAltaResponse` | confirmación del alta; el staff verifica lo que acaba de cargar |
| `PagoResponse.ClienteResumen` | **es el motivo de toda la fase**: sin esto, el desglose del mes sigue mostrando dos filas iguales |

El socio ve su propio documento en su portal, que es correcto: es un dato suyo.
Ninguna respuesta pública lo expone.

### Edición

`PUT /clientes/{id}` pasa a aceptar el documento. Es distinto del email, que se
excluye a propósito porque es la identidad de login del socio y cambiárselo le
sacaría el acceso: un documento mal tipeado en el alta hay que poder arreglarlo,
y el único que puede hacerlo es el staff. Se revalida la unicidad contra los
demás socios.

## 5. Validación

- `@NotBlank`: es obligatorio; ese es el punto de la fase.
- Formato: letras, números, puntos, espacios y guiones. No se valida contra el
  formato de DNI argentino a propósito — un socio extranjero con pasaporte o
  cédula tiene que poder anotarse, y una validación demasiado estricta se
  esquiva escribiendo cualquier cosa, que es peor que no validar.
- Largo: hasta 20 caracteres, y al menos 6 ya normalizado.
- Duplicado → **409**, con un mensaje que diga que ese documento ya pertenece a
  otro socio. No 400: el dato está bien formado, el conflicto es con el estado
  de la base.

## 6. Casos de error / borde

| Caso | Respuesta |
|---|---|
| Alta sin documento | 400 por validación |
| Alta con documento ya usado | 409 |
| Alta con `12.345.678` existiendo `12345678` | 409 — son el mismo documento |
| Edición que choca con otro socio | 409 |
| Edición que "choca" consigo mismo | 200, no es conflicto |
| Documento con caracteres raros | 400 |

## 7. Tests

- El documento se guarda normalizado: se da de alta con puntos y se lee sin.
- Dos altas con el mismo documento escrito distinto → la segunda da 409.
- El alta sin documento da 400 y no crea nada.
- Editar el documento de un socio a uno ya usado da 409; dejarlo igual, 200.
- `GET /clientes` y `GET /pagos` traen el documento (el segundo es el caso de la
  fase: dos socios homónimos distinguibles en el desglose del mes).

## 8. Checklist antes de mergear

- [x] `AUTHZ-MATRIX.md`: sin cambios, no se toca ningún permiso. El documento se
      expone en respuestas que ya estaban restringidas a staff y al propio socio.
- [x] `2026-09-15-tickets-web.md`: actualizados W3, W6 y W13.
- [x] Tests corridos y en verde (152; 6 nuevos para el documento).
- [x] `/security-review` corrido: sin hallazgos. Verificó que ninguna ruta
      pública devuelve el documento (`/registro` y `/logout` devuelven solo un
      mensaje; `/login` y `/refresh`, el del propio socio que se autenticó), que
      ningún controller serializa la entidad `Cliente`, que todas las escrituras
      pasan por la normalización, y que el 409 con el documento en el mensaje
      solo lo puede provocar staff, que ya ve todos los documentos igual.
- [x] Ninguna migración ya aplicada fue editada (`V7` es nueva).
