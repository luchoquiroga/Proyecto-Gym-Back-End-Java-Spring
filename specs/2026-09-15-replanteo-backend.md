# Spec: replanteo del backend

Fecha: 2026-09-15
Estado: aprobado — decisiones tomadas en §8; listo para implementar por fases

## 1. Qué resuelve

Antes de escribir la web y la mobile conviene frenar y revisar el backend, por
una razón de costo: hoy hay **un solo consumidor** del API (la app Swing). Todo
lo que haya que corregir en el contrato o en el modelo de permisos se corrige
ahora tocando una app; dentro de unos meses habrá que tocar tres.

Este documento no propone reescribir nada. La arquitectura que hay
(`controller → service → repository → entity`, con `SecurityConfig` + filtro
JWT) es correcta y se respeta. Lo que hace es: (a) dejar escrito quién
interviene y qué puede hacer, (b) listar los huecos concretos encontrados al
revisar el código, y (c) proponer un orden de trabajo.

Complementa a `ARQUITECTURA-APPS.md` (qué app llama a qué) y a
`AUTHZ-MATRIX.md` (quién tiene permiso hoy).

---

## 2. Quiénes intervienen

Cinco actores. Los primeros cuatro son principales autenticables; el quinto no
es una persona pero toma decisiones de negocio y hay que tenerlo en la lista.

| Actor | Entidad | Cómo se autentica | Qué es en el negocio |
|---|---|---|---|
| **Público** | — | sin token | alguien que todavía no probó quién es |
| **CLIENTE** | `Cliente` | `POST /clientes/login` (email + contraseña) | un socio del gimnasio |
| **GERENCIA** | `Usuario` | `POST /usuarios/login` (nombre + contraseña) | quien atiende el mostrador |
| **ADMIN** | `Usuario` | igual que GERENCIA | el dueño |
| **Sistema** | — | ninguna (`VencimientoScheduler`) | tarea programada que vence socios |

`Usuario` y `Cliente` son principales **completamente separados**: tablas
distintas, tokens distintos, ciclos de vida distintos. Eso ya está bien
resuelto y no se toca.

### 2.1 Qué puede hacer cada uno (capacidades, no endpoints)

Escrito en términos de negocio, que es lo que tiene que sobrevivir a los
refactors. Los endpoints salen de acá, no al revés.

| Capacidad | Público | CLIENTE | GERENCIA | ADMIN |
|---|---|---|---|---|
| Ver el catálogo de planes | — | ✓ | ✓ | ✓ |
| Editar el catálogo de planes | — | — | — | ✓ |
| Reclamar su cuenta (código de activación) | ✓ | — | — | — |
| Ver **sus** datos y **sus** pagos | — | ✓ | — | — |
| Buscar y listar socios | — | — | ✓ | ✓ |
| Dar de alta / editar un socio | — | — | ✓ | ✓ |
| Cambiar el estado de un socio a mano | — | — | ✓ | ✓ |
| Dar de baja un socio | — | — | ✓ | ✓ |
| Registrar un pago (cobrar) | — | — | ✓ | ✓ |
| Saber si un socio está al día | — | el suyo | ✓ | ✓ |
| Ver **montos e historial** de pagos de un socio | — | los suyos | **—** | ✓ |
| Ver los **ingresos del negocio** (agregado) | — | — | **—** | ✓ |
| Administrar cuentas de staff | — | — | — | ✓ |
| Vencer socios automáticamente | — | — | — | Sistema |

Las tres filas en negrita son las que el código de hoy **no** respeta: GERENCIA
puede ver montos, historial y agregado (huecos A2 y A5). El resto ya está
implementado correctamente.

### 2.2 La regla que define a los dos roles de staff

> **GERENCIA opera socios y cobra. ADMIN es el único que ve la plata.**

La línea no pasa entre "puntual y global", como estaba planteado antes, sino
entre **operar** y **ver**:

- GERENCIA **registra** pagos: es quien atiende el mostrador y cobra.
- GERENCIA **no ve** ningún dato monetario: ni el listado de pagos, ni el
  historial de un socio, ni el dashboard. Las pantallas de pagos no existen
  para ese rol.
- ADMIN ve todo lo anterior.

### 2.3 El problema que eso genera, y cómo se resuelve

Si a GERENCIA le escondemos los pagos, pierde la respuesta a la pregunta que
más necesita en el mostrador: **"¿este socio está al día?"**. Hoy esa respuesta
solo se obtiene leyendo la tabla de pagos, que es justamente lo que le vamos a
cerrar.

