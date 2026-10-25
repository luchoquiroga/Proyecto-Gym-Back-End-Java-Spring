-- Dos redes de seguridad en la base, que no cambian nada de lo que hace la app.
--
-- 1. RLS activado en todas las tablas, SIN políticas.
--
-- Quién ve qué lo decide este backend (SecurityConfig y los chequeos de ownership de los
-- controllers), no la base: la app se conecta siempre con un único usuario, que es el
-- dueño de las tablas, y el dueño se saltea RLS (no se usa FORCE a propósito). Para la
-- app, entonces, esto no cambia nada.
--
-- Lo que cubre es otra puerta: la Data API de Supabase, que publica por REST todas las
-- tablas de `public` a cualquiera con la clave `anon`, que no es secreta. Está apagada
-- (DESPLIEGUE.md §1), pero eso es un interruptor en un panel. Con RLS activo y sin
-- políticas, si alguien la vuelve a prender, los roles `anon` y `authenticated` leen
-- cero filas en vez de `usuarios`, `clientes` y `pagos` enteras.
--
-- No se escriben políticas por socio porque serían una copia en SQL de las reglas de
-- Java: con el pool de conexiones la base no sabe qué persona hizo cada pedido, y dos
-- lugares con la misma regla terminan diciendo cosas distintas.
--
-- `flyway_schema_history` no se toca: es de Flyway y no tiene datos del gimnasio.
ALTER TABLE usuarios ENABLE ROW LEVEL SECURITY;
ALTER TABLE clientes ENABLE ROW LEVEL SECURITY;
ALTER TABLE planes ENABLE ROW LEVEL SECURITY;
ALTER TABLE pagos ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE cliente_refresh_tokens ENABLE ROW LEVEL SECURITY;

-- 2. Las reglas de plata y fechas, también en la base.
--
-- Hasta acá las validaban solo los DTO (PlanRequest, PagoRequest) y PagoServiceImpl. Eso
-- alcanza mientras todo entre por el API; un UPDATE a mano en la consola de Supabase o un
-- bug en un servicio nuevo pueden dejar un plan de 0 días o un pago negativo, y las
-- ganancias del mes los suman sin avisar. Cada CHECK repite una regla que Java ya aplica,
-- así que ningún pedido válido de hoy pasa a fallar.
--
-- Si esta migración falla, la base ya tiene filas que rompen la regla y hay que mirarlas
-- antes de reintentar (corregir plata no lo decide una migración):
--     SELECT * FROM planes WHERE duracion <= 0 OR precio <= 0;
--     SELECT * FROM pagos  WHERE monto_abonado <= 0 OR fecha_vencimiento < fecha_pago;
ALTER TABLE planes ADD CONSTRAINT ck_planes_duracion_positiva CHECK (duracion > 0);
ALTER TABLE planes ADD CONSTRAINT ck_planes_precio_positivo CHECK (precio > 0);

ALTER TABLE pagos ADD CONSTRAINT ck_pagos_monto_positivo CHECK (monto_abonado > 0);
-- Mayor o igual y no mayor: el período puede empezar después del pago (renovación antes
-- de vencer), pero nunca vence antes de haberse pagado.
ALTER TABLE pagos ADD CONSTRAINT ck_pagos_vencimiento_posterior
    CHECK (fecha_vencimiento >= fecha_pago);

-- Una anulación es completa o no existe (V6). Se exigen la fecha y el motivo, pero NO
-- `anulado_por`: esa columna es ON DELETE SET NULL a propósito, y exigirla haría fallar
-- el borrado a mano de una cuenta de staff que alguna vez anuló un pago.
ALTER TABLE pagos ADD CONSTRAINT ck_pagos_anulacion_completa
    CHECK (NOT anulado OR (fecha_anulacion IS NOT NULL AND motivo_anulacion IS NOT NULL));
ALTER TABLE pagos ADD CONSTRAINT ck_pagos_sin_anulacion_sin_datos
    CHECK (anulado OR (anulado_por IS NULL AND fecha_anulacion IS NULL AND motivo_anulacion IS NULL));
