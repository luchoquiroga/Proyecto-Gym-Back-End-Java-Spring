# Spec: <nombre del feature>

Fecha: <YYYY-MM-DD>
Estado: borrador | en revisión | implementado

## 1. Qué resuelve

2-3 líneas: qué necesidad de negocio cubre, quién lo pidió o por qué hace falta.

## 2. Alcance

Qué incluye este cambio y, explícitamente, qué NO incluye (para evitar scope
creep durante la implementación).

## 3. Modelo de autorización (obligatorio si toca auth, roles, dinero, o datos de otro usuario)

- **Quién puede llamar a esto:** roles/principales habilitados.
- **Ownership:** si el recurso pertenece a alguien, ¿quién puede tocar el
  propio y quién puede tocar el de otros? ¿Dónde se chequea — en
  `SecurityConfig` (regla de ruta) o a mano en el controller (regla de
  ownership)? Si es en el controller, anotar explícitamente que hace falta,
  porque `SecurityConfig` sola no alcanza.
- **Pregunta de sanity check:** ¿qué pasa si el usuario con MENOS privilegios
  del sistema (hoy: un CLIENTE recién registrado) llama a este endpoint con
  un id que no es el suyo? Escribir la respuesta esperada acá.
- **Endpoints nuevos o modificados:** listarlos con método + ruta + rol/ownership
  requerido, para copiar directo a `AUTHZ-MATRIX.md` al terminar.

## 4. Datos

- Campos nuevos en el modelo/DTO, y cuáles NO deben poder venir del body
  (mass assignment: `id`, `estado`, `rol`, cualquier campo que el propio
  usuario no debería poder auto-asignarse).
- Migraciones de base necesarias (Flyway `V{n}__`).

## 5. Validación

- Qué inputs son obligatorios / tienen formato específico (usar
  `jakarta.validation` como el resto del proyecto).
- Qué pasa ante datos inválidos o duplicados (¿409, 400? — ver
  `GlobalExceptionHandler`).

## 6. Casos de error / borde

Listar los que importan: token expirado, recurso no encontrado, duplicado,
rol insuficiente, etc. y qué status/mensaje debe devolver cada uno.

## 7. Tests a agregar

- Integración: casos felices + los de la sección 6, para cada rol relevante
  (incluyendo "un CLIENTE intentando acceder a lo de otro CLIENTE" si aplica).
- Unit: lógica de negocio nueva en el service.

## 8. Checklist antes de mergear

- [ ] `AUTHZ-MATRIX.md` actualizado con las filas nuevas/modificadas.
- [ ] Tests corridos y en verde.
- [ ] `/security-review` corrido si se tocó `SecurityConfig`, un controller,
      o un DTO de request.
