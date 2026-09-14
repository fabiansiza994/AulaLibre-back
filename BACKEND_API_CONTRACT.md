# BACKEND_API_CONTRACT.md

Contrato de API derivado **exclusivamente** de lo que el frontend de AulaLibre usa hoy (componentes, páginas, `DataContext`, `utils/availability.js`, `utils/time.js` y los mocks en `src/data/*.js`). No se documentan funcionalidades que el frontend no ejercita. Donde el frontend hace algo que no escala (descargar todo y calcular en el cliente), se marca explícitamente y se propone el endpoint de negocio equivalente — pero siempre como reemplazo de un cálculo que el frontend YA hace, nunca como una feature nueva.

Prefijo de rutas: `/api/v1`. Formato: JSON. Autenticación: Bearer token (a definir; ver módulo Authentication).

---

## 1. Convenciones generales

- **IDs**: el frontend nunca hace aritmética con IDs de dominio (`roomId`, `professorId`, `blockId`, `requestId`, `scheduleId`); siempre los trata como strings opacos (los usa en URLs, como key de React, como valor de `<option value>`). El backend puede usar enteros o UUIDs — deben serializarse como string o number, el frontend no distingue.
- **Fechas**: `date` siempre `"YYYY-MM-DD"` (comparación lexicográfica se usa literalmente en el frontend para ordenar y filtrar por rango — ver `MyRequests.jsx`). **No enviar con hora ni timezone.**
- **Horas**: `"HH:mm"` en 24h (`"08:00"`, `"16:30"`), nunca con segundos. Se comparan como strings y también se convierten a minutos (`toMinutes`) — deben tener siempre 2 dígitos en hora y minuto.
- **Timestamps** (`createdAt`): ISO 8601 sin timezone, ej. `"2026-08-29T09:15:00"`, tal como están en el mock actual.
- **Estados HTTP usados en todo el documento**: `200` OK, `201` Created, `204` No Content, `400` Validación, `401` No autenticado, `403` Rol no autorizado, `404` No encontrado, `409` Conflicto de negocio, `422` Entidad válida pero regla de negocio no satisfecha (se usa donde el mock actual devuelve `false`/bloquea la acción en vez de tirar excepción, ej. borrar un bloque con salones).
- Los valores de `status`, `type`, `reason`, `role` que aparecen en los JSON de este documento son **literalmente los strings en español que usa el frontend hoy** (`"disponible"`, `"pendiente"`, `"Aula"`, `"profesor"`, etc.), porque son las claves que usan directamente `StatusBadge`, los `<Select>` y las comparaciones en `DataContext`/`utils`. Ver la sección de inconsistencias — es una decisión consciente, no un descuido.

---

## 2. Módulo: Authentication / Users

### Estado actual en el frontend
`Login.jsx` **no tiene formulario de credenciales**: son 3 botones ("Entrar como Estudiante/Profesor/Administrador") que llaman a `login(role)`. `AuthContext` guarda solo el `role` en `localStorage` y deriva el objeto `user` de un diccionario estático `DEMO_USERS` (`src/data/users.js`) con exactamente 3 usuarios fijos (`est-1`, `prof-1`, `admin-1`). No existe pantalla de registro, recuperación de contraseña, ni gestión de usuarios en ningún lugar del frontend. `OTHER_PROFESSORS` (`prof-2`) solo existe como dato de referencia dentro de los mocks de solicitudes, nunca se lista ni se autentica.

Por eso este módulo documenta el **mínimo necesario para sostener el comportamiento actual** (saber qué usuario/rol está activo en cada pantalla), no un sistema de auth completo — inventar login por email/password, roles múltiples, registro, etc. sería añadir funcionalidad que el frontend no tiene.

### 2.1 `POST /api/v1/auth/login`
- **Rol autorizado:** público (sin sesión previa).
- **Query params:** ninguno.
- **Request body:**
```json
{ "role": "profesor" }
```
- **Response 200 (exacto):**
```json
{
  "token": "eyJhbGciOi...",
  "user": {
    "id": "prof-1",
    "name": "Juan Carlos Pérez",
    "role": "profesor",
    "initials": "JP",
    "email": "juan.perez@aulalibre.edu"
  }
}
```
- **Estados HTTP:** `200` login ok · `400` `role` inválido o ausente.
- **Validaciones:** `role` ∈ `{"estudiante","profesor","administrador"}`.
- **Reglas de negocio:** ninguna (login demo, sin contraseña) — **ver inconsistencia #2**, esto debe rediseñarse antes de producción real.
- **Reemplaza mock:** `DEMO_USERS[role]` en `src/data/users.js` + `login()` de `AuthContext.jsx`.
- **Consumido por:** `pages/Login.jsx`.

### 2.2 `GET /api/v1/auth/me`
- **Rol autorizado:** cualquier usuario autenticado.
- **Query params:** ninguno.
- **Request body:** ninguno.
- **Response 200 (exacto):** igual forma que `user` en 2.1.
- **Estados HTTP:** `200` · `401` sin token / token expirado.
- **Reglas de negocio:** permite rehidratar la sesión al recargar la página (hoy `AuthContext` lee el rol de `localStorage` y reconstruye `user` desde el diccionario estático; con backend real debe validar el token en vez de confiar en `localStorage`).
- **Reemplaza mock:** `loadFromStorage("auth", null)` + lookup en `DEMO_USERS` dentro de `AuthContext.jsx`.
- **Consumido por:** `AppLayout` (indirectamente, vía `AuthContext` al montar la app), `Sidebar.jsx`, `Topbar.jsx`.

### 2.3 `POST /api/v1/auth/logout`
- **Rol autorizado:** cualquier usuario autenticado.
- **Request/Response body:** vacío.
- **Estados HTTP:** `204`.
- **Reemplaza mock:** `logout()` en `AuthContext.jsx` (hoy solo borra `localStorage`).
- **Consumido por:** botón "Cambiar de perfil" en `Sidebar.jsx`.

> **No se documentan** endpoints de registro, listado de usuarios o edición de perfil: el frontend no tiene ninguna pantalla que los use.

---

## 3. Módulo: Blocks

Modelo consumido por el frontend (`src/data/blocks.js`): `{ id, code, name, description, active }`.

