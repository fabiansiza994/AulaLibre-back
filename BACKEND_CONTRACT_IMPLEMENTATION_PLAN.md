# BACKEND_CONTRACT_IMPLEMENTATION_PLAN.md

Comparación entre el backend actual (monolito modular, `BACKEND_ARCHITECTURE.md`) y
`BACKEND_API_CONTRACT.md`. No se modifica `BACKEND_API_CONTRACT.md`.

## 1. Resumen de brechas encontradas

1. **Enums en español vs inglés.** El contrato exige `status`/`type`/`reason`/`role` en
   español literal (`"pendiente"`, `"Aula"`, `"Tutoría"`, `"profesor"`). El backend actual
   persiste y serializa en inglés (`PENDING`, `CLASSROOM`...).
2. **Nombres de campo `start`/`end` vs `startTime`/`endTime`.** El contrato usa `start`/`end`
   en `Schedule` y `RoomRequest` (excepto el wrapper de 6.1, que usa `startTime`/`endTime` a
   nivel raíz — inconsistencia propia del contrato, se respeta literalmente).
3. **`Room` en dos formas distintas.** CRUD (`GET /rooms`) expone `blockId` plano; los
   endpoints de negocio (`availability`, `room-requests`) anidan `block: {id,code,name}`.
4. **`Schedule.subject` vs `subjectOrActivity`**, sin `PUT` (el contrato no tiene edición de
   horarios, solo alta/baja), rutas anidadas bajo `/rooms/{roomId}/schedules`.
5. **`RoomRequest.note` vs `observation`**, `reviewNote` vs `rejectionReason`, sin
   `reviewedBy`/`reviewedAt` expuestos, `professorId`/`professorName` planos (no anidados).
6. **`professorId`/`reviewerId` en el body** — el contrato exige que se deriven del usuario
   autenticado, nunca del payload.
7. **Falta el módulo `Availability`** (6.1–6.4) — el cálculo dinámico de disponibilidad no
   existe todavía.
8. **Falta paginación real** en `GET /room-requests/my` (7.1) y conteos agregados (7.2).
9. **Faltan acciones de negocio**: `toggle-active` y `DELETE` de bloques, `DELETE` de salones
   (con cascada), `DELETE` de horarios, `approve`/`reject`/`cancel` de solicitudes con
   revalidación de disponibilidad.
10. **Falta detección de solapamiento** en horarios (409 `SCHEDULE_CONFLICT`) y en
    solicitudes (409 `ROOM_NOT_AVAILABLE`), tanto al crear como al aprobar.
11. **Auth (módulo 2)** no se implementa (fuera de alcance explícito de esta tarea). Se deja
    aislado detrás de `CurrentUserProvider` (ver sección 4).

## 2. Matriz de endpoints

Estados finales usados: **IMPLEMENTED**, **PARTIAL**, **PENDING**. (Ninguno quedó en
CONFLICT sin resolver — todos los conflictos detectados se resolvieron según la sección 3.)

