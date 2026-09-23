# Stack del frontend: la web y el shell de escritorio

Fecha: 2026-09-18
Estado: vigente — base tecnológica acordada antes de empezar los tickets

> **La copia viva de este documento está en el repo del front**, en
> `Gym-Project-Front-End\specs\STACK.md`, junto con el resto del SDD de la web
> (contrato, tickets, bitácora, checklist). Este archivo queda como registro de
> cómo se decidió el stack; si hay que ajustarlo, se ajusta allá.

Este documento no sigue `TEMPLATE.md`: esa plantilla es para especificar un
endpoint o un cambio de autorización. Esto es un documento de arquitectura,
hermano de `ARQUITECTURA-APPS.md`. Aquel dice **qué app consume qué**; este dice
**con qué se construye la web y cómo se usa cada pieza**.

El alcance de producto no se decide acá: está cerrado en `ARQUITECTURA-APPS.md`
§2.1 y desglosado en tickets en `2026-09-15-tickets-web.md`. Acá se decide la
base técnica para poder ejecutar esos tickets sin discutir la herramienta en
cada uno.

---

## 1. El punto de partida real

La web **ya existe** en `C:\Users\lucia\Project Visual Studio Code\Gym-Project-Front-End`:
maqueta navegable del 3–5/09, 1.629 líneas de TS/TSX, con auth de staff que
funciona de verdad y cuatro lecturas. No se empieza de cero y no se reescribe:
el stack de abajo es en su mayoría **el que ya está elegido**, más las piezas
que faltan para poder escribir (hoy no hay una sola mutación).

Lo que ya está y no se toca:

| Capa | Elegido | Por qué se queda |
|---|---|---|
| Base | React 19 + Vite + TypeScript | Es lo que hay, funciona y es lo correcto para una SPA detrás de login |
| Router | `react-router-dom` 7 | Las tres áreas son rutas con guard; ya está resuelto en `RoleRoute` |
| Estado de servidor | `@tanstack/react-query` | Cache, `isError`, invalidación tras mutar. W1 depende de sus estados |
| Estado de cliente | `zustand` | Solo sesión, en RAM. Ver §5 |
| HTTP | `axios` (instancia única con interceptores) | El interceptor de 401 con cola de peticiones es lo mejor del repo. **No se reescribe con `fetch`** |
| Estilos | Tailwind 3 + `lucide-react` | Ya hay un tema propio (`gym-black`, `gym-red-*`) |
| Lint / PM | `oxlint` + `pnpm` | Rápidos y ya configurados |

---

## 2. Lo que se agrega, y qué problema concreto resuelve cada cosa

Ninguna de estas entra "por completitud": cada una existe porque hay un ticket
del alcance que sin ella se hace a mano y mal.

### 2.1 `react-hook-form` + `zod` — formularios

Casi todo el alcance nuevo son formularios: alta y edición de socio, cobrar,
crear plan, crear cuenta de staff, cambiar contraseña. El motivo de usar una
librería y no `useState` por campo no es comodidad, es esto:

> El backend valida y rechaza con **400 y un mensaje**, y varias reglas
> distintas devuelven 400 (documento duplicado, monto menor al precio del plan,
> nombre de usuario repetido). El formulario tiene que poder poner ese mensaje
> **en el campo que lo causó**, no en un `alert` genérico.

`zod` da el esquema del lado del cliente (obligatorios, formato, tipos), y
`react-hook-form` expone `setError('documento', ...)` para inyectar lo que
devolvió el servidor. La validación del cliente es **comodidad, nunca
seguridad**: la fuente de verdad es el backend y se asume que puede rechazar
algo que el front dio por bueno.

### 2.2 `@tanstack/react-table` — listados

Socios y pagos son listas largas. **El backend ya pagina, ordena y busca del
lado servidor** (Fase 3: `/clientes` paginado, `/clientes/buscar`, `/pagos` con
filtro de fechas). Entonces la tabla se usa en modo `manualPagination` /
`manualFiltering`: renderiza y maneja columnas, **no** ordena ni filtra en
memoria. Una tabla que filtra en memoria sobre una página de 20 filas miente:
parece que busca en todos los socios y busca en los 20 que tiene a mano.

### 2.3 `date-fns` — fechas

El portal del socio muestra **cuántos días faltan para pagar**, restando
`fechaVencimiento` contra hoy, y el dashboard filtra pagos por rango. Hacer eso
con `Date` a mano es exactamente donde aparecen los off-by-one: el backend manda
la fecha como texto ISO, `new Date('2026-09-30')` se interpreta en UTC y en
Argentina (UTC-3) puede mostrar el día anterior. `date-fns` con `parseISO` +
`differenceInCalendarDays` lo resuelve y se lee.

### 2.4 `recharts` — un solo gráfico

