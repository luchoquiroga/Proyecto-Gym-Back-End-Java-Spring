-- Trazabilidad de caja: hasta acá un pago guardaba cliente, plan, monto y fechas,
-- pero no quién lo había cobrado. Con dos roles de staff (ADMIN y GERENCIA) cargando
-- pagos, un faltante de caja o un pago cargado de favor no eran atribuibles a nadie.
-- El autor se toma siempre del token del que llama (AuthPrincipal), nunca del body.
--
-- NULL permitido a propósito: los pagos ya registrados antes de esta migración no
-- tienen autor conocido y no se puede inventar uno. ON DELETE SET NULL para que dar
-- de baja a un usuario de staff no borre ni bloquee el historial de pagos.
ALTER TABLE pagos ADD COLUMN registrado_por INTEGER;
ALTER TABLE pagos ADD CONSTRAINT fk_pagos_registrado_por
    FOREIGN KEY (registrado_por) REFERENCES usuarios(id) ON DELETE SET NULL;