### 3.1 `GET /api/v1/blocks` — CRUD
- **Rol autorizado:** todos los roles autenticados (se usa también para filtrar salones en búsqueda, no solo en admin).
- **Query params:** ninguno (el frontend nunca pagina bloques — son pocos).
- **Response 200 (exacto):**
```json
[
  { "id": "b1", "code": "B1", "name": "Bloque 1", "description": "Edificio principal, pisos 1 y 2.", "active": true },
  { "id": "b2", "code": "B2", "name": "Bloque 2", "description": "Edificio de laboratorios y aulas, pisos 1 y 2.", "active": true }
]
```
- **Estados HTTP:** `200`.
- **Reemplaza mock:** `BLOCKS` en `src/data/blocks.js`.
- **Consumido por:** `BlocksManagement.jsx` (tabla admin), `RoomFormModal.jsx` (select de bloque), `SearchFilters.jsx` (filtro de bloque en búsqueda de salones), `RoomDetailPage.jsx` (mostrar nombre del bloque), `RoomsManagement.jsx` (columna Bloque).

> Nota: el número de salones por bloque (`roomCount`, usado en la columna "Salones" de `BlocksManagement.jsx`) **no requiere un campo extra en la respuesta** — hoy se calcula en el cliente contando `rooms.filter(r => r.blockId === block.id)`, y eso sigue siendo válido una vez `GET /rooms` esté disponible.

### 3.2 `POST /api/v1/blocks` — CRUD
- **Rol autorizado:** `administrador`.
- **Request body:**
```json
{ "code": "B4", "name": "Bloque 4", "description": "Edificio de posgrados." }
```
- **Response 201 (exacto):**
```json
{ "id": "b4-x7k2p1", "code": "B4", "name": "Bloque 4", "description": "Edificio de posgrados.", "active": true }
```
- **Estados HTTP:** `201` · `400` faltan `code`/`name` · `409` `code` duplicado.
- **Validaciones:** `code` y `name` requeridos y no vacíos (`BlockFormModal.jsx` ya valida esto en cliente); `code` se normaliza a mayúsculas antes de enviarse.
- **Reglas de negocio:** un bloque nuevo siempre nace `active: true`.
- **Reemplaza mock:** `addBlockEntry()` en `DataContext.jsx`.
- **Consumido por:** `BlockFormModal.jsx` vía `BlocksManagement.jsx`.

### 3.3 `PUT /api/v1/blocks/{id}` — CRUD
- **Rol autorizado:** `administrador`.
- **Request body:** igual que 3.2 (`code`, `name`, `description`).
- **Response 200:** el bloque actualizado, misma forma que 3.1.
- **Estados HTTP:** `200` · `400` validación · `404` no existe.
- **Reemplaza mock:** `updateBlockEntry()`.
- **Consumido por:** `BlockFormModal.jsx` (modo edición).

### 3.4 `PATCH /api/v1/blocks/{id}/toggle-active` — negocio (no es un simple PUT de campo, es una acción con nombre propio en la UI: botón "Activar/Desactivar")
- **Rol autorizado:** `administrador`.
- **Request body:** vacío (toggle, no recibe el nuevo valor — el backend invierte el actual).
- **Response 200:** `{ "id": "b3", "active": false }` (o el bloque completo).
- **Estados HTTP:** `200` · `404`.
- **Reemplaza mock:** `toggleBlockActive()`.
- **Consumido por:** botón con icono `Power` en `BlocksManagement.jsx`.

### 3.5 `DELETE /api/v1/blocks/{id}` — CRUD con regla de negocio
- **Rol autorizado:** `administrador`.
- **Response 204** si se elimina.
- **Estados HTTP:** `204` · `409` si el bloque tiene salones asignados.
- **Response 409 (exacto, para el mensaje que ya muestra el frontend):**
```json
{ "message": "No se puede eliminar B3: todavía tiene salones asignados." }
```
- **Reglas de negocio:** un bloque con `rooms.blockId === id` **no puede eliminarse** (`deleteBlockEntry()` hoy verifica esto en el cliente contra el array local de `rooms`; en el backend debe ser una constraint real). El frontend ya construye el mensaje de error con el `code` del bloque — si el backend devuelve un `message` distinto, `BlocksManagement.jsx` deberá actualizarse para usarlo (hoy compone el string localmente).
- **Consumido por:** botón `Trash2` en `BlocksManagement.jsx` (deshabilitado en el cliente si `roomCount > 0`, pero el backend debe validar igual por si acaso).

---

## 4. Módulo: Rooms

Modelo consumido por el frontend (`src/data/rooms.js`): `{ id, name, blockId, type, floor, capacity }`. `type` ∈ `ROOM_TYPES = ["Aula", "Laboratorio", "Sala de reuniones"]`.

### 4.1 `GET /api/v1/rooms` — CRUD
- **Rol autorizado:** todos los roles autenticados.
- **Query params:** ninguno hoy (el frontend siempre trae el catálogo completo y filtra/calcula disponibilidad en el cliente — ver módulo Availability para el reemplazo correcto de ese patrón).
- **Response 200 (exacto):**
```json
[
  { "id": "salon-203", "name": "Salón 203", "blockId": "b2", "type": "Aula", "floor": 2, "capacity": 26 },
  { "id": "lab-1", "name": "Laboratorio 1", "blockId": "b1", "type": "Laboratorio", "floor": 2, "capacity": 24 }
]
```
- **Estados HTTP:** `200`.
- **Reemplaza mock:** `ROOMS` en `src/data/rooms.js`.
- **Consumido por:** `RoomsManagement.jsx`, `SchedulesManagement.jsx` (select), `SchedulesBrowserPage.jsx` (select), `RoomFormModal.jsx` (no lo lee, pero `addRoom`/`updateRoom` lo mutan), y — de forma indirecta y no escalable hoy — `RoomsSearchPage.jsx` y `DashboardPage.jsx`, que deberían migrar a `GET /rooms/availability` / `GET /rooms/status` (módulo 6).

### 4.2 `GET /api/v1/rooms/{id}` — CRUD
- **Rol autorizado:** todos los roles autenticados.
- **Response 200:** un objeto con la misma forma que un elemento de 4.1.
- **Estados HTTP:** `200` · `404` salón no existe (hoy `RoomDetailPage.jsx` maneja esto localmente mostrando `EmptyState` "Salón no encontrado" cuando `rooms.find(...)` no encuentra nada — con este endpoint ese `404` dispara la misma pantalla).
- **Reemplaza mock:** patrón `rooms.find(r => r.id === id)` dentro de `RoomDetailPage.jsx` sobre el array completo ya cargado.
- **Consumido por:** `RoomDetailPage.jsx`.

