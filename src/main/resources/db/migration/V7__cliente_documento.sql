-- Documento del socio: obligatorio y único.
--
-- Dos socios homónimos eran indistinguibles en el API. El caso que lo destapó: el ADMIN
-- revisa el desglose de ganancias del mes, encuentra un pago cargado por error y tiene que
-- anularlo, pero PagoResponse.ClienteResumen expone solo id, nombre y apellido, así que dos
-- Juan Pérez se ven como dos filas idénticas. El id los distingue para la máquina, no para
-- la persona que tiene que decidir cuál de los dos pagó.
--
-- El teléfono no servía de reemplazo: es nullable, no tiene validación, y el caso realista
-- de dos homónimos en un gimnasio es padre e hijo, que comparten el teléfono de la casa.
--
-- Se hace ahora porque producción se recrea vacía (ver DESPLIEGUE.md): una columna
-- NOT NULL UNIQUE contra una tabla vacía es gratis, y la misma columna con cientos de
-- socios ya cargados es un backfill a mano y una restricción que nunca se llega a activar.
--
-- Va en tres pasos porque las bases de desarrollo y gimnasio_test ya tienen filas. El
-- placeholder es deliberadamente feo: si una base con datos reales pasara por acá, tiene
-- que cantar en pantalla que a ese socio le falta el documento, no disimularse como válido.
ALTER TABLE clientes ADD COLUMN documento VARCHAR(20);

UPDATE clientes SET documento = 'PENDIENTE-' || id WHERE documento IS NULL;

ALTER TABLE clientes ALTER COLUMN documento SET NOT NULL;

-- El valor guardado siempre está normalizado (sin puntos, espacios ni guiones, en
-- mayúsculas): lo hace ClienteServiceImpl antes de persistir. Sin esa normalización esta
-- restricción sería decorativa, porque '12.345.678' y '12345678' entrarían como dos socios
-- distintos siendo la misma persona, que es exactamente el problema que la columna viene a
-- resolver.
ALTER TABLE clientes ADD CONSTRAINT uk_clientes_documento UNIQUE (documento);