| Módulo | Endpoint | Estado previo | Acción | Estado final |
|---|---|---|---|---|
| Auth | `POST /auth/login` | MISSING → implementado en la fase de seguridad | Body real `{email,password}` (ver `SECURITY_IMPLEMENTATION_REPORT.md` §5 para la desviación del `{role}` del contrato) | **IMPLEMENTED** |
| Auth | `GET /auth/me` | MISSING → implementado en la fase de seguridad | Requiere JWT válido | **IMPLEMENTED** |
| Auth | `POST /auth/logout` | MISSING → implementado en la fase de seguridad | `204`, stateless | **IMPLEMENTED** |
| Blocks | `GET /blocks` | IMPLEMENTED | Ninguna | **IMPLEMENTED** |
| Blocks | `POST /blocks` | PARTIAL | Normalizar `code` a mayúsculas | **IMPLEMENTED** |
| Blocks | `PUT /blocks/{id}` | CONFLICT (pedía `active` en el body) | Quitar `active` del request | **IMPLEMENTED** |
| Blocks | `PATCH /blocks/{id}/toggle-active` | MISSING | Implementar | **IMPLEMENTED** |
| Blocks | `DELETE /blocks/{id}` | MISSING | Implementar con validación de salones asignados (409) | **IMPLEMENTED** |
| Rooms | `GET /rooms` | CONFLICT (anidaba `block`, tenía filtro `blockId` no contractual) | DTO plano `blockId`, sin query params | **IMPLEMENTED** |
| Rooms | `GET /rooms/{id}` | CONFLICT (misma forma) | Igual que arriba | **IMPLEMENTED** |
| Rooms | `POST /rooms` | PARTIAL | Ajustar DTO/tipo (enum en español, `RoomType` recortado a 3 valores) | **IMPLEMENTED** |
| Rooms | `PUT /rooms/{id}` | CONFLICT (pedía `active`) | Quitar `active` del request | **IMPLEMENTED** |
| Rooms | `DELETE /rooms/{id}` | MISSING | Implementar con cascada de horarios + bloqueo si tiene solicitudes | **IMPLEMENTED** |
| Schedules | `GET /rooms/{roomId}/schedules` | CONFLICT (ruta era `/schedules?roomId=`, campo `subjectOrActivity`) | Ruta anidada + DTO `day/start/end/subject` | **IMPLEMENTED** |
| Schedules | `POST /rooms/{roomId}/schedules` | CONFLICT (`roomId` iba en el body) | `roomId` por path, detectar solapamiento (409 `SCHEDULE_CONFLICT`) | **IMPLEMENTED** |
| Schedules | `PUT /schedules/{id}` | Existía, no está en el contrato | **Eliminado** (no hay edición de horarios en el frontend) | Removido |
| Schedules | `DELETE /schedules/{id}` | MISSING | Implementar | **IMPLEMENTED** |
| Availability | `GET /rooms/availability` | MISSING | Implementar `RoomAvailabilityService` | **IMPLEMENTED** |
| Availability | `GET /rooms/status` | MISSING | Implementar | **IMPLEMENTED** |
| Availability | `GET /rooms/{id}/status` | MISSING | Implementar | **IMPLEMENTED** |
| Availability | `GET /rooms/{id}/timeline` | MISSING | Implementar | **IMPLEMENTED** |
| RoomRequests | `GET /room-requests/my` | MISSING | Implementar con `Specification` + `Pageable` | **IMPLEMENTED** |
| RoomRequests | `GET /room-requests/my/counts` | MISSING | Implementar (agregado por estado) | **IMPLEMENTED** |
| RoomRequests | `POST /room-requests` | CONFLICT (`professorId` en body, sin validar disponibilidad) | `CurrentUserProvider` + validar disponibilidad (409 `ROOM_NOT_AVAILABLE`) | **IMPLEMENTED** |
| RoomRequests | `PATCH /room-requests/{id}/cancel` | MISSING | Implementar (solo dueño → 403, solo `PENDING` → 409) | **IMPLEMENTED** |
| RoomRequests | `GET /room-requests` (admin) | MISSING | Implementar con `status`/`sort`/`limit`/`dateFrom` | **IMPLEMENTED** |
| RoomRequests | `PATCH /room-requests/{id}/approve` | CONFLICT (era `review()` genérico con `reviewerId` en body) | Revalidar disponibilidad + lock de fila (`Room`) + `CurrentUserProvider` | **IMPLEMENTED** |
| RoomRequests | `PATCH /room-requests/{id}/reject` | CONFLICT (idem) | `CurrentUserProvider` + `reviewNote` | **IMPLEMENTED** |
| Admin Dashboard | (8) | — | Sin endpoint nuevo — se resuelve con Rooms + `/rooms/status` + `/room-requests` | **IMPLEMENTED** (por composición) |
| Student/Professor Dashboard | (9, 10) | — | Se resuelve por transitividad de Rooms/Availability/RoomRequests | **IMPLEMENTED** (por composición) |

## 3. Conflictos y resolución (regla de prioridad aplicada)