La solución no es devolverle los pagos recortados, es **reconocer que son dos
preguntas distintas**:

| Pregunta | Dato que la responde | Quién |
|---|---|---|
| ¿Está al día? | `Cliente.estado` + hasta cuándo está paga la cuota | GERENCIA y ADMIN |
| ¿Cuánto pagó, cuándo y con qué plan? | el historial de `Pago` | solo ADMIN |

La primera **no tiene ningún monto adentro**. Es un estado y una fecha. Así que
la respuesta de diseño es exponer en el socio su fecha de vencimiento vigente
(la del último pago) junto al `estado` que ya existe, y con eso GERENCIA
atiende el mostrador completo sin tocar un solo dato de dinero.

Concepto detrás: se separa el **dato de negocio sensible** (cuánta plata entró)
del **dato operativo derivado** (hasta cuándo vale la membresía). Son dos
lecturas distintas del mismo hecho, y solo una es confidencial.

Para cobrar, GERENCIA sí ve un monto: el del plan que está cobrando, que sale
del catálogo de planes (público para todo el staff) y que acaba de recibir en
mano. Eso no es una fuga: es la operación que está haciendo. Lo que no ve es
lo que pagaron los demás, ni lo que pagó este mismo socio el mes pasado.

---

## 3. Lo que está bien y no se toca

Vale escribirlo para que el replanteo no se convierta en una reescritura:

- La separación en capas y el uso de interfaz + `impl` en services.
- `Usuario` vs `Cliente` como principales separados, cada uno con su tabla de
  refresh tokens.
- `AuthPrincipal` como record extraído del JWT, sin volver a pegarle a la base
  para saber quién es el que llama.
- El rechazo de refresh tokens fuera de `/refresh` en `JwtAuthenticationFilter`.
- `@JsonProperty(WRITE_ONLY)` en las contraseñas y `@JsonIgnore` en el código
  de activación.
- El flujo de activación por código de un solo uso.
- `GlobalExceptionHandler` centralizado y el `RateLimitFilter`.
- Flyway como fuente de verdad del esquema.

---

## 4. Huecos encontrados

Ordenados por severidad. Cada uno dice qué falla hoy y qué lo arreglaría.

### A. Alta

**A1 — Un pago no registra quién lo cobró.**
`Pago` guarda cliente, plan, monto y fechas, pero no el `Usuario` que lo
registró. Es plata, la registran dos roles distintos, y no hay forma de
auditarlo: si mañana falta dinero en caja, o alguien carga un pago de $1 para
activar a un amigo, el sistema no puede decir quién fue. Para un sistema que
maneja dinero esto es el hueco más importante de la lista.
*Arreglo:* FK `registrado_por` → `usuarios(id)` en `pagos` (migración `V3`),
tomada del `AuthPrincipal`, **nunca del body**.

**A2 — GERENCIA ve todos los datos monetarios.**
`/api/v1/dashboard/**` está restringido a ADMIN, pero **los cuatro endpoints de
lectura de pagos** están abiertos a GERENCIA: `GET /pagos`, `GET /pagos/buscar`,
`GET /pagos/{id}` y `GET /pagos/cliente/{id}`. Esconder el dashboard es una
cortina, no un permiso: con el listado completo se suma a mano.
*Arreglo:* **toda lectura de pagos pasa a ADMIN.** GERENCIA conserva
únicamente `POST /pagos`, es decir cobrar. El único que sigue accediendo a
pagos sin ser ADMIN es el CLIENTE, y solo a los propios, con el chequeo de
ownership que ya existe.

**A3 — Registrar un pago activa al socio sin validar nada.**
`PagoServiceImpl.registrarPago` hace `cliente.setEstado(ACTIVO)` siempre, y:
- acepta `fechaPago` del body, así que un pago retroactivo ya vencido igual
  deja al socio ACTIVO (y `VencimientoScheduler` solo escala estados una vez
  por corrida, nunca revierte);
- acepta cualquier `montoAbonado > 0` sin compararlo con `plan.getPrecio()`,
  así que un pago de $1 activa un mes completo.
*Arreglo (decidido):* **un pago menor al precio del plan se rechaza** — 400,
no se registra, no activa. No existe el pago parcial en este negocio. Y la
activación ocurre solo si la `fechaVencimiento` calculada es futura.
Ojo con el detalle de implementación: el precio a comparar es el del plan **al
momento del cobro**, y `Plan.precio` es mutable por ADMIN. Como `Pago` ya
guarda `montoAbonado`, el histórico queda correcto igual; lo que no hay que
hacer es validar pagos viejos contra el precio nuevo.

