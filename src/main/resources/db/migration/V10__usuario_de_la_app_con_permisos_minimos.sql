-- La app deja de conectarse como dueña de las tablas.
--
-- Hasta acá un único usuario hacía todo: Flyway creaba las tablas con él y la app atendía
-- cada pedido con él. El dueño puede DROP, TRUNCATE y DELETE en cualquier tabla, así que la
-- regla "un pago no se borra nunca, se anula" (V6) y las bajas lógicas de socios y de
-- staff (V5) dependían de que ningún código Java hiciera un DELETE. Ni un bug, ni un
-- endpoint nuevo mal hecho, ni alguien con la contraseña de Render tenían un freno en la base.
--
-- Desde acá son dos usuarios:
--   - el dueño (el de siempre): solo lo usa Flyway, al arrancar, para migrar;
--   - gimnasio_runtime: el de la app. Lee, inserta y modifica todo, y borra SOLO lo que la
--     app borra hoy: planes (DELETE /planes/{id}) y refresh tokens (la limpieza diaria).
--
-- gimnasio_runtime NO se crea acá, porque lleva contraseña y una migración queda en el
-- repo. Se crea a mano en cada base ANTES de desplegar esta migración (DESPLIEGUE.md §1):
-- si no existe, el GRANT falla y la app no arranca, que es lo que tiene que pasar.
--
-- Tiene BYPASSRLS: la V8 activó RLS sin políticas y la app, al no ser más la dueña, vería
-- cero filas. BYPASSRLS es "este usuario no pasa por RLS", sin escribir políticas que
-- dupliquen nada. A anon y authenticated (los de la Data API de Supabase) RLS los sigue
-- frenando.
--
-- Si se agrega una tabla nueva, toma SELECT, INSERT y UPDATE sola (ALTER DEFAULT
-- PRIVILEGES, abajo). DELETE nunca viene solo: si la app tiene que borrar en una tabla
-- nueva, se concede en la migración que la crea, a propósito y a la vista.
-- PermisosDeLaBaseIntegrationTest verifica todo esto.
GRANT USAGE ON SCHEMA public TO gimnasio_runtime;

GRANT SELECT, INSERT, UPDATE
    ON usuarios, clientes, planes, pagos, refresh_tokens, cliente_refresh_tokens
    TO gimnasio_runtime;

GRANT DELETE ON planes, refresh_tokens, cliente_refresh_tokens TO gimnasio_runtime;

-- Las secuencias de las columnas GENERATED ... AS IDENTITY no necesitan permiso aparte: el
-- INSERT sobre la tabla alcanza (a diferencia de SERIAL).

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE ON TABLES TO gimnasio_runtime;

-- Supabase les da por defecto todos los permisos sobre las tablas de `public` a anon y a
-- authenticated, los usuarios de su Data API. Esa API está apagada y RLS los deja en cero
-- filas (V8); esto es la tercera capa: ni siquiera tienen el permiso. Esos usuarios solo
-- existen en Supabase, así que en una base local esto no hace nada.
DO $$
DECLARE
    usuario_supabase TEXT;
BEGIN
    FOREACH usuario_supabase IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = usuario_supabase) THEN
            EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA public FROM %I', usuario_supabase);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM %I',
                           usuario_supabase);
        END IF;
    END LOOP;
END
$$;
