# AulaLibre — Endpoints habilitados (guía de integración frontend)

Snapshot de lo que el backend expone **hoy**, tal como está implementado en el código (no
el contrato de diseño original — ver `BACKEND_API_CONTRACT.md` para el razonamiento
detrás de cada decisión, y `BACKEND_API_IMPLEMENTATION_REPORT.md` para diferencias
respecto a ese contrato). 28 endpoints en total.

## 1. Conexión

- **Base URL (dev):** `http://localhost:8080/api/v1` (`server.port`, override con `SERVER_PORT`).
- **CORS:** habilitado para `http://localhost:5173` y `http://localhost:3000` (`aulalibre.cors.allowed-origins`). Si el front corre en otro puerto/host, hay que agregarlo ahí.
- **Formato:** JSON en request y response.
- **Fechas:** `"YYYY-MM-DD"`. **Horas:** `"HH:mm"` (24h, sin segundos). **Timestamps:** ISO 8601 sin timezone (`"2026-08-29T09:15:00"`).
- **Enums por el wire:** siempre en **español**, como string plano (no como objeto). Ej.: `role: "profesor"`, `status: "pendiente"`, `type: "Aula"`. El backend los persiste en inglés internamente, pero eso es transparente — nunca vas a ver `"PROFESSOR"` ni `"PENDING"` en una respuesta.
- **IDs:** numéricos (`Long`), pero trátalos como opacos (nunca asumas formato).

## 2. Autenticación

JWT stateless vía header `Authorization: Bearer <token>`. No hay cookies de sesión, no hay refresh token (el token expira a los 60 min por defecto — `aulalibre.jwt.expiration-minutes` — y hay que loguear de nuevo).

Flujo:
1. `POST /auth/login` con `email`/`password` → devuelve `token` + datos del usuario.
2. Guardar el `token` (ej. `localStorage`) y mandarlo en cada request protegido: `Authorization: Bearer <token>`.
3. Al recargar la página, usar el token guardado contra `GET /auth/me` para rehidratar la sesión (si devuelve `401`, el token expiró o es inválido → volver a login).
4. `POST /auth/logout` es solo cosmético (no hay blacklist server-side) — basta con borrar el token del cliente.

### Usuarios demo (perfil `dev`)

| Email | Password | Rol |
|---|---|---|
| `student@aulalibre.edu` | `Student123*` | `estudiante` |
| `professor@aulalibre.edu` | `Professor123*` | `profesor` |
| `admin@aulalibre.edu` | `Admin123*` | `administrador` |

### 2.1 `POST /auth/login` — público
```json
// Request
{ "email": "professor@aulalibre.edu", "password": "Professor123*" }
```
```json
// Response 200
{
  "token": "eyJhbGciOi...",
  "user": {
    "id": 2,
    "name": "Juan Carlos Pérez",
    "role": "profesor",
    "initials": "JP",
    "email": "professor@aulalibre.edu"
  }
}
```
`400` datos inválidos/faltantes · `401` credenciales incorrectas o usuario inactivo (mensaje genérico, no distingue "email no existe" de "password incorrecta").

### 2.2 `GET /auth/me` — cualquier usuario autenticado
Response 200: mismo objeto `user` de 2.1. `401` sin token / token inválido / expirado.

### 2.3 `POST /auth/logout` — cualquier usuario autenticado
Sin body. `204 No Content`.

## 3. Roles y permisos

Rol autenticado va en el JWT; el backend lo valida por endpoint. `403` si el rol no alcanza, `401` si no hay token o es inválido.

| Rol (valor en JSON) | Puede |
|---|---|
| `estudiante` | Leer catálogo (blocks/rooms/schedules), consultar disponibilidad. |
| `profesor` | Todo lo del estudiante + crear/cancelar sus propias solicitudes de salón. |
| `administrador` | Todo + CRUD de blocks/rooms/schedules + aprobar/rechazar solicitudes de cualquier profesor. |

## 4. Formato de error (uniforme en todos los endpoints)

```json
{
  "timestamp": "2026-08-31T10:15:00",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "code": "INVALID_REQUEST",
  "message": "La solicitud contiene datos inválidos",
  "path": "/api/v1/blocks",
  "fieldErrors": [
    { "field": "code", "message": "El código es obligatorio" }
  ]
}
```
`fieldErrors` solo aparece en errores de validación (`400`). Códigos HTTP usados: `200`, `201`, `204`, `400`, `401`, `403`, `404`, `409`, `500`.

