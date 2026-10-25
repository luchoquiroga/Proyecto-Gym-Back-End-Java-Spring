-- La plata pasa de DOUBLE PRECISION a NUMERIC(12,2).
--
-- DOUBLE PRECISION guarda los decimales de forma aproximada (0,1 + 0,2 da
-- 0,30000000000000004). Con montos enteros en pesos no se notaba, pero el total de
-- ganancias del mes es una suma de muchos cobros y con centavos el error se acumula.
-- NUMERIC es exacto: guarda los centavos como enteros, igual que se cuentan en la caja.
-- Del lado de Java es BigDecimal (ver la clase Plata).
--
-- 12 dígitos con 2 decimales llegan a 9.999.999.999,99: sobra para el tope de cordura de
-- 1.000.000.000 que ya ponen PlanRequest y PagoRequest.
--
-- La conversión redondea a dos decimales. Antes de aplicarla en producción se verificó que
-- ningún monto tuviera más (si alguno tiene, esta migración le cambia el valor sin avisar):
--     SELECT * FROM planes WHERE precio <> round(precio::numeric, 2);
--     SELECT * FROM pagos  WHERE monto_abonado <> round(monto_abonado::numeric, 2);
--
-- Los CHECK de la V8 (precio > 0, monto > 0) siguen valiendo tal cual: Postgres los vuelve
-- a verificar sobre la columna nueva.
ALTER TABLE planes ALTER COLUMN precio TYPE NUMERIC(12, 2);
ALTER TABLE pagos ALTER COLUMN monto_abonado TYPE NUMERIC(12, 2);

-- Índices para la limpieza diaria de refresh tokens (LimpiezaRefreshTokensService), que
-- borra por fecha de expiración. Sin índice, cada corrida recorre la tabla entera, y es
-- justo la tabla que crece con cada login.
CREATE INDEX idx_refresh_tokens_expiracion ON refresh_tokens (expiracion);
CREATE INDEX idx_cliente_refresh_tokens_expiracion ON cliente_refresh_tokens (expiracion);