**A4 — Credencial por defecto `admin` / `admin123`.**
`DataInitializer` la crea en cualquier entorno donde la tabla `usuarios` esté
vacía, producción incluida. Hoy Render no está vacío, así que no hay una
cuenta viva con esa clave — pero el código la recrearía ante cualquier reset
de base, y la contraseña está en el repositorio.
*Arreglo:* tomar la contraseña inicial de una variable de entorno y fallar al
arrancar si no está definida fuera de `dev`/`test`.

**A5 — No existe la vista "¿está al día?" sin montos.**
Consecuencia directa de cerrar A2: una vez que GERENCIA no lee pagos, no tiene
cómo saber hasta cuándo está paga la cuota de un socio. `Cliente` expone
`estado` (ACTIVO/MOROSO/INACTIVO) pero no la fecha de vencimiento vigente, que
hoy vive únicamente en el último `Pago`.
Sin esto, cerrar A2 deja a GERENCIA sin poder trabajar — así que **A5 y A2 van
juntos, en el mismo tramo**. No se mergea uno sin el otro.
*Arreglo:* exponer la fecha de vencimiento vigente en la respuesta del socio.
Dos caminos, ver §8 pregunta 1: calcularla al vuelo desde el último pago
(sin cambio de esquema, una consulta más) o desnormalizarla en `clientes`
(columna nueva que `registrarPago` y el scheduler mantienen).

### B. Media

**B1 — Las entidades JPA son el contrato del API.**
`ClienteController`, `PagoController`, `PlanController` y `UsuarioController`
reciben y devuelven entidades (`@RequestBody Cliente`, `ResponseEntity<Pago>`)
aunque ya existen `ClienteResponse` y `UsuarioResponse` sin usar. Tres
consecuencias: cada campo nuevo en una entidad se filtra solo al API; cada
campo que no debe llegar del body hay que recordarlo a mano (de ahí los
`setId(null)` desperdigados); y `GET /pagos` serializa el `Cliente` anidado
entero. Es la causa raíz de dos de los tres incidentes del historial.
*Arreglo:* DTO de request y de response por endpoint. **Este es el que más
conviene hacer temprano**: hoy rompe una app, más adelante rompe tres.

**B2 — `usuarios.nombre` es el identificador de login y no es `UNIQUE`.**
`UsuarioServiceImpl.registrar` chequea el duplicado en Java
(`findByNombre(...).isPresent()`), pero la tabla no tiene la constraint
(`V1__baseline.sql`), así que dos altas simultáneas pueden crear dos usuarios
con el mismo nombre — y `autenticar` usa `findByNombre`, que entonces revienta
o elige uno arbitrario. Además `nombre` cumple dos roles a la vez: usuario de
login y nombre de la persona.
*Arreglo:* `UNIQUE` en migración nueva. Separar login de nombre real es
opcional y más invasivo (ver §8, pregunta 3).

**B3 — Listados sin paginación.**
`obtenerTodos()` en clientes y pagos hace `findAll()`. Con unos cientos de
socios y su historial, `GET /pagos` devuelve todo en una respuesta — y es
justo lo que la app mobile va a pedir por red celular.
*Arreglo:* `Pageable` en los listados. Es un cambio de contrato, así que
conviene hacerlo junto con B1 y no dos veces.

**B4 — La severidad del estado depende del orden del enum.**
`VencimientoServiceImpl` compara `estadoCalculado.ordinal() > cliente.getEstado().ordinal()`.
Funciona porque `EstadoCliente` está declarado `ACTIVO, MOROSO, INACTIVO`, pero
agregar un valor en el medio (por ejemplo `SUSPENDIDO`) cambia en silencio la
lógica de vencimiento, sin que falle ningún test ni el compilador.
*Arreglo:* un campo explícito de severidad en el enum.

**B5 — `RuntimeException` genérica para "no encontrado".**
Los services lanzan `new RuntimeException("Cliente no encontrado...")`. El
`GlobalExceptionHandler` lo traduce, pero "no existe" y "algo se rompió"
viajan por el mismo tipo, así que la traducción depende de que nadie lance otra
`RuntimeException` por otro motivo.
*Arreglo:* una `RecursoNoEncontradoException` propia.

### C. Baja