---

## 5. Blocks

Modelo: `{ id, code, name, description, active }`.

| Método | Ruta | Rol | Notas |
|---|---|---|---|
| GET | `/blocks` | autenticado | lista completa, sin paginar |
| GET | `/blocks/{id}` | autenticado | `404` si no existe |
| POST | `/blocks` | `administrador` | body: `{code, name, description}` → `201` |
| PUT | `/blocks/{id}` | `administrador` | mismo body → `200` |
| PATCH | `/blocks/{id}/toggle-active` | `administrador` | sin body, invierte `active` → `200` |
| DELETE | `/blocks/{id}` | `administrador` | `204`, o `409` si el bloque tiene salones asignados |

## 6. Rooms

Modelo CRUD (plano): `{ id, name, blockId, type, floor, capacity }`. `type` ∈ `"Aula" | "Laboratorio" | "Sala de reuniones"`.

| Método | Ruta | Rol | Notas |
|---|---|---|---|
| GET | `/rooms` | autenticado | catálogo completo, sin filtros |
| GET | `/rooms/{id}` | autenticado | `404` si no existe |
| POST | `/rooms` | `administrador` | body: `{name, blockId, floor, capacity, type}` → `201`, `404` si `blockId` no existe |
| PUT | `/rooms/{id}` | `administrador` | mismo body → `200` |
| DELETE | `/rooms/{id}` | `administrador` | cascadea horarios (`Schedule`); `409` si el salón tiene solicitudes (`RoomRequest`) asociadas — se preserva el historial |

## 7. Schedules (horarios recurrentes semanales)

Sub-recurso de Room. Modelo: `{ id, roomId, day, start, end, subject }`. `day` en español sin tildes (`"lunes"`…`"sabado"`, también acepta `"domingo"`).

| Método | Ruta | Rol | Notas |
|---|---|---|---|
| GET | `/rooms/{roomId}/schedules` | autenticado | horarios de ese salón |
| POST | `/rooms/{roomId}/schedules` | `administrador` | body: `{day, start, end, subject}` → `201`, `409 SCHEDULE_CONFLICT` si se solapa con otro bloque del mismo salón/día |
| DELETE | `/schedules/{id}` | `administrador` | `204` (no hay `PUT`, solo crear/borrar) |

## 8. Availability (calculado, nunca persistido)

Combina `Schedule` + `RoomRequest` en estado `aprobada` para responder disponibilidad real. `AvailabilityStatus` ∈ `"disponible" | "ocupado"`.

### 8.1 `GET /rooms/availability` — autenticado
Query: `date`, `startTime`, `endTime` (requeridos) + `blockId`, `roomType`, `minCapacity` (opcionales).
```json
{
  "date": "2026-09-03",
  "startTime": "16:00",
  "endTime": "18:00",
  "rooms": [
    { "id": 12, "name": "Salón 201", "block": {"id":2,"code":"B2","name":"Bloque 2"},
      "floor": 2, "capacity": 20, "type": "Aula",
      "availabilityStatus": "disponible", "availableUntil": "20:00" }
  ]
}
```
`400` si falta algún query param o `startTime >= endTime`.

### 8.2 `GET /rooms/status` — autenticado
Query opcionales: `date`, `time` (default: ahora). Estado puntual de **todos** los salones.
```json
[ { "id": 12, "status": "disponible", "until": "14:00" } ]
```

### 8.3 `GET /rooms/{id}/status` — autenticado
Mismos query opcionales, un solo salón.
```json
{ "status": "ocupado", "until": "16:00", "label": "Programación I", "source": "clase" }
```
`source` ∈ `"clase" | "solicitud"`. `404` si el salón no existe.

### 8.4 `GET /rooms/{id}/timeline` — autenticado
Query opcional: `date` (default hoy). Devuelve el día completo (07:00–22:00) partido en bloques libres/ocupados:
```json
[
  { "start": "07:00", "end": "08:00", "status": "disponible" },
  { "start": "08:00", "end": "10:00", "status": "ocupado", "label": "Programación I", "source": "clase" }
]
```

## 9. Room Requests (solicitudes de profesor)

Modelo: `{ id, professorId, professorName, room: {id,name,floor,block:{...}}, date, start, end, reason, note, status, reviewNote, createdAt }`. `status` ∈ `"pendiente"|"aprobada"|"rechazada"|"cancelada"`. `reason` ∈ `"Tutoría"|"Asesoría"|"Reunión académica"|"Actividad extracurricular"|"Otro"`.

