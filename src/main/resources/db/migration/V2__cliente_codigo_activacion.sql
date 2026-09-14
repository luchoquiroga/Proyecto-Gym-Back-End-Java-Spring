-- Reemplaza la verificación de identidad de /api/v1/clientes/registro (antes por
-- nombre+apellido+teléfono, datos adivinables/conocidos por terceros) por un código
-- de activación de un solo uso: el staff lo genera al dar de alta al cliente y se lo
-- entrega en persona. Se anula (vuelve a NULL) apenas se usa.
ALTER TABLE clientes ADD COLUMN codigo_activacion VARCHAR(10);
ALTER TABLE clientes ADD CONSTRAINT uk_clientes_codigo_activacion UNIQUE (codigo_activacion);