`GET /dashboard/ganancias-mensuales`, área ADMIN. Nada más. Si termina siendo
una tabla y un número grande, se saca la dependencia.

### 2.5 `shadcn/ui` — componentes

Recomendado, no obligatorio. No es una dependencia en el sentido clásico: el CLI
**copia el código del componente a tu repo**, sobre Radix + Tailwind, y a partir
de ahí es tuyo. Da dialog, select, tabla, toast, form y tooltip ya accesibles
(foco, teclado, `aria-*`), que es donde se va el tiempo en un CRUD y donde una
implementación propia queda a medias.

La alternativa honesta es seguir a mano con Tailwind puro: más control sobre el
tema oscuro que ya está armado, bastante más trabajo en modales y selects. Si se
elige shadcn, se adopta **componente por componente a medida que se necesita**,
no todo de una.

### 2.6 `vitest` + `@testing-library/react` — tests

No se testea la maqueta. Se testean tres cosas, que son las que si se rompen no
se ven mirando la pantalla:

1. El interceptor de axios: que un 401 dispare **un solo** refresh, que encole
   las peticiones concurrentes, y que un refresh fallido limpie la sesión.
2. Los guards de rol: que GERENCIA no entre al dashboard y que un socio no entre
   a nada de staff. (La seguridad real la da el backend con 403; esto evita
   mostrar una pantalla rota.)
3. El cálculo de días hasta el vencimiento, con un caso en cada borde: hoy,
   ayer, y el cambio de mes.

`msw` (Mock Service Worker) para los dos primeros: intercepta a nivel red, así
el interceptor se ejercita de verdad en vez de mockear axios.

### 2.7 Lo que NO se usa

| Descartado | Por qué |
|---|---|
| Next.js | No hay SEO ni SSR: todo está detrás de login. Y complicaría el shell de escritorio, que quiere una SPA estática servida por URL |
| Redux / Redux Toolkit | El estado de servidor lo tiene react-query y el de sesión zustand. No queda estado global que justifique el boilerplate |
| MUI / Chakra | Traen su propio sistema de tema y pelean con el Tailwind que ya está |
| Cliente generado desde OpenAPI | El contrato es chico y quedó quieto después de las 8 fases. El costo del generador supera lo que ahorra |
| `localStorage` para el access token | Decisión ya tomada y es correcta: token en RAM, sesión en cookie HttpOnly. **No se reabre** |

---

## 3. Cómo se usa: convenciones

Estas son las reglas que hacen que el stack rinda. Sin ellas, tener react-query
instalado no sirve de nada.

### 3.1 Estructura por feature, no por tipo técnico

Hoy es `pages/admin/ClientesPage.tsx` + `api/axios.ts` + `types/auth.ts`. Con
tres áreas y escrituras eso se desarma rápido. La forma destino:

```
src/
  api/            axios.ts (la instancia y el interceptor, nada más)
  features/
    socios/       api.ts  hooks.ts  schemas.ts  components/  pages/
    pagos/        idem
    planes/       idem
    staff/        idem
    portal-socio/ idem
  auth/           sesión, guards, tipos del principal (§5)
  components/ui/  lo compartido (shadcn va acá)
  lib/            fechas, formato de plata, normalización de errores
```

La regla es: **una carpeta por área del alcance**. Cuando un ticket dice "socios:
listar, buscar, crear, modificar e inhabilitar", todo lo que toca vive junto.

### 3.2 Ningún componente llama a axios

Un componente usa un hook. El hook usa un módulo `api.ts` de su feature. Ese
módulo es el único que conoce la ruta y el DTO:

```ts
// features/socios/api.ts       — conoce el contrato
export const listarSocios = (params: SociosQuery) =>
  api.get<Pagina<Socio>>('/api/v1/clientes', { params }).then(r => r.data);

// features/socios/hooks.ts     — conoce el cache
export const useSocios = (params: SociosQuery) =>
  useQuery({ queryKey: ['socios', params], queryFn: () => listarSocios(params) });

// features/socios/pages/...    — no conoce ninguna de las dos cosas
const { data, isLoading, isError, error } = useSocios({ page, q });
```

Beneficio concreto: cuando el backend cambia un DTO, hay **un** archivo por
recurso que tocar, y el grep de "qué pantallas se rompen" es inmediato.

### 3.3 Claves de cache y qué invalidar

`queryKey` siempre en el orden recurso → parámetros: `['socios', { page, q }]`,
`['socios', id]`, `['pagos', { desde, hasta }]`, `['planes']`,
`['dashboard', 'ganancias', anio]`.

Después de una mutación se invalida el recurso entero, y también **lo que esa
escritura cambió indirectamente**. Esto último es la parte que se olvida y acá
importa mucho:

- Cobrar (`POST /pagos`) cambia el **estado y el vencimiento del socio**, porque
  el estado ACTIVO lo determina un pago válido. Invalidar `['socios']`,
  `['pagos']` y `['dashboard']`.
- Anular un pago (`POST /pagos/{id}/anulacion`) hace lo mismo al revés: puede
  dejar al socio sin vencimiento. Mismas tres invalidaciones.
- Dar de baja un socio (`PATCH /clientes/{id}/estado`) invalida `['socios']`.

Si esto se hace mal, la pantalla muestra en MOROSO a un socio al que le acabás
de cobrar, y el cajero cobra dos veces.

### 3.4 Un error se ve como un error

Regla que sale del ticket W1 y vale para todo lo nuevo: **prohibido el `catch`
que devuelve datos inventados.** Si el backend falla, se muestra el estado de
error de react-query con un mensaje. Si alguna vez hace falta una maqueta sin
backend, va detrás de un flag explícito (`VITE_USE_MOCKS`), nunca de un `catch`.

Para que el mensaje sea el del servidor y no "Request failed with status code
400", el interceptor normaliza la respuesta a un tipo propio (el backend
responde `MensajeResponse` en casi todos los errores) y lo demás muestra un
texto genérico.

### 3.5 El rol esconde, no protege

`RoleRoute` ya hace lo correcto. La regla al escribir pantallas nuevas: los
botones de ADMIN (dashboard, anular pago, crear staff, editar plan) se esconden
según `principal.rol`, pero **el que decide es el backend con 403**. El canal no
es un rol y el front no es una frontera de seguridad: es una capa de comodidad
sobre una frontera que vive en `SecurityConfig`.

Corolario: un 403 inesperado no se traga en silencio, se muestra. Es la señal de
que la pantalla y la matriz de permisos se desincronizaron.

### 3.6 Variables de entorno

Todo lo que empieza con `VITE_` **termina en el bundle y es público**. Ahí van
solo `VITE_API_URL` y flags. Nunca una credencial, nunca una clave de nada.

---

## 4. Despliegue y el borde que hay que configurar

La web es un build estático (`pnpm build` → `dist/`) servible en Vercel, Netlify
o un static site de Render. El API sigue en Render, en **otro dominio**, y eso
tiene dos consecuencias que ya están previstas en el backend y hay que recordar
al desplegar:

1. **CORS**: `CORS_ALLOWED_ORIGINS` en Render tiene que incluir el origen exacto
   de la web desplegada (hoy el default es `http://localhost:5173`). Sin esto no
   funciona ni el login.
2. **La cookie de refresh es cross-site**: el backend ya la emite con
   `SameSite=None; Secure` en producción (`REFRESH_COOKIE_SAMESITE`) y `Lax` sin
   `Secure` en local. Por eso axios va con `withCredentials: true` y por eso la
   web local corre en `http://localhost:5173` contra `http://localhost:8080`.

Detalle en `specs/DESPLIEGUE.md`; no lo reconstruyas de memoria.

---

## 5. El agujero que el stack tiene que cerrar el día uno: hay dos principals

Esto no es un ticket de pantalla, es una decisión de base, y si se deja para
después contamina todo lo que se escriba encima.

El backend tiene **dos tipos de principal deliberadamente separados**: `Usuario`
(staff, `ADMIN`/`GERENCIA`) y `Cliente` (socio). Son dos flujos de login
distintos, dos endpoints de refresh distintos y **dos cookies distintas**
(`refreshToken` vs `clienteRefreshToken`, nombradas distinto justamente para
poder convivir en el mismo navegador).

El front de hoy no refleja eso:

- `types/auth.ts` declara `type RolUsuario = 'ADMIN' | 'CLIENTE' | 'GERENCIA'`,
  que es **exactamente la mezcla que el backend se cuidó de no hacer**. En el
  backend, `CLIENTE` no es un `RolUsuario`: es un claim de otro tipo de token.
- `AuthInitializer` y el interceptor llaman siempre a `VITE_REFRESH_ENDPOINT`,
  fijo en `/api/v1/usuarios/refresh`. O sea que **la sesión de un socio no se
  puede restaurar nunca**: recarga la página y se cae a login, y su 401 dispara
  un refresh de staff que no le corresponde.

La forma correcta, y es barata si se hace ahora:

```ts
// auth/types.ts — unión discriminada, no un enum aplanado
export type Principal =
  | { tipo: 'staff'; id: number; nombre: string; rol: 'ADMIN' | 'GERENCIA' }
  | { tipo: 'socio'; id: number; nombre: string };
```

Y una config por portal —endpoints de login/refresh/logout— que el store de
sesión y el interceptor leen del principal activo, en vez de una constante.
Para el arranque en frío (no sabemos quién es hasta que el refresh responde) se
guarda **el último portal usado** en `localStorage`; es un dato no sensible
(`'staff' | 'socio'`), no es una credencial, y evita disparar dos refresh en
cada carga.