**C1 — El plan actual de un socio es implícito.** `Cliente` no referencia a
`Plan`; el plan vigente es el del último `Pago`. Funciona, pero cada consulta
de "qué plan tiene" ordena pagos. No urge; anotado para no redescubrirlo.

**C2 — `GET /clientes/buscar` devuelve un solo `Cliente`.** Un endpoint que se
llama "buscar" y devuelve un resultado único (y lanza excepción si no hay) es
raro de consumir desde una UI. Revisar junto con B1/B3.

---

## 5. Decisiones de arquitectura

1. **No se agregan roles.** Los cuatro actores alcanzan. Si aparece
   "entrenador" o "recepcionista", eso es una spec propia con su fila en la
   matriz, no un agregado al pasar.
2. **El canal nunca es un rol** (ya en `ARQUITECTURA-APPS.md` §3): nada de
   `if (esMobile)`; la autorización se decide por principal.
3. **La distinción de staff es puntual vs global**, no "lectura vs escritura".
   GERENCIA escribe (cobra, da de alta) y aun así no ve el agregado.
4. **El API habla DTOs, no entidades.** Una entidad JPA modela la base; un DTO
   modela el contrato. Que hayan coincidido hasta ahora fue casualidad.
5. **Todo lo que identifica al que llama sale del `AuthPrincipal`**, nunca del
   body. Vale para `registrado_por` (A1) y para cualquier campo futuro.
6. **Flyway es la fuente de verdad del esquema**, y una migración ya aplicada
   no se edita: siempre `V{n+1}`.

---

## 6. Plan de implementación por fases

Cada fase es un commit (o unos pocos) verificable por separado. **Nada de esto
requiere reescribir la arquitectura.**

Premisa que ordena todo: **no hay nada entregado todavía**. La app Swing corre
pero no está en manos de nadie, así que el backend puede romper su contrato sin
costo. No hay compatibilidad hacia atrás que cuidar, no hay versionado de API
que inventar, no hay migración progresiva. Se cambia el contrato de una y las
UIs se adaptan después (§10).

**Fase 1 — Integridad del dinero** (A1, A3) — ✅ implementada el 2026-09-15
Migración `V3`: `pagos.registrado_por` → `usuarios(id)`, nullable (los pagos
históricos no tienen autor y no se puede inventar). `registrarPago` toma el
autor del `AuthPrincipal`, rechaza montos menores al precio del plan, y activa
al socio solo si el vencimiento calculado es futuro.
*Tests:* el pago guarda el autor correcto; un pago de monto menor al plan
devuelve 400 y no crea nada; un pago retroactivo ya vencido no deja al socio
ACTIVO.

**Fase 2 — GERENCIA deja de ver plata** (A2 + A5, juntos) — ✅ implementada el 2026-09-15
1. Las cuatro lecturas de pagos (`GET /pagos`, `/buscar`, `/{id}`,
   `/cliente/{id}`) pasan a ADMIN en `SecurityConfig`. GERENCIA conserva solo
   `POST /pagos`. El CLIENTE mantiene el acceso a los propios por el chequeo de
   ownership que ya existe en `PagoController`.
2. En el mismo tramo, el socio expone su fecha de vencimiento vigente, para que
   GERENCIA siga sabiendo si está al día (§2.3).
*Tests:* GERENCIA recibe 403 en las cuatro lecturas y 201 al cobrar; un CLIENTE
sigue viendo los suyos y no los de otro; la respuesta del socio trae estado y
vencimiento y **ningún monto**.

Estas dos cosas no se separan: cerrar A2 sin A5 deja a GERENCIA sin poder
atender el mostrador.

**Lección que dejó esta fase, para no repetirla.** Al sacar a GERENCIA del
atajo `esStaff`, los chequeos de ownership de `PagoController` pasaron a
comparar `principal.id()` contra un id de `Cliente`. `usuarios` y `clientes`
son tablas distintas con secuencias de id independientes: los números se
solapan todo el tiempo, ambas arrancan en 1. Un GERENCIA con id de usuario 5
pidiendo `/pagos/cliente/5` habría pasado el chequeo. El arreglo es exigir el
rol antes de comparar el número:

```java
boolean esClienteDuenio = "CLIENTE".equals(principal.rol()) && clienteId.equals(principal.id());
```

Es el mismo patrón que causó los tres incidentes del historial: **un supuesto
tácito del código viejo que deja de valer cuando cambia quién puede llegar
hasta ahí.** Acá el supuesto era "si no es staff, entonces el id es de un
cliente". Al agregar o quitar un rol de una condición, hay que releer qué
asumía la rama de abajo.