| Conflicto | Opción del contrato | Opción técnica alternativa | Decisión |
|---|---|---|---|
| Enums en español | Sí, literal | Enums en inglés (más "estándar") | **Se adoptan los valores en español como forma pública (JSON)**, pero el dominio interno y la persistencia siguen en inglés (`@JsonValue`/`@JsonCreator` en cada enum). Motivo: el contrato es explícito y consciente de la decisión (sección 13.1); traducir solo en el borde de la API evita acoplar el dominio a un idioma de presentación sin romper el frontend. |
| Error 409 de bloques con `{"message": "..."}` | Body mínimo | El `GlobalExceptionHandler` ya devuelve `{timestamp,status,error,code,message,path}` | **No se crea un sistema paralelo.** El `message` que pide el contrato ya es un campo del `ApiErrorResponse` existente; cualquier cliente que lea `response.data.message` sigue funcionando. Los campos extra son aditivos, no rompen nada. |
| `DELETE /rooms/{id}` borra en cascada **todo**, incluidas solicitudes | Cascada total, sin preguntar | Las solicitudes (`RoomRequest`) son registros de auditoría de negocio (quién pidió qué, aprobado/rechazado por quién) | **Se cascade-borran los `Schedule`** (como pide el contrato explícitamente), pero si el salón tiene **cualquier** `RoomRequest` asociada se rechaza con `409` en vez de borrarlas silenciosamente. Es la inconsistencia #8 del propio contrato, que la deja abierta ("decidir si es intencional o un descuido"); se prioriza la integridad del historial de solicitudes sobre replicar el borrado silencioso del mock. |
| `RoomType` incluye `AUDITORIUM`/`OTHER` (creados en la tarea base anterior) | `ROOM_TYPES = ["Aula","Laboratorio","Sala de reuniones"]` | Mantener los 5 valores "por si acaso" | **Se recorta el enum a los 3 valores reales** que el frontend puede crear/filtrar. Los otros dos eran especulativos y el frontend no tiene forma de generarlos ni de renderizarlos (`StatusBadge` no los reconoce) — mantenerlos sería el tipo de sobreingeniería que el encargo pide evitar. |
| `professorId`/`reviewerId` en el body | El contrato exige que se deriven del usuario autenticado | Aceptarlos igual "por ahora" | **Se elimina de los DTOs.** Se introduce `CurrentUserProvider` (interfaz) + `DevCurrentUserProvider` (implementación temporal que lee el header `X-Demo-User-Id`, nunca el body). Motivo: aceptar un ID de profesor/admin arbitrario en el body es una vulnerabilidad de suplantación real; el contrato mismo lo señala como algo a corregir en cuanto exista auth (sección 13.5). |
| `GET /rooms` no debía tener filtros | Contrato: "ninguno hoy" | El backend base ya traía `?blockId=` | **Se retira el filtro** de `RoomController`; el método de repositorio (`findByBlockId`) se conserva porque es inofensivo y reutilizable, pero no se expone por HTTP sin necesidad documentada. |

## 4. Usuario actual sin JWT (`CurrentUserProvider`)

Endpoints que dependerán del usuario autenticado cuando exista JWT:

- `POST /room-requests` (deriva `professorId`)
- `PATCH /room-requests/{id}/cancel` (verifica dueño)
- `PATCH /room-requests/{id}/approve` / `.../reject` (deriva `reviewedBy`, exige rol `ADMIN`)
- `GET /room-requests/my` y `GET /room-requests/my/counts` (deriva `professorId`)

Solución temporal: `user.application.service.CurrentUserProvider#getCurrentUser()` devuelve
la entidad `User`. Hoy lo implementa `DevCurrentUserProvider` (lee el header
`X-Demo-User-Id`, **nunca** un campo del body). El día que exista Spring Security, se agrega
`SpringSecurityCurrentUserProvider` y los services no cambian una sola línea porque dependen
de la interfaz, no de la implementación.

## 5. Roles esperados (documentados, sin `@PreAuthorize` todavía)

- **STUDENT**: lectura de blocks, rooms, availability, schedules.
- **PROFESSOR**: todo lo de STUDENT + crear solicitud, listar/cancelar las propias.
- **ADMIN**: gestión de blocks/rooms/schedules + listar/aprobar/rechazar solicitudes.

Hoy la restricción de rol se aplica manualmente dentro de cada `Service` comparando
`currentUser.getRole()` (igual que ya hacía `RoomRequestService`), no vía anotaciones de
Spring Security — eso queda para la fase de seguridad.

## 6. Concurrencia en `approve`

Se usa **bloqueo pessimista a nivel de fila** sobre el `Room` (`SELECT ... FOR UPDATE`, vía
`@Lock(PESSIMISTIC_WRITE)` en `RoomRepository`) durante la aprobación: dos `approve()`
concurrentes sobre solicitudes del mismo salón se serializan en esa fila, así que el segundo
vuelve a evaluar disponibilidad después del primero y detecta el conflicte real. No se agrega
Redis/Kafka ni locking distribuido — es innecesario para el volumen de un proyecto académico
sobre una única instancia de PostgreSQL.

## 7. Migraciones

Se agrega `V2__contract_alignment.sql`: renombra `room_requests.observation` → `note`,
`room_requests.rejection_reason` → `review_note`, `schedules.subject_or_activity` → `subject`.
No se edita `V1__init_schema.sql` (ya aplicada).

## 8. Orden de implementación

Blocks → Rooms → Schedules → Availability → RoomRequests (crear/leer → paginar/filtrar →
aprobar/rechazar/cancelar) → verificación de Dashboards por composición → tests → build.