Con eso, `RoleRoute` pasa a preguntar por `principal.tipo` primero y por
`principal.rol` después, y desaparece el caso raro de hoy donde un socio y un
admin se comparan contra la misma lista de roles.

**Y en el mismo movimiento se borra el bloqueo a GERENCIA** (`RoleRoute` y
`Login` la mandan a `/acceso-restringido`), que viene del diseño viejo y es lo
que hoy impide que la web reemplace al escritorio.

---

## 6. El escritorio

Decisión ya tomada en `ARQUITECTURA-APPS.md`: el escritorio es un **envoltorio
de la web**, no una UI nativa. Entonces su stack es casi nada.

**Elegido: Tauri v2**, cargando la **URL de la web desplegada**. Windows 11 ya
trae WebView2, el instalador queda en el orden de los 5 MB y no arrastra un
Chromium propio. El costo es instalar la toolchain de Rust una vez; el `main.rs`
de un shell es de unas veinte líneas y no se vuelve a tocar.

**Alternativa válida: Electron**, si no querés tocar Rust. Cero lenguaje nuevo,
instalador de ~120 MB. Para un shell puro la diferencia funcional es nula.

Lo que sí es una decisión técnica y no una preferencia:

> El shell carga la URL de la web desplegada. **No empaqueta el SPA adentro.**

Si se empaqueta el build, la app corre en origen `tauri://localhost` (o
`file://`) y las llamadas al API pasan a ser cross-origin desde un origen que no
es un sitio: la cookie `refreshToken` se vuelve cookie de tercero y **el silent
refresh deja de funcionar**. Resultado: la misma sesión que muere a los 30
minutos que sufre hoy la app Swing, que es justamente lo que se quiere dejar
atrás. Cargando la URL real, el shell hereda el auth de la web sin escribir una
línea de sesión.

Responsabilidad del shell, completa: ventana, ícono, título, auto-update, y que
cerrar la ventana no mate la sesión. **Cero lógica de negocio, cero llamadas
propias al API, cero superficie nueva.** Si algún día el shell necesita llamar al
API por su cuenta, esa es la señal de que la decisión de `ARQUITECTURA-APPS.md`
se está reabriendo, y se reabre en ese documento, no en el código del shell.

---

## 7. Orden de adopción

Nada de instalar las ocho dependencias hoy. Cada una entra con el ticket que la
necesita, así el `package.json` siempre refleja algo que se usa:

| Paso | Qué | Dependencias nuevas |
|---|---|---|
| 0 | git init + commit de la maqueta tal cual está | ninguna |
| 1 | **W1**: sacar los mocks de los `catch` + normalizar el error | ninguna |
| 2 | Los dos principals (§5) + habilitar GERENCIA | ninguna |
| 3 | Socios: crear / editar / inhabilitar | `react-hook-form` `zod` |
| 4 | Listados paginados y búsqueda contra el servidor | `@tanstack/react-table` |
| 5 | Cobrar, y portal del socio con los días restantes | `date-fns` |
| 6 | Dashboard de ADMIN | `recharts` |
| 7 | Tests del interceptor y de los guards | `vitest` `@testing-library/react` `msw` |
| 8 | Shell de escritorio | Tauri (repo aparte) |

Los pasos 1 y 2 **no agregan una sola dependencia** y son los que más deuda
sacan. Por eso van primero.

Comandos, cuando toque cada paso:

```bash
pnpm add react-hook-form zod @hookform/resolvers
pnpm add @tanstack/react-table
pnpm add date-fns
pnpm add recharts
pnpm add -D vitest @testing-library/react @testing-library/jest-dom jsdom msw
pnpm dlx shadcn@latest init     # solo si se adopta shadcn
```

---

## 8. Decisiones que no conviene reabrir

- El access token vive en RAM (zustand), nunca en `localStorage`. La persistencia
  la da la cookie HttpOnly + silent refresh.
- La instancia de axios con su interceptor es única y es la que usa todo. No se
  reescribe con `fetch` ni se crea una segunda para el portal del socio: se
  parametriza por principal (§5).
- El front no decide permisos. Esconde por rol, el backend responde 403.
- El escritorio no es consumidor del API.
- `CLIENTE` no es un rol de staff, en el backend ni en los tipos del front.

## 9. Abierto

- **shadcn/ui sí o no.** Recomendado, decisión del dueño. Afecta el paso 3 en
  adelante, no antes.
- **Tauri vs Electron.** Recomendado Tauri; lo único que no es negociable es
  cargar la URL, no empaquetar el SPA.
- **Prettier.** Hoy hay `oxlint` solo. Si el formateo empieza a ensuciar diffs,
  se agrega; no antes.