### 4.3 `POST /api/v1/rooms` — CRUD
- **Rol autorizado:** `administrador`.
- **Request body:**
```json
{ "name": "Salón 401", "blockId": "b3", "type": "Aula", "floor": 4, "capacity": 30 }
```
- **Response 201:** el salón creado, misma forma que 4.1 con `id` generado.
- **Estados HTTP:** `201` · `400` validación · `404` `blockId` no existe.
- **Validaciones (ya presentes en `RoomFormModal.jsx`, deben repetirse en backend):** `name` no vacío; `blockId` requerido y debe existir; `floor > 0`; `capacity > 0`; `type` ∈ `ROOM_TYPES`.
- **Reemplaza mock:** `addRoom()` en `DataContext.jsx`.
- **Consumido por:** `RoomFormModal.jsx` (modo creación) vía `RoomsManagement.jsx`.

### 4.4 `PUT /api/v1/rooms/{id}` — CRUD
- **Rol autorizado:** `administrador`.
- **Request body:** igual forma que 4.3.
- **Response 200:** el salón actualizado.
- **Estados HTTP:** `200` · `400` · `404`.
- **Reemplaza mock:** `updateRoom()`.
- **Consumido por:** `RoomFormModal.jsx` (modo edición).

### 4.5 `DELETE /api/v1/rooms/{id}` — CRUD con efecto en cascada
- **Rol autorizado:** `administrador`.
- **Response 204.**
- **Estados HTTP:** `204` · `404`.
- **Reglas de negocio:** al eliminar un salón, sus horarios recurrentes (`schedules` con ese `roomId`) deben eliminarse también — hoy `deleteRoom()` en `DataContext.jsx` hace `setSchedules(prev => prev.filter(s => s.roomId !== id))` explícitamente. El backend debe reproducir ese borrado en cascada (o rechazarlo si hay solicitudes/horarios activos — el frontend actual **no** pregunta ni bloquea, borra directo, ver inconsistencia #7).
- **Consumido por:** botón `Trash2` + `ConfirmDialog` en `RoomsManagement.jsx`.

---

## 5. Módulo: Schedules

Bloques horarios **recurrentes semanales** (clases fijas), no confundir con las solicitudes puntuales de salón (módulo 7). Modelo (`src/data/schedules.js`): `{ id, roomId, day, start, end, subject }`. `day` ∈ `DAY_KEYS = ["domingo","lunes","martes","miercoles","jueves","viernes","sabado"]` (sin tildes); la UI de creación (`SchedulesManagement.jsx`) solo ofrece `WEEK_DAY_KEYS` (lunes–sábado, **sin domingo**).

### 5.1 `GET /api/v1/rooms/{roomId}/schedules` — CRUD
- **Rol autorizado:** todos los roles autenticados.
- **Response 200 (exacto):**
```json
[
  { "id": "sch-1", "roomId": "salon-101", "day": "lunes", "start": "08:00", "end": "10:00", "subject": "Programación I" },
  { "id": "sch-2", "roomId": "salon-101", "day": "lunes", "start": "14:00", "end": "16:00", "subject": "Cálculo" }
]
```
- **Estados HTTP:** `200`.
- **Reemplaza mock:** `SCHEDULES.filter(s => s.roomId === roomId)` — hoy el frontend descarga **todo** `SCHEDULES` (todos los salones) vía `DataContext` y filtra en cliente en 3 lugares distintos (`RoomDetailPage.jsx`, `SchedulesBrowserPage.jsx`, `SchedulesManagement.jsx`). Con este endpoint por salón ya no hace falta traer el horario completo de la universidad para ver uno solo.
- **Consumido por:** `ScheduleTable.jsx` (vista semanal), `RoomDetailPage.jsx`, `SchedulesBrowserPage.jsx`, `SchedulesManagement.jsx`.

### 5.2 `POST /api/v1/rooms/{roomId}/schedules` — CRUD
- **Rol autorizado:** `administrador`.
- **Request body:**
```json
{ "day": "martes", "start": "10:00", "end": "12:00", "subject": "Programación II" }
```
- **Response 201:** el bloque creado, misma forma que 5.1.
- **Estados HTTP:** `201` · `400` validación · `409` se solapa con otro bloque recurrente del mismo salón/día.
- **Validaciones (ya en `SchedulesManagement.jsx`):** `day`, `start`, `end`, `subject` requeridos; `start < end`.
- **Reglas de negocio recomendada (no aplicada hoy en el frontend):** el frontend **no valida solapamiento** entre bloques recurrentes del mismo salón/día antes de guardarlos (`handleAdd` en `SchedulesManagement.jsx` solo revisa campos vacíos y `start < end`). Se recomienda que el backend rechace (`409`) un bloque que se solape con otro ya existente para el mismo `roomId`+`day`, ya que `utils/availability.js` asume que los bloques de un mismo día no se pisan al construir la línea de tiempo.
- **Reemplaza mock:** `addScheduleBlock()`.
- **Consumido por:** formulario en `SchedulesManagement.jsx`.

### 5.3 `DELETE /api/v1/schedules/{id}` — CRUD
- **Rol autorizado:** `administrador`.
- **Response 204.**
- **Estados HTTP:** `204` · `404`.
- **Reemplaza mock:** `removeScheduleBlock()`.
- **Consumido por:** botón `X` en `ScheduleTable.jsx` (modo `editable`) dentro de `SchedulesManagement.jsx`.

---

## 6. Módulo: Availability (negocio — NO son endpoints CRUD)

Todo este módulo reemplaza `src/utils/availability.js`, que hoy calcula disponibilidad **100% en el cliente** combinando `schedules` (recurrentes) + `requests` con `status === "aprobada"` para una fecha/hora dada. Esa lógica solo funciona porque el frontend ya descargó *todas* las solicitudes y *todos* los horarios de *todos* los salones — exactamente el antipatrón que no debe repetirse en el backend real. Estos 4 endpoints son los que el frontend necesita para dejar de hacer ese cálculo local.

### 6.1 `GET /api/v1/rooms/availability` — negocio (búsqueda por rango)
- **Rol autorizado:** todos los roles autenticados.
- **Query params:** `date` (requerido), `startTime`, `endTime` (requeridos — el rango completo, no solo el punto de inicio), `blockId`, `roomType`, `minCapacity` (todos opcionales).
- **Response 200 (exacto):**
```json
{
  "date": "2026-09-03",
  "startTime": "16:00",
  "endTime": "18:00",
  "rooms": [
    {
      "id": "salon-201",
      "name": "Salón 201",
      "block": { "id": "b2", "code": "B2", "name": "Bloque 2" },
      "floor": 2,
      "capacity": 20,
      "type": "Aula",
      "availabilityStatus": "disponible",
      "availableUntil": "20:00"
    },
    {
      "id": "salon-203",
      "name": "Salón 203",
      "block": { "id": "b2", "code": "B2", "name": "Bloque 2" },
      "floor": 2,
      "capacity": 26,
      "type": "Aula",
      "availabilityStatus": "ocupado",
      "availableUntil": "18:00"
    }
  ]
}
```
- **Estados HTTP:** `200` (lista vacía si nada coincide con los filtros, **no** `404`) · `400` `date`/`startTime`/`endTime` faltantes o `startTime >= endTime`.
- **Validaciones:** `date` formato válido; `startTime < endTime`; `minCapacity >= 0`; `roomType` ∈ `ROOM_TYPES`; `blockId` debe existir si se envía.
- **Reglas de negocio:**
  - Un salón es `"ocupado"` si su horario recurrente para el día de la semana de `date`, **o** alguna solicitud `"aprobada"` para esa `date`, se **solapa** con el rango `[startTime, endTime)` (`rangesOverlap` en `utils/time.js`).
  - `availableUntil`: si `"disponible"`, hora en que empieza el próximo bloque ocupado ese día (o el fin de jornada `22:00` si no hay ninguno); si `"ocupado"`, hora en que termina el bloque que se solapa con el rango buscado.
  - **Corrección respecto al frontend actual:** hoy `RoomsSearchPage.jsx` solo evalúa el estado **en el instante `start`** (`getStatusAt(room.id, date, appliedFilters.start, ...)`), ignorando `end` por completo — un salón libre a las 16:00 pero con una clase a las 16:30 aparece como "disponible" aunque el rango pedido (16:00–18:00) esté parcialmente ocupado. Este endpoint debe evaluar **el rango completo**, no un punto — es la corrección de negocio más importante de todo el documento.
- **Reemplaza mock:** el `useMemo` de `results` en `RoomsSearchPage.jsx` (que hoy filtra `rooms` en memoria y llama `getStatusAt` por cada salón).
- **Consumido por:** `RoomsSearchPage.jsx` (pantalla "Buscar salones", con sus tabs Todos/Disponibles/Ocupados aplicados sobre `rooms` de esta respuesta), y el buscador compacto de `DashboardPage.jsx` (que hoy solo redirige con querystring a `/rooms?date&start&end` — sigue haciendo eso, pero la página destino consume este endpoint en vez de calcular localmente).

### 6.2 `GET /api/v1/rooms/status` — negocio (estado puntual, todos los salones)
- **Rol autorizado:** todos los roles autenticados.
- **Query params:** `date`, `time` (ambos opcionales — si se omiten, el backend usa fecha/hora actuales del servidor, igual que `getStatusNow()`).
- **Response 200 (exacto):**
```json
[
  { "id": "salon-101", "status": "disponible", "until": "14:00" },
  { "id": "salon-102", "status": "ocupado", "until": "12:00" }
]
```
- **Estados HTTP:** `200`.
- **Reglas de negocio:** mismo cálculo que 6.1 pero puntual (un instante, no un rango) y para **todos** los salones a la vez — reemplaza el patrón `rooms.map(room => getStatusNow(room.id, ...))` que hoy se repite en 3 pantallas.
- **Reemplaza mock:** el `useMemo` de `availableNow` en `DashboardPage.jsx`, el de `statuses` en `AdminDashboard.jsx`, y el de `rows` (columna Estado) en `RoomsManagement.jsx`.
- **Consumido por:** `DashboardPage.jsx` ("Disponibles ahora", top 4), `AdminDashboard.jsx` (tiles "Disponibles ahora"/"Ocupados ahora"), `RoomsManagement.jsx` (columna Estado de la tabla de salones).

### 6.3 `GET /api/v1/rooms/{id}/status` — negocio (estado puntual, un salón)
- **Rol autorizado:** todos los roles autenticados.
- **Query params:** `date`, `time` (opcionales, igual que 6.2).
- **Response 200 (exacto):**
```json
{ "status": "disponible", "until": "18:00", "label": null, "source": null }
```
o, si está ocupado:
```json
{ "status": "ocupado", "until": "16:00", "label": "Programación I", "source": "clase" }
```
- **Estados HTTP:** `200` · `404` salón no existe.
- **Reglas de negocio:** `source` ∈ `{"clase", "solicitud"}` indica si el bloque ocupado viene de un horario recurrente o de una solicitud aprobada; `label` es el `subject` de la clase o el `reason` de la solicitud. Esto es exactamente lo que devuelve `getStatusAt()` hoy.
- **Reemplaza mock:** `getStatusNow(room.id, ...)` calculado dentro de `RoomDetailPage.jsx` para el encabezado del salón.
- **Consumido por:** `RoomDetailPage.jsx` (badge de estado junto al nombre del salón, y el texto "Libre hasta las..." / "Ocupado hasta las...").

### 6.4 `GET /api/v1/rooms/{id}/timeline` — negocio
- **Rol autorizado:** todos los roles autenticados.
- **Query params:** `date` (opcional, default hoy).
- **Response 200 (exacto):**
```json
[
  { "start": "07:00", "end": "08:00", "status": "disponible" },
  { "start": "08:00", "end": "10:00", "status": "ocupado", "label": "Programación I", "source": "clase" },
  { "start": "10:00", "end": "16:00", "status": "disponible" },
  { "start": "16:00", "end": "18:00", "status": "ocupado", "label": "Tutoría", "source": "solicitud" },
  { "start": "18:00", "end": "22:00", "status": "disponible" }
]
```
- **Estados HTTP:** `200` · `404` salón no existe.
- **Reglas de negocio:** jornada `07:00`–`22:00` (`DAY_START`/`DAY_END` en `utils/availability.js`); intercala huecos libres entre los bloques ocupados, ordenados cronológicamente.
- **Reemplaza mock:** `buildDayTimeline(room.id, todayISO(), schedules, requests)`.
- **Consumido por:** `ScheduleTimeline.jsx` dentro de `RoomDetailPage.jsx`, pestaña "Horario de hoy" (la pestaña "Vista semanal" de la misma página usa `GET /rooms/{roomId}/schedules`, módulo 5, no este endpoint).

---

## 7. Módulo: Professor Requests

Modelo (`src/data/requests.js` + estado agregado `cancelada`): `{ id, professorId, professorName, roomId, date, start, end, reason, note, status, reviewNote?, createdAt }`. `status` ∈ `{"pendiente","aprobada","rechazada","cancelada"}`. `reason` ∈ `REQUEST_REASONS = ["Tutoría","Asesoría","Reunión académica","Actividad extracurricular","Otro"]`.

> **Enriquecimiento recomendado:** a diferencia del mock (donde el frontend cruza `roomId` contra la lista completa de `rooms` ya cargada en `DataContext` para mostrar el nombre del salón), se recomienda que estos endpoints devuelvan el salón **embebido** (`room: {...}` con su `block` anidado) en cada solicitud. Es la misma razón que en el módulo 6: la vista "Mis solicitudes" está diseñada para paginar decenas/cientos de registros y no debería requerir descargar todo el catálogo de salones solo para pintar una columna. Se marca explícitamente porque es una desviación intencional del literal `roomId` que usa el mock hoy.

### 7.1 `GET /api/v1/room-requests/my` — negocio (bandeja paginada del profesor)
- **Rol autorizado:** `profesor` (siempre filtrado por el profesor autenticado — nunca recibe `professorId` como parámetro, se deriva del token).
- **Query params:** `page` (0-index), `size`, `status` (`pendiente|aprobada|rechazada|cancelada`, omitir = todas), `search` (coincide contra nombre de salón, `reason` y `note`), `dateFrom`, `dateTo`, `roomId`, `reason`, `sort` (`createdAt,desc` por defecto, o `date,desc`).
- **Request body:** ninguno.
- **Response 200 (exacto):**
```json
{
  "content": [
    {
      "id": "req-1",
      "room": {
        "id": "salon-203",
        "name": "Salón 203",
        "floor": 2,
        "block": { "id": "b2", "code": "B2", "name": "Bloque 2" }
      },
      "date": "2026-09-02",
      "start": "16:00",
      "end": "18:00",
      "reason": "Tutoría",
      "note": "",
      "status": "pendiente",
      "reviewNote": null,
      "createdAt": "2026-08-29T09:15:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 29,
  "totalPages": 3
}
```
- **Estados HTTP:** `200` (lista vacía con `totalElements: 0` si no hay coincidencias, no `404`) · `400` parámetros de paginación/fecha inválidos.
- **Validaciones:** `dateFrom <= dateTo` si ambos vienen; `status`/`reason` deben pertenecer a sus enums; `size` con tope razonable (ej. máx 100).
- **Reglas de negocio:** solo devuelve solicitudes donde `professorId === usuario autenticado`; el orden por defecto es `createdAt` descendente (más recientes primero).
- **Reemplaza mock:** el pipeline de filtros/orden/paginación construido en `src/pages/professor/MyRequests.jsx` (`filteredExceptStatus`, `counts`, `filtered`, `sorted`, `paginated`) sobre el array completo `REQUESTS` cargado por `DataContext`.
- **Consumido por:** `MyRequests.jsx`, `RequestFilters.jsx`, `RequestStatusTabs.jsx`, `MyRequestsTable.jsx`, `RequestMobileCard.jsx`.

### 7.2 `GET /api/v1/room-requests/my/counts` — negocio (conteos de los tabs, independiente de la paginación)
- **Rol autorizado:** `profesor`.
- **Query params:** los mismos filtros de 7.1 **excepto** `status`, `page`, `size`, `sort` (los conteos deben calcularse *antes* de aplicar el filtro de estado, para que los tabs reflejen cuántas hay en cada estado dentro del resto de filtros activos).
- **Response 200 (exacto):**
```json
{ "todos": 29, "pendiente": 4, "aprobada": 16, "rechazada": 6, "cancelada": 3 }
```
- **Estados HTTP:** `200`.
- **Reglas de negocio:** debe ser un cálculo agregado en el servidor (`COUNT ... GROUP BY status`), **no** requiere traer las filas — es justamente el punto de separar esta lógica del endpoint 7.1 (ver `MyRequests.jsx`, comentario "separar esta lógica para que posteriormente los conteos puedan venir del backend").
- **Reemplaza mock:** el `useMemo` de `counts` en `MyRequests.jsx`.
- **Consumido por:** `RequestStatusTabs.jsx`.

### 7.3 `POST /api/v1/room-requests` — negocio
- **Rol autorizado:** `profesor`.
- **Request body:**
```json
{
  "roomId": "salon-203",
  "date": "2026-09-02",
  "start": "16:00",
  "end": "18:00",
  "reason": "Tutoría",
  "note": ""
}
```
- **Response 201 (exacto):** el objeto de solicitud creado, misma forma que un elemento de `content` en 7.1, con `status: "pendiente"` y `createdAt` generado por el servidor.
- **Estados HTTP:** `201` · `400` validación · `404` `roomId` no existe · `409` (recomendado, ver regla de negocio abajo).
- **Validaciones (ya presentes en `RequestFormModal.jsx`):** `roomId`, `date`, `start`, `end` requeridos; `start < end`; `reason` ∈ `REQUEST_REASONS`.
- **Reglas de negocio:**
  - `professorId`/`professorName` **no deben venir en el body** — se derivan del usuario autenticado. Esto es un cambio respecto al frontend actual: `RequestFormModal.jsx` hoy arma `{ professorId: professor.id, professorName: professor.name, ... }` en el cliente y lo manda tal cual porque no hay sesión real (ver inconsistencia #5). Al conectar auth real, el frontend debe dejar de enviar esos dos campos.
  - **Recomendado, no aplicado hoy en el frontend:** validar que `[start, end)` esté realmente libre para `roomId` en `date` (contra horarios recurrentes + otras solicitudes aprobadas) antes de aceptar, devolviendo `409`. Hoy `RequestFormModal.jsx` **no** hace esta verificación (no llama a `isRangeFree`, que existe en `utils/availability.js` pero no se usa en ningún lado) — cualquier profesor puede pedir un horario ya ocupado y queda pendiente hasta que un admin lo rechace manualmente. Se documenta como recomendación porque es la causa raíz visible de por qué existen mensajes de rechazo tipo *"El salón ya tenía una clase asignada en ese horario"* en el mock.
- **Reemplaza mock:** `addRequest()` en `DataContext.jsx`.
- **Consumido por:** `RequestFormModal.jsx`, invocado desde `RoomsSearchPage.jsx`, `RoomDetailPage.jsx` y `DashboardPage.jsx` (mismo modal reutilizado en las 3 pantallas — no hay un formulario duplicado).

### 7.4 `PATCH /api/v1/room-requests/{id}/cancel` — negocio
- **Rol autorizado:** `profesor`, y solo el dueño de la solicitud.
- **Request/Response body:** vacío o la solicitud actualizada con `status: "cancelada"`.
- **Estados HTTP:** `200`/`204` · `403` si el profesor no es el dueño · `409` si la solicitud no está `"pendiente"`.
- **Reglas de negocio:** solo se puede cancelar una solicitud en estado `"pendiente"` — una `"aprobada"` o `"rechazada"` no se puede cancelar desde esta pantalla (`cancelRequest()` en `DataContext.jsx` ya aplica este guard: `r.status === "pendiente"` antes de mutar).
- **Reemplaza mock:** `cancelRequest()` en `DataContext.jsx`.
- **Consumido por:** botón "Cancelar solicitud" + `ConfirmDialog` en `RequestDetailDialog.jsx`, dentro de `MyRequests.jsx`.

### 7.5 `GET /api/v1/room-requests` — CRUD (bandeja de administración)
- **Rol autorizado:** `administrador`.
- **Query params:** `status` (`todas|pendiente|aprobada|rechazada|cancelada`, default `todas`). **No** tiene `search`/`dateFrom`/`dateTo`/paginación hoy — `RequestsInbox.jsx` solo filtra por estado sobre el array completo, sin paginar. No se documenta paginación aquí para no inventar una funcionalidad que la pantalla admin no tiene (aunque, dado el mismo problema de escala resuelto en el módulo del profesor, sería razonable extenderla igual en el futuro).
- **Response 200 (exacto):** un array plano, misma forma de solicitud que 7.1 pero incluyendo `professorId`/`professorName` (necesarios para la columna "Profesor" que no existe en la vista del profesor):
```json
[
  {
    "id": "req-1",
    "professorId": "prof-1",
    "professorName": "Juan Carlos Pérez",
    "room": { "id": "salon-203", "name": "Salón 203", "floor": 2, "block": { "id": "b2", "code": "B2", "name": "Bloque 2" } },
    "date": "2026-09-02",
    "start": "16:00",
    "end": "18:00",
    "reason": "Tutoría",
    "note": "",
    "status": "pendiente",
    "reviewNote": null,
    "createdAt": "2026-08-29T09:15:00"
  }
]
```
- **Estados HTTP:** `200`.
- **Reemplaza mock:** el `useMemo` de `rows` en `RequestsInbox.jsx` (filtra `requests` completo por `filter` y ordena por `createdAt`).
- **Consumido por:** `RequestsInbox.jsx` (vía `RequestTable.jsx`).

### 7.6 `GET /api/v1/room-requests?sort=createdAt,desc&limit=5` y `?status=aprobada&dateFrom={hoy}&sort=date,asc&limit=5` — reutilización del mismo endpoint 7.5 con parámetros
- **Rol autorizado:** `administrador`.
- **Reglas de negocio:** el dashboard de admin necesita (a) las 5 solicitudes más recientes de cualquier estado, y (b) las 5 próximas solicitudes ya aprobadas cuya fecha sea hoy o futura, ordenadas por fecha de uso ascendente. Ambos son el mismo recurso 7.5 con `sort`/`limit`/`status`/`dateFrom` distintos — **no requieren un endpoint nuevo**, solo que 7.5 soporte esos query params en vez de forzar al frontend a traer todo y recortar en memoria como hace hoy `AdminDashboard.jsx` (`[...requests].sort(...).slice(0,5)`).
- **Reemplaza mock:** los `useMemo` de `recentRequests` y `upcomingChanges` en `AdminDashboard.jsx`.
- **Consumido por:** `AdminDashboard.jsx` ("Solicitudes recientes" y "Próximos cambios de disponibilidad").

### 7.7 `PATCH /api/v1/room-requests/{id}/approve` — negocio
- **Rol autorizado:** `administrador`.
- **Request body:** vacío.
- **Response 200:** la solicitud con `status: "aprobada"`.
- **Estados HTTP:** `200` · `404` · `409` si ya no está `"pendiente"`.
- **Reglas de negocio:** **una solicitud aprobada pasa a bloquear la disponibilidad del salón** — es la regla central de todo el sistema: `getBlocksForDate()` en `utils/availability.js` incluye explícitamente `requests.filter(r => r.status === "aprobada")` como ocupación real del salón en los módulos 6.1–6.4. Aprobar una solicitud debe invalidar cualquier caché de disponibilidad de ese salón/fecha.
- **Reemplaza mock:** `approveRequest()` en `DataContext.jsx`.
- **Consumido por:** botón "Aprobar" en `RequestDetailModal.jsx` (modal de administración), dentro de `RequestsInbox.jsx`.

### 7.8 `PATCH /api/v1/room-requests/{id}/reject` — negocio
- **Rol autorizado:** `administrador`.
- **Request body:**
```json
{ "reviewNote": "El salón ya tenía una clase asignada en ese horario." }
```
- **Response 200:** la solicitud con `status: "rechazada"` y `reviewNote` guardado.
- **Estados HTTP:** `200` · `404` · `409` si ya no está `"pendiente"`.
- **Validaciones:** `reviewNote` es opcional (`Textarea` en `RequestDetailModal.jsx` dice explícitamente "Motivo del rechazo (opcional)").
- **Reglas de negocio:** una vez rechazada, el motivo debe quedar visible para el profesor dueño de la solicitud en su propio detalle (`RequestDetailDialog.jsx`, sección "Motivo del rechazo").
- **Reemplaza mock:** `rejectRequest()` en `DataContext.jsx`.
- **Consumido por:** botón "Rechazar" → `Textarea` → "Confirmar rechazo" en `RequestDetailModal.jsx`.

---

## 8. Módulo: Admin Dashboard

`AdminDashboard.jsx` no tiene datos propios: es una composición de llamadas a los módulos ya documentados. No se propone un endpoint de agregación nuevo porque el frontend no lo necesita hoy (los 3 bloques de la pantalla son perfectamente servibles con los endpoints existentes):

| Sección de la UI | Endpoint(s) que la alimentan |
|---|---|
| Tile "Salones totales" | `GET /api/v1/rooms` (`.length`) |
| Tile "Disponibles ahora" / "Ocupados ahora" | `GET /api/v1/rooms/status` (6.2), contando por `status` |
| Tile "Solicitudes pendientes" | `GET /api/v1/room-requests?status=pendiente` (7.5), o su `length` |
| "Solicitudes recientes" (tabla, top 5) | `GET /api/v1/room-requests?sort=createdAt,desc&limit=5` (7.6) |
| "Próximos cambios de disponibilidad" (top 5) | `GET /api/v1/room-requests?status=aprobada&dateFrom={hoy}&sort=date,asc&limit=5` (7.6) |

> Si en el futuro el número de llamadas en el primer render resulta un problema de performance, se podría evaluar un `GET /api/v1/admin/dashboard/summary` que agregue las 4 métricas en una sola respuesta — pero eso sería una optimización nueva, no algo que el frontend actual pida explícitamente, así que se deja fuera del contrato base.

---

## 9. Módulo: Student Dashboard

`role === "estudiante"` renderiza el mismo componente `DashboardPage.jsx` que el profesor (ver módulo 10), con dos diferencias puramente de UI: el texto de bienvenida ("...para estudiar o reunirte" en vez de "...para tus clases, asesorías o reuniones") y la ausencia del botón "Solicitar salón" en cada `RoomCard` (`showRequestButton={isProfessor}`, `isProfessor` es `false` para estudiante). El estudiante también tiene acceso a `RoomsSearchPage.jsx` y `RoomDetailPage.jsx` en modo solo lectura (sin botón de solicitud) y a `SchedulesBrowserPage.jsx`.

**No hay endpoints exclusivos de estudiante.** Los que usa son:
- `GET /api/v1/rooms/status` (6.2) — "Disponibles ahora".
- `GET /api/v1/rooms/availability` (6.1) — al enviar el buscador compacto, redirige a `RoomsSearchPage.jsx`.
- `GET /api/v1/rooms/{id}/status` (6.3) y `GET /api/v1/rooms/{id}/timeline` (6.4) — `RoomDetailPage.jsx`.
- `GET /api/v1/rooms/{roomId}/schedules` (5.1) — `SchedulesBrowserPage.jsx`, vista semanal en `RoomDetailPage.jsx`.
- `GET /api/v1/blocks` (3.1) — filtro de bloque en `SearchFilters.jsx`.

---

## 10. Módulo: Professor Dashboard

Mismo componente `DashboardPage.jsx` que el módulo 9, con `isProfessor = true`: agrega el botón "Solicitar salón" en cada `RoomCard` de "Disponibles ahora", que abre `RequestFormModal.jsx`. Usa **los mismos endpoints del módulo 9** más:
- `POST /api/v1/room-requests` (7.3) — al enviar el formulario de solicitud desde el dashboard.
- Además, como profesor, tiene acceso adicional a `MyRequests.jsx` (módulo 7.1/7.2/7.4) y puede solicitar salón también desde `RoomsSearchPage.jsx` y `RoomDetailPage.jsx` (mismo modal reutilizado en las 3 pantallas, no hay 3 formularios distintos).

---

## 11. BACKEND IMPLEMENTATION PRIORITY

Orden recomendado para poder ir eliminando mocks progresivamente sin dejar el frontend roto a medias:

1. **Auth mínimo** (2.1, 2.2, 2.3) — todo lo demás depende de saber qué usuario/rol está activo; sin esto no se puede ni derivar `professorId` en las solicitudes ni proteger rutas por rol.
2. **Blocks CRUD** (3.1–3.5) — catálogo pequeño, sin dependencias, y `rooms` depende de `blockId` existente.
3. **Rooms CRUD** (4.1–4.5) — depende de Blocks; una vez listo, `RoomsManagement.jsx` y los selects de salón en el resto de la app dejan de usar `ROOMS` mock.
4. **Schedules CRUD** (5.1–5.3) — depende de Rooms; permite reemplazar `SCHEDULES` mock y habilita el cálculo real de disponibilidad (paso siguiente).
5. **Availability** (6.1–6.4) — depende de Rooms + Schedules + (parcialmente) Requests aprobadas; es el módulo más importante para dejar de descargar todo y calcular en el cliente, y el que corrige el bug de "solo valida el instante `start`, no el rango".
6. **Professor Requests — lectura y creación** (7.1, 7.2, 7.3) — depende de Rooms + Availability (para la validación de solapamiento recomendada en 7.3). Con esto `MyRequests.jsx` deja de usar el mock `REQUESTS`.
7. **Professor Requests — acciones de administración** (7.5, 7.6, 7.7, 7.8) — depende de 7.3 (deben existir solicitudes reales antes de poder aprobarlas/rechazarlas); con esto `RequestsInbox.jsx` deja el mock.
8. **Professor Requests — cancelación** (7.4) — depende de 7.1/7.3, es la acción más nueva y menos crítica del flujo.
9. **Admin Dashboard** (8) — no requiere código backend adicional una vez 4, 6.2 y 7.5/7.6 existen; solo se conectan los 3 bloques de UI.
10. **Student/Professor Dashboard** (9, 10) — igual que el anterior, quedan resueltos por transitividad al completar 5, 6 y 7.

---

## 12. CURRENT FRONTEND MOCK DATA

| Mock | Archivo | Estructura actual | Endpoint que lo reemplaza |
|---|---|---|---|
| Bloques | `src/data/blocks.js` (`BLOCKS`) | `{ id, code, name, description, active }[]` | `GET /api/v1/blocks` (3.1) |
| Salones | `src/data/rooms.js` (`ROOMS`, `ROOM_TYPES`) | `{ id, name, blockId, type, floor, capacity }[]` | `GET /api/v1/rooms` (4.1) |
| Horarios recurrentes | `src/data/schedules.js` (`SCHEDULES`) | `{ id, roomId, day, start, end, subject }[]`, generados con un helper `block(...)` y contador `seq` incremental | `GET /api/v1/rooms/{roomId}/schedules` (5.1) |
| Solicitudes | `src/data/requests.js` (`REQUESTS`, `REQUEST_REASONS`) | `{ id, professorId, professorName, roomId, date, start, end, reason, note, status, reviewNote?, createdAt }[]` | `GET /api/v1/room-requests/my` (7.1) para el profesor, `GET /api/v1/room-requests` (7.5) para admin; creación/acciones vía 7.3/7.4/7.7/7.8 |
| Usuarios | `src/data/users.js` (`DEMO_USERS`, `OTHER_PROFESSORS`, `ROLE_LABELS`) | `DEMO_USERS`: diccionario fijo `{estudiante, profesor, administrador} -> {id, name, role, initials, email}`; `OTHER_PROFESSORS`: solo `{id, name}[]`, referencial | `POST /api/v1/auth/login` + `GET /api/v1/auth/me` (2.1, 2.2) — `OTHER_PROFESSORS` no tiene endpoint propio porque el frontend nunca lo consulta directamente, solo aparece denormalizado dentro de `REQUESTS.professorName` |
| Estado de disponibilidad | *(no es un archivo mock — es lógica pura en)* `src/utils/availability.js` | Funciones `getBlocksForDate`, `getStatusAt`, `getStatusNow`, `isRangeFree` (no usada), `getFreeFromTime` (no usada), `buildDayTimeline` sobre `schedules`+`requests` en memoria | `GET /api/v1/rooms/availability`, `/rooms/status`, `/rooms/{id}/status`, `/rooms/{id}/timeline` (6.1–6.4) |
| Persistencia local | `src/utils/storage.js` + `DataContext.jsx` | Todo el estado (`rooms`, `schedules`, `requests`, `blocks`) vive en memoria de React y se sincroniza a `localStorage` bajo el prefijo `aulalibre:` en cada cambio | Se elimina por completo: el backend es la única fuente de verdad; `localStorage` deja de usarse para datos de dominio (podría conservarse solo para el token de sesión) |

---

## 13. Inconsistencias de modelos/nombres a resolver antes de crear el backend

1. **Enums en español embebidos como lógica, no solo como texto.** `status` de solicitudes (`"pendiente"/"aprobada"/"rechazada"/"cancelada"`), `type` de salón (`"Aula"/"Laboratorio"/"Sala de reuniones"`), `status` de disponibilidad (`"disponible"/"ocupado"`) y `role` (`"estudiante"/"profesor"/"administrador"`) son strings en español usados directamente como claves de comparación en `StatusBadge.jsx`, `DataContext.jsx` y en cada filtro (`r.status === "pendiente"`, `f.status === tab.key"`, etc.), no solo como etiquetas visuales. **Si el backend responde con enums en inglés (`"PENDING"`, `"CLASSROOM"`, `"AVAILABLE"`) — como en los ejemplos ilustrativos de este mismo pedido — el frontend actual no renderiza nada** (`StatusBadge` devuelve `null` para una clave no mapeada) y ningún filtro coincide. Hay que decidir explícitamente: (a) el backend habla en español igual que el frontend hoy (cero cambios en el cliente), o (b) se define un enum canónico en inglés y se agrega una capa de mapeo en el frontend **antes** de conectar cualquier endpoint real. Este documento asumió la opción (a) para ser fiel al frontend actual, pero es una decisión de producto, no técnica, y debe tomarse antes de escribir el backend.
2. **No existe autenticación real.** `Login.jsx` es un selector de rol sin credenciales, respaldado por 3 usuarios hardcodeados en `data/users.js`. Todo el concepto de "usuario actual" (`AuthContext`) depende de esto. Antes de construir el backend hay que decidir el mecanismo real de login (¿SSO institucional?, ¿usuario/contraseña propio?) — no se puede inferir del frontend porque simplemente no existe todavía.
3. **`start`/`end` vs `startTime`/`endTime`.** El modelo de datos real del frontend (`requests.js`, `schedules.js`, `SearchFilters.jsx`, todos los componentes) usa uniformemente `start`/`end`. Los ejemplos ilustrativos usados en las conversaciones de diseño de este proyecto (incluida la consigna de esta misma tarea) usan `startTime`/`endTime`. Hay que fijar un nombre único antes de generar DTOs de backend para evitar que cada endpoint use una convención distinta.
4. **Validación de solapamiento inexistente en la creación de solicitudes.** `RequestFormModal.jsx` nunca verifica que el horario pedido esté libre — `isRangeFree()` y `getFreeFromTime()` existen en `utils/availability.js` pero **no se llaman desde ningún componente** (código muerto). Esto significa que hoy es posible que dos profesores pidan el mismo salón/horario y ambas queden "pendiente" simultáneamente; solo se resuelve manualmente cuando el admin aprueba una y rechaza la otra. El backend debería cerrar este hueco (ver regla recomendada en 7.3), pero es una **mejora de negocio, no una corrección de un contrato ya definido** — se documenta aquí para que quede explícito que no es un olvido de este documento sino un gap real del frontend actual.
5. **`professorId`/`professorName` viajan desde el cliente.** `RequestFormModal.jsx` arma el objeto de solicitud incluyendo `professorId: professor.id, professorName: professor.name` tomados del `user` en memoria, porque no hay sesión real que el backend pueda usar para derivarlos. En cuanto exista auth real (punto 2), el frontend debe dejar de enviar estos dos campos y el backend debe ignorarlos/rechazarlos si vienen, para no confiar en un valor que el cliente podría falsificar.
6. **Representación de `block` inconsistente entre endpoints.** En los endpoints CRUD (`GET /rooms`) el bloque es un `blockId` plano, tal como lo usa hoy el frontend (`room.blockId`, cruzado contra `GET /blocks` en el cliente). En los endpoints de negocio (`availability`, `room-requests`) este documento recomienda anidar `block: {id, code, name}` para evitar que el frontend tenga que volver a descargar el catálogo completo de bloques solo para pintar una columna. Es una decisión intencional (documentada en cada sección), pero implica que el frontend deberá manejar **dos formas distintas** de referenciar un bloque según el endpoint — vale la pena evaluar si conviene unificarlo a una sola forma antes de escribir clientes HTTP tipados.
7. **Rutas en inglés, roles en español.** Las rutas del frontend son `/student`, `/professor`, `/admin` (`ROLE_TO_PATH` en `utils/roles.js`), pero el valor interno de `role` que viaja en `AuthContext`, en `RoleRoute` y en las comparaciones de todo el código es español (`"estudiante"`, `"profesor"`, `"administrador"`). Si el backend define su propio enum de roles, hay que decidir en qué idioma/convención vive, porque hoy conviven dos.
8. **Borrado de salón sin bloqueo de negocio.** A diferencia de "Bloques" (que sí impide borrar uno con salones asignados, con un mensaje explícito), `deleteRoom()` en `DataContext.jsx` borra el salón **y en cascada sus horarios**, sin preguntar si tiene solicitudes activas o horarios vigentes. Antes del backend, decidir si esa asimetría (bloque protegido, salón no) es intencional o un descuido a corregir.
9. **`REQUEST_REASONS` es una lista plana de strings, no un catálogo con id.** El motivo "Otro" no tiene forma de llevar una explicación estructurada aparte del campo libre `note` — si el negocio necesita reportar "cuántas solicitudes son 'Otro' y por qué", hoy esa información solo vive como texto libre no clasificable.
10. **IDs opacos.** Todos los IDs de dominio (`salon-203`, `prof-1`, `req-12`, `sch-14`, `b1`) son strings kebab-case generados en el propio mock (`Date.now().toString(36)`, `slugify(...)`). El frontend nunca los trata como números. El backend puede usar el esquema de ID que prefiera (autoincremental, UUID) siempre que se sirva como string — no es necesario mantener el formato `"salon-203"`, pero si se cambia a IDs numéricos, revisar que ningún componente asuma que un ID de salón "parece" un slug (no se encontró ningún caso, pero vale la verificación al integrar).