| Método | Ruta | Rol | Notas |
|---|---|---|---|
| GET | `/room-requests/my` | `profesor` | paginado (`content/page/size/totalElements/totalPages`); query: `page`, `size`, `sort` (`createdAt,desc` default), `status`, `search`, `dateFrom`, `dateTo`, `roomId`, `reason` |
| GET | `/room-requests/my/counts` | `profesor` | `{todos, pendiente, aprobada, rechazada, cancelada}`, mismos filtros que arriba salvo `status/page/size/sort` |
| POST | `/room-requests` | `profesor` | body: `{roomId, date, start, end, reason, note}` (⚠️ **no** enviar `professorId` — se deriva del token) → `201`, `409` si el salón no está libre en ese rango |
| PATCH | `/room-requests/{id}/cancel` | `profesor`, solo el dueño | sin body → `200` con `status: "cancelada"`; `403` si no es el dueño; `409` si no está `pendiente` |
| GET | `/room-requests` | `administrador` | lista plana (no paginada); query: `status`, `dateFrom`, `sort`, `limit` — reutilizable para "recientes" (`?sort=createdAt,desc&limit=5`) y "próximas aprobadas" (`?status=aprobada&dateFrom=hoy&sort=date,asc&limit=5`) |
| PATCH | `/room-requests/{id}/approve` | `administrador` | sin body → `200`; `409` si ya no está `pendiente` o el salón dejó de estar libre |
| PATCH | `/room-requests/{id}/reject` | `administrador` | body opcional `{reviewNote}` → `200`; `409` si ya no está `pendiente` |

---

## 10. Users (gestión de cuentas — admin)

Distinto del objeto `user` de `/auth/login`/`/auth/me` (`{id,name,role,initials,email}`, para la sesión): este módulo administra cuentas — nombre separado, `active`, timestamps. Modelo: `{ id, firstName, lastName, email, role, active, createdAt, updatedAt }`. **Todo el módulo es `administrador` únicamente — incluidos los `GET`.**

| Método | Ruta | Notas |
|---|---|---|
| GET | `/users` | paginado (`content/page/size/totalElements/totalPages`, default `size=10`, `sort=createdAt,desc`); query: `page`, `size`, `sort`, `search` (nombre/apellido/nombre completo/email), `role`, `active` |
| GET | `/users/{id}` | `404` si no existe |
| POST | `/users` | body: `{firstName, lastName, email, role, password}` → `201`; `active` nace siempre `true`; `409 EMAIL_ALREADY_EXISTS` si el email ya existe |
| PUT | `/users/{id}` | body: `{firstName, lastName, email, role}` (⚠️ sin `password`/`active` — son acciones separadas) → `200`; `409 EMAIL_ALREADY_EXISTS`; `409 CANNOT_CHANGE_OWN_ROLE` si el admin autenticado intenta cambiarse su propio rol |
| PATCH | `/users/{id}/status` | body: `{active}` → `200`; `409 CANNOT_DISABLE_SELF` si el admin autenticado intenta desactivarse a sí mismo |
| PATCH | `/users/{id}/password` | body: `{password}` → `204`, sin body de respuesta (reset administrativo, no "olvidé mi contraseña" — no manda mail ni token) |

Validaciones: `firstName`/`lastName`/`email` obligatorios; `email` formato válido y único (case-insensitive); `password` (crear/reset) mínimo 8 caracteres. Un usuario con `active: false` no puede loguearse (`POST /auth/login` → `401` genérico, igual que ya documenta la sección 2). No hay `DELETE` — la gestión es 100% vía `active`.

---

## 11. Notas para el front

- El login real (email/password) reemplaza los "3 botones de rol" del mock — ya no hace falta un `DEMO_USERS` hardcodeado en el cliente, pero sí un formulario de credenciales.
- `professorId`/`professorName` **nunca** se envían al crear una solicitud; el backend los toma del JWT. Si el frontend actual los arma en el cliente (`RequestFormModal.jsx`), hay que quitarlo.
- Todas las rutas de escritura (`POST/PUT/PATCH/DELETE`) exigen `Authorization` salvo `POST /auth/login`. Un `401` en cualquier request debe redirigir a login (token vencido o ausente); un `403` significa "autenticado pero sin permiso" (no redirigir, mostrar error).
