# Legal y datos personales: TyC, privacidad, consentimiento, backups y licencias

**Estado:** en curso. L6 y L7 hechos el 2026-11-24; el resto espera los datos del
responsable (ver "Pendiente del usuario").

Origen: una checklist legal que trajo el usuario (Ley 25.326, TyC, ARCO, licencias,
B2B, defensa del consumidor, logs de aceptación, backups). Esta spec es la versión
aterrizada a este sistema: qué ya está cubierto, qué no aplica y qué hay que construir.

> Los textos legales que salgan de acá son **borradores**. Lo técnico (qué versión
> aceptó quién, cuándo y desde qué IP) lo garantiza el código; la redacción final la
> tiene que revisar un abogado antes de usarse con clientes reales.

## Roles (define quién responde por qué)

- **El gimnasio** es el *responsable* de la base de datos de sus socios: los carga, los
  usa y decide para qué. Le tocan la inscripción de la base en la AAIP (trámite gratis
  por TAD) y la marca en el INPI.
- **El desarrollador** (el usuario) es el *encargado del tratamiento* / proveedor
  tecnológico. Eso tiene que quedar escrito en el contrato B2B (L8).
- Proveedores que procesan datos y van nombrados en la política de privacidad: Supabase
  (base), Render (API), Vercel (web) y Google Fonts (la web carga Inter desde Google, que
  recibe la IP del visitante).

## Ya cubierto (verificado el 2026-11-24)

- Contraseñas con BCrypt (`BCryptTiempoConstantePasswordEncoder`); HTTPS en Vercel y
  Render; tráfico a Supabase con `sslmode=require`.
- Autorización y ownership con tests (`AtaquesIntegrationTest`, `AUTHZ-MATRIX.md`).
- Sin secretos reales en git (solo los de la base de test local, a propósito).
- **Sin datos sensibles**: del socio se guarda nombre, apellido, teléfono, documento y
  email. Si algún día se agrega apto físico u otro dato de salud, la exigencia legal y de
  seguridad sube mucho: revisar esta spec antes.

## No aplica (y por qué)

- **Botón de arrepentimiento**: es para ventas online a consumidores; el socio paga en
  el mostrador. Si algún día se cobra por la web, pasa a aplicar.
- **GDPR**: no hay usuarios en Europa.
- **Inscripción AAIP y marca INPI**: son del gimnasio (ver Roles), no del código.

## Tickets

| # | Qué | Dónde | Estado |
|---|---|---|---|
| L1 | Páginas públicas `/terminos` y `/privacidad` + footer con links en login, registro y portal del socio | Front | pendiente (necesita datos del responsable) |
| L2 | Checkbox obligatorio "Acepto TyC y Política de Privacidad" en `/socio/registro` | Front | pendiente |
| L3 | Prueba de aceptación: tabla `aceptaciones_legales` (V13: socio, versión, fecha, IP, user-agent); el registro rechaza sin aceptación o con versión vieja. La IP sale igual que en `RateLimitFilter.resolverIpCliente` (detrás de proxy) | Back | pendiente (va junto con L2) |
| L4 | Socios ya registrados: el portal pide aceptar la versión vigente antes de seguir | Back + front | pendiente |
| L5 | ARCO: email de contacto visible en la política y en el portal. La cancelación es **anonimizar** al socio, no borrarlo: los pagos quedan para la caja | Front (email) / back (anonimizar, después) | pendiente |
| L6 | Backups diarios cifrados | Infra | **hecho** 2026-11-24 |
| L7 | Licencias de dependencias transitivas y assets | Back + front | **hecho** 2026-11-24 |
| L8 | Borradores de TyC, Política de Privacidad y puntos del contrato B2B | Docs | pendiente (necesita datos del responsable) |

Orden acordado: L6 → L7 → (L3 + L2) → L1, L4, L5 → L8.

## L6 — Backups (hecho)

`.github/workflows/backup-base.yml`: `pg_dump` diario del esquema `public` a las 03:00
AR, verifica que el dump traiga los datos de cada tabla, lo cifra con AES-256 (gpg,
passphrase por stdin) y lo guarda 30 días como artifact. Puesta en marcha, restauración
y trampas en `DESPLIEGUE.md` §8.

**Falta (lo hace el usuario):** cargar los secrets `SUPABASE_DB_URL` y
`BACKUP_PASSPHRASE`, mergear a `master` (el cron solo corre en la rama por defecto),
correrlo una vez a mano y hacer el simulacro de restauración en una base vacía.

Verificado en local: ida y vuelta dump → cifrado → descifrado con las mismas filas que
`gym_api_local` (3 usuarios, 7 socios, 3 planes, 18 pagos). No se probó restaurar en una
base vacía porque `gimnasio_app` no puede crear bases: queda para el simulacro.

## L7 — Licencias (hecho)

Revisión sin agregar nada al build:

- **Backend**, `./mvnw org.codehaus.mojo:license-maven-plugin:2.4.0:add-third-party
  -Dlicense.excludedScopes=test,provided,system`: 107 dependencias de runtime, todas
  Apache 2.0 / MIT / BSD / EPL / EDL. Cuatro mencionan GPL pero tienen **doble
  licencia** y se usan bajo la permisiva: logback (EPL-2.0 o LGPL-2.1) y las APIs
  `jakarta.annotation` / `jakarta.transaction` (EPL-2.0 o GPL2 con Classpath
  Exception). Ninguna GPL pura ni AGPL.
- **Front**, `pnpm licenses list --prod`: 45 paquetes, 44 MIT y 1 ISC (lucide).
- **Assets**: la tipografía Inter (OFL, por Google Fonts) y dos SVG propios,
  `logo-athletics.svg` y `favicon.svg`, que son la marca del gimnasio: el contrato B2B
  tiene que incluir la autorización para usarla.

Para repetirlo: los mismos dos comandos. Conviene hacerlo al sumar una dependencia.

## Pendiente del usuario (bloquea L1, L5 y L8)

1. Datos del responsable: razón social o nombre del dueño, CUIT, domicilio, email de
   contacto para pedidos ARCO, jurisdicción (¿Mendoza?).
2. ¿El staff también acepta algo? Recomendación: no por ahora (su relación con el
   gimnasio es laboral, es otro documento).