**Fase 3 — El contrato del API** (B1, B3, C2) — ✅ implementada el 2026-09-15
DTOs de request y response por endpoint, y `Pageable` en los listados. Es la
fase más grande, y la que mejor aprovecha que no haya nada entregado: es el
momento más barato que va a existir para cambiar el contrato.
*Tests:* por cada endpoint, un assert de que no aparece ningún campo sensible
ni ninguna entidad anidada de más.

Decisiones tomadas dentro de esta fase:
- **Envoltorio propio de paginación** (`PaginaResponse`) en vez de devolver el
  `Page` de Spring Data. La serialización JSON de `PageImpl` no es parte
  estable de su contrato — Spring avisa de eso al arrancar — y un cambio suyo
  arrastraría a las tres apps. La forma de la página es nuestra.
- **El catálogo de planes no se pagina.** Son un puñado de filas; paginarlo
  sería sobreingeniería. Solo se paginan clientes y pagos.
- **`PagoResponse` no expone `registradoPor`.** El CLIENTE dueño puede leer su
  propio pago por `/{id}` y `/cliente/{id}`, y no hay motivo para contarle qué
  empleado lo atendió. El dato sigue siendo auditoría interna hasta que exista
  una vista de pagos solo-ADMIN que justifique exponerlo.
- **Los endpoints `/buscar` devuelven listas**, no un único resultado con
  excepción si no encuentra (hueco C2). Además pasan a **coincidencia parcial
  insensible a mayúsculas**: `findByNombre` era búsqueda exacta, inservible
  para un buscador de UI donde se escribe y se filtra.
- **`PUT /clientes/{id}` edita solo datos de contacto.** El email no se edita
  desde el mostrador porque es la identidad de login del socio en el portal:
  cambiárselo le sacaría el acceso sin que se entere. La contraseña la define
  él en `/registro`. Queda documentado en el javadoc de `ClienteRequest`.

Queda sabido y aceptado: el listado paginado de socios resuelve las fechas de
vencimiento con una consulta que trae el último pago de **todos** los socios,
no solo los de la página, porque `PagoRepository` no tiene un método acotado a
un subconjunto de ids. Sigue siendo una sola consulta sin importar el tamaño de
página, así que no hay N+1; si el volumen crece, el arreglo es un método que
reciba los ids de la página.

**Fase 4 — Higiene** (A4, B2, B4, B5) — ✅ implementada el 2026-09-15
Contraseña inicial por variable de entorno, `UNIQUE` en `usuarios.nombre`,
severidad explícita en `EstadoCliente`, excepción propia de "no encontrado".
Independientes entre sí, se pueden hacer sueltas.

Lo que salió de acá y no estaba previsto:
- El handler genérico devolvía **400 con el mensaje interno** para cualquier
  `RuntimeException` — y `NullPointerException` es una `RuntimeException`, así
  que un bug cualquiera filtraba detalle interno al cliente con un código de
  estado que además mentía. Ahora el 404 sale del **tipo**
  (`RecursoNoEncontradoException`), lo inesperado cae en 500 con mensaje
  genérico, y el detalle se loguea del lado del servidor.
- La guía operativa quedó en `DESPLIEGUE.md`: qué configurar en Render y Neon
  antes del primer arranque, y las trampas (esquema vacío por el baseline de
  Flyway, la URL de Neon que no es JDBC, el `JWT_SECRET` que no se decodifica
  de base64 pese al nombre del parámetro).

**Fase 5 — Higiene de la gestión de staff** — ✅ implementada el 2026-09-15
No estaba en este plan: salió del hueco que quedó anotado al cerrar la Fase 4
(dar de baja a un empleado fallaba siempre contra la base) y de un segundo
problema que apareció al mirarlo, el cambio de contraseña. Tiene spec propia:
`2026-09-15-higiene-staff.md`.

**Después:** con el contrato ya quieto, se escriben los tickets de UI (§10) y
la spec de mobile.

---

## 7. Qué NO incluye este replanteo

Para que no se expanda solo:

- No se cambia la arquitectura de capas ni se agregan patrones nuevos.
- No se migra a otro esquema de autenticación ni se tocan los refresh tokens
  (el tema mobile está en `ARQUITECTURA-APPS.md` §6, y va aparte).
- No se agregan features de negocio (rutinas, asistencia, notificaciones).
- No se toca el `VencimientoScheduler` más allá de B4.
- No se borra el login de clientes por estar sin usar todavía.

