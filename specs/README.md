# specs/

Esta carpeta es donde vive el "spec-driven development" de este repo: antes de
tocar código en un cambio que agregue un endpoint nuevo, un rol nuevo, o toque
quién-puede-hacer-qué, ese diseño se escribe acá primero, en texto plano, y se
discute/ajusta ANTES de escribir la implementación.

No es burocracia por la burocracia: la razón concreta de que esta carpeta
exista es que el feature de login de clientes (doble token, portal web) se
implementó sin este paso, y terminó dejando dos huecos de autorización reales
en producción (ver `AUTHZ-MATRIX.md` § Historial) — un Cliente autenticado
podía escalar su propio estado a ACTIVO sin pagar, y un refresh token filtrado
servía como access token por 30 días en vez de 30 minutos. Ninguno de los dos
era un bug de lógica difícil de ver in situ; eran huecos que aparecen cuando
se agrega una fila nueva a la matriz de permisos (un rol nuevo, CLIENTE) sin
releer explícitamente toda la matriz.

## Contenido

- **`AUTHZ-MATRIX.md`** — la fuente de verdad de quién puede llamar a cada
  endpoint hoy. Se actualiza en el mismo commit que cualquier cambio a
  `SecurityConfig` o a un chequeo de ownership en un controller. Si el código
  y este archivo no coinciden, el archivo está desactualizado y hay que
  corregirlo — no es al revés.
- **`TEMPLATE.md`** — la plantilla a copiar para especificar un feature nuevo
  antes de implementarlo.
- Un archivo por feature grande (ej. `2026-09-cliente-portal-web.md`) para los
  que ya se escriba spec de acá en adelante.

## Cuándo escribir un spec antes de codear

Sí, siempre:
- Se agrega un rol/tipo de principal nuevo (como pasó con `CLIENTE`).
- Se agrega un endpoint que toca dinero, contraseñas, o datos de otro usuario.
- Se cambia el esquema de autenticación/autorización.

Para el resto (un fix de bug, un refactor, agregar un campo de validación) no
hace falta spec: alcanza con lo que ya pide `AGENTS.md` (analizar antes de
tocar, explicar el cambio, verificar que compile y los tests pasen).

## Flujo sugerido

1. Copiar `TEMPLATE.md` a `specs/<fecha>-<nombre-feature>.md`.
2. Completar "Modelo de autorización" ANTES de escribir código — quién llama,
   qué rol necesita, qué pasa si intenta acceder a un recurso ajeno.
3. Revisar ese modelo con una pregunta simple: "¿qué pasa si el usuario que
   menos privilegios tiene en el sistema llama a este endpoint?"
4. Implementar. El PR/commit que cierra el feature actualiza
   `AUTHZ-MATRIX.md` con las filas nuevas.
5. Correr (o pedirle a Claude que corra) `/security-review` antes de mergear
   cualquier cosa que haya tocado `SecurityConfig`, un controller, o un DTO
   de request.
