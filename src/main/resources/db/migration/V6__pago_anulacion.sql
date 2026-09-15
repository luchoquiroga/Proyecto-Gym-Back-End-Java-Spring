-- Anulación de pagos cargados por error.
--
-- No es un DELETE, y la diferencia no es de estilo. Un pago es el registro de que entró
-- plata y de quién la cobró, así que borrarlo tiene tres efectos que no se ven desde la
-- pantalla que aprieta el botón:
--
--   1. Se pierde `registrado_por` (V3), la auditoría de caja: quién cobró deja de constar.
--   2. La fecha de vencimiento del socio se CALCULA a partir de su último pago (decisión de
--      la Fase 2: es un dato derivado y no una columna). Borrar el último pago le corre el
--      vencimiento hacia atrás sin que nada lo avise, y VencimientoScheduler solo escala
--      estados -- nunca los revierte --, así que el socio queda ACTIVO con una fecha que
--      ya no existe hasta la próxima corrida.
--   3. Las ganancias de un mes ya cerrado cambian sin que quede rastro de que cambiaron.
--
-- Editar el monto sería peor todavía: cambia la plata sin registro del valor anterior. Por
-- eso tampoco existe un PUT /pagos/{id}; corregir un importe es anular y volver a cobrar.
--
-- El motivo es obligatorio a nivel de negocio (lo valida el DTO, no la base): un pago
-- anulado sin explicación no sirve como auditoría, que es el único punto de conservarlo.
-- A nivel de columna se permite NULL porque las filas que ya existen no tienen ninguno.
--
-- `anulado_por` usa ON DELETE SET NULL por el mismo criterio que `registrado_por`, aunque
-- hoy las cuentas de staff ya no se borran (V5 las da de baja lógicamente): si alguna vez
-- se borra una fila de usuarios a mano, el historial de pagos no se cae con ella.
ALTER TABLE pagos ADD COLUMN anulado BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE pagos ADD COLUMN anulado_por INTEGER;
ALTER TABLE pagos ADD COLUMN fecha_anulacion TIMESTAMP;
ALTER TABLE pagos ADD COLUMN motivo_anulacion VARCHAR(300);

ALTER TABLE pagos ADD CONSTRAINT fk_pagos_anulado_por
    FOREIGN KEY (anulado_por) REFERENCES usuarios(id) ON DELETE SET NULL;

-- Índice parcial: todas las consultas de plata (ganancias del mes, vencimiento vigente del
-- socio, listados) pasan a filtrar por pagos NO anulados, que son la enorme mayoría de las
-- filas. Anular es excepcional.
CREATE INDEX idx_pagos_no_anulados ON pagos (cliente_id, fecha_vencimiento) WHERE NOT anulado;