---

## 8. Decisiones tomadas y lo que queda abierto

Resueltas el 2026-09-15:

- **Nada está entregado**, así que el backend puede romper contratos libremente.
  No se cuida compatibilidad hacia atrás en ninguna fase.
- **Un pago menor al precio del plan se rechaza** (400). No hay pago parcial.
- **GERENCIA no ve pantallas de pagos**: cobra, pero no lee montos ni historial
  ni agregado. Las cuatro lecturas de pagos pasan a ADMIN.
- **GERENCIA necesita saber si un socio está al día**, y eso se resuelve con
  estado + fecha de vencimiento en el socio, sin montos (§2.3).
- **La fecha de vencimiento del socio se calcula al vuelo** a partir del último
  pago registrado, no se guarda como columna. Es un dato derivado: duplicarlo
  sería crear la deuda técnica que este replanteo trata de evitar. Si algún
  listado se pone lento, se desnormaliza después con evidencia.

Queda abierto (no bloquea ninguna fase):

1. **¿`Usuario.nombre` sigue siendo el usuario de login?** La alternativa es un
   campo aparte y dejar `nombre` como nombre real. Más prolijo, más invasivo;
   se puede resolver en la Fase 4 o dejarlo como está.

---

## 9. Checklist antes de mergear cada fase

- [ ] `AUTHZ-MATRIX.md` actualizado si cambió un permiso.
- [ ] `ARQUITECTURA-APPS.md` actualizado si cambió qué consume una app.
- [ ] Tests corridos y en verde (`./mvnw test`).
- [ ] `/security-review` corrido si se tocó `SecurityConfig`, un controller o
      un DTO de request.
- [ ] Ninguna migración ya aplicada fue editada.

---

## 10. Tickets de UI (se escriben al cerrar el backend)

El backend se replantea primero; recién con el contrato quieto se escribe qué
tiene que cambiar cada app. Anticipo de lo que ya se sabe que va a salir, para
que no se pierda en el camino:

**Escritorio (`Proyecto-Gym-Desktop`)**
- `PagoPanel` deja de mostrarse a GERENCIA (hoy se agrega sin condicionar por
  rol en `MainFrame.initUI`, a diferencia de `UsuarioPanel`).
- Hace falta una forma de **cobrar sin ver la tabla de pagos**: hoy el cobro
  vive dentro de la pantalla que hay que esconder. Probablemente una acción
  "Registrar pago" desde la ficha del socio.
- La ficha del socio muestra estado + vencimiento (dato nuevo de A5).
- **Ya rompió (Fase 2):** los tres GET de `/api/v1/clientes` (listado, `/{id}`
  y `/buscar`) devuelven ahora `ClienteResponse` — id, nombre, apellido,
  telefono, email, estado y `fechaVencimiento` — en vez de la entidad completa.
  El escritorio tiene que leer el campo nuevo y dejar de esperar los que ya no
  vienen.
- Adaptación a los DTOs y a los listados paginados de la Fase 3.
- El error 400 de pago insuficiente necesita un mensaje claro en pantalla.
- **Ya rompió (Fase 5):** la pantalla de cambio de contraseña mandaba
  `{nombre, nuevaContrasena}`. Ahora el endpoint propio pide
  `{contrasenaActual, nuevaContrasena}` y le cambia la clave a quien tiene el
  token, así que la pantalla necesita un campo "contraseña actual" y dejar de
  mandar el nombre. El reset de la clave de otro empleado es una pantalla
  distinta, solo para ADMIN (`PUT /usuarios/{id}/contrasena`).
- La baja de un empleado ya no lo borra: lo desactiva. Si hay una lista de
  usuarios, tiene que distinguir cuentas activas de dadas de baja y ofrecer
  reactivar (`PATCH /usuarios/{id}/activo`), que es la única salida cuando se
  dio de baja a la persona equivocada.

**Web** — esto estaba mal: **sí existe** (React + Vite, del 3–5/09) y está
escrita contra el contrato viejo, así que acumula la migración más grande de las
tres apps. Sus tickets, ya escritos, están en `2026-09-15-tickets-web.md`.
Además le falta el área de GERENCIA, que hoy tiene bloqueada por decisión del
diseño viejo — y sin eso no puede reemplazar al escritorio.

**Mobile** — solo ADMIN y solo consulta, según `ARQUITECTURA-APPS.md`. Nace
contra el contrato nuevo. Su spec se escribe después de la Fase 4.
