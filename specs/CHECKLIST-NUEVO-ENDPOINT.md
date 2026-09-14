# Checklist: nuevo endpoint / cambio de autorización

Fuente única de este checklist. Tanto el agente de Claude Code
(`.claude/skills/nuevo-endpoint/SKILL.md`) como el de Antigravity/Gemini
(`.agents/skills/nuevo-endpoint/SKILL.md`) apuntan acá en vez de duplicar estos
pasos — si se ajusta el proceso, se edita **solo este archivo**; los dos
SKILL.md son punteros delgados que no deberían acumular su propia lógica.

Existe porque el feature de login de clientes se implementó sin este chequeo y
dejó dos huecos de autorización en producción (un CLIENTE podía auto-activarse
sin pagar, y un refresh token filtrado servía como access token por 30 días).
Ver `specs/AUTHZ-MATRIX.md` § Historial de incidentes para el detalle. La
causa raíz en ambos casos fue la misma: se agregó un endpoint/rol nuevo sin
releer explícitamente la matriz de permisos completa.

## Cuándo usar esto

Antes de tocar código, si el pedido:
- agrega un endpoint nuevo en cualquier `*Controller.java`;
- agrega o modifica una regla en `SecurityConfig.java`;
- agrega un rol o tipo de principal nuevo (como pasó con `CLIENTE`);
- cambia un DTO de request (nuevos campos que un usuario podría enviar).

Para el resto (fix de bug sin tocar auth, refactor, ajustar un mensaje), no
hace falta — seguir las reglas generales de `AGENTS.md` alcanza.

## Pasos

1. **Leer `specs/AUTHZ-MATRIX.md` completo** (no solo la sección del recurso
   que se está tocando). El objetivo es tener en la cabeza el modelo de
   permisos entero antes de agregar una fila nueva.

2. **Si el cambio es no trivial (agrega rol, toca dinero, o expone datos de
   otro usuario), copiar `specs/TEMPLATE.md`** a
   `specs/<fecha>-<feature>.md` y completar la sección "Modelo de
   autorización" ANTES de escribir el controller. Para un endpoint chico que
   no cambia el modelo de permisos existente, alcanza con completar mentalmente
   esa misma sección sin crear el archivo.

3. **Responder explícitamente:** "¿qué pasa si el usuario con menos
   privilegios del sistema llama a este endpoint con un id que no es el
   suyo?" Si la respuesta no es "403" o "un subconjunto de datos que sí le
   corresponde", el diseño está incompleto.

4. **Decidir dónde va el chequeo:**
   - Restricción por rol de staff (ADMIN/GERENCIA únicamente) → regla en
     `SecurityConfig.securityFilterChain` (`.requestMatchers(...).hasRole(...)`).
   - Restricción de "solo el dueño del recurso, o staff" → `SecurityConfig`
     NO alcanza (no sabe de ownership); hace falta un chequeo a mano en el
     controller usando `@AuthenticationPrincipal AuthPrincipal principal`,
     como en `ClienteController.obtenerPorId` o
     `PagoController.obtenerPagosPorCliente`.
   - Si el endpoint queda bajo `.anyRequest().authenticated()` sin ninguna de
     las dos cosas, cualquier principal autenticado (incluyendo un CLIENTE)
     puede llamarlo. Verificar que eso sea realmente lo querido.

5. **Mass assignment:** si el endpoint recibe una entidad completa por
   `@RequestBody` (no un DTO acotado), revisar en el service que no se pueda
   pisar `id`, `estado`, `rol`, `contrasena`, ni ningún campo que el llamador
   no debería poder auto-asignarse (patrón ya usado en `ClienteServiceImpl.crear`
   y `UsuarioServiceImpl.registrar`).

6. **Tipo de token:** si el endpoint participa del flujo de auth, confirmar
   que un refresh token no pueda usarse ahí en lugar de un access token
   (`JwtAuthenticationFilter` ya rechaza `tipo=refresh` fuera de `/refresh`;
   no reintroducir un camino que lo esquive).

7. **Serialización:** si el campo es una contraseña, hash, o cualquier dato
   que nunca debe volver en una respuesta, anotarlo `@JsonProperty(access =
   WRITE_ONLY)` (o `@JsonIgnore` si tampoco debe poder setearse desde el
   body) en la entidad — patrón ya usado en `Cliente.contrasena`,
   `Usuario.contrasena` y `Cliente.codigoActivacion`. No asumir que devolver
   la entidad completa por un endpoint nuevo es seguro solo porque otros
   endpoints ya la devuelven.

8. **Validación:** agregar anotaciones `jakarta.validation` al DTO de
   request, siguiendo el patrón existente en `LoginRequest`,
   `ClienteRegistroRequest`, etc.

9. **Tests:** agregar un caso de integración por rol relevante, incluyendo el
   caso "un CLIENTE (o el rol con menos privilegios que exista) intenta
   acceder al recurso de otro" esperando 403/404 según corresponda, y si se
   tocó un campo sensible, un assert de que no aparece en la respuesta
   (`jsonPath("$.campo").doesNotExist()`).

10. **Antes de terminar:**
    - Actualizar `specs/AUTHZ-MATRIX.md` con la fila nueva/modificada.
    - Correr los tests (`./mvnw test`).
    - Si se tocó `SecurityConfig`, un controller, o un DTO de request, correr
      `/security-review` (Claude Code) sobre el diff antes de dar el cambio
      por terminado.
