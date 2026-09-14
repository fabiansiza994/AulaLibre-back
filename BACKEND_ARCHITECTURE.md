# AulaLibre — Arquitectura del Backend

Base package: `com.alulalibre.app.aulalibre` (dentro del proyecto Maven existente
`com.alulalibre:app`, Java 21, Spring Boot 4.1.1).

Este documento describe la arquitectura **tal como quedó después de implementar
`BACKEND_API_CONTRACT.md`**. El detalle de qué se hizo endpoint por endpoint,
qué conflictos hubo entre la arquitectura base y el contrato, y cómo se
resolvieron está en `BACKEND_CONTRACT_IMPLEMENTATION_PLAN.md` y
`BACKEND_API_IMPLEMENTATION_REPORT.md` — este archivo no repite esa historia,
solo el estado actual.

## 1. Arquitectura elegida

**Monolito modular.** Un único desplegable, sin capas globales
(`controller/`, `service/`, `repository/` para toda la app). Cada
funcionalidad de negocio es un módulo independiente bajo
`aulalibre/<modulo>/`, y dentro de cada módulo:

```
<modulo>/
├── domain/           modelo JPA, enums de dominio, repositorios
│   ├── model/
│   ├── enums/
│   └── repository/
├── application/       casos de uso: DTOs, mappers, servicios
│   ├── dto/
│   │   ├── request/
│   │   └── response/
│   ├── mapper/
│   └── service/
└── infrastructure/
    └── web/           controladores REST
```

No se crean capas vacías solo por convención (p. ej. `availability` no tiene
`domain/` porque la disponibilidad no es una entidad — se calcula, nunca se
persiste; `user` no tiene un controller de negocio propio porque no hay auth
todavía, solo `CurrentUserProvider` bajo `infrastructure/web`).

## 2. Módulos

| Módulo         | Responsabilidad                                                          |
|----------------|----------------------------------------------------------------------------|
| `shared`       | Excepciones, respuesta de error, config transversal (converters, OpenAPI) |
| `user`         | Entidad `User`, su rol, y `CurrentUserProvider` (ver sección 9)          |
| `block`        | Bloques/edificios de la universidad                                      |
| `room`         | Salones, cada uno perteneciente a un bloque                              |
| `schedule`     | Horarios académicos recurrentes de un salón                              |
| `availability` | Cálculo de disponibilidad (sin entidad propia) a partir de Schedule + RoomRequest |
| `roomrequest`  | Solicitudes temporales de un profesor para usar un salón                 |

## 3. Entidades y relaciones

```
Block
  1
  |
  N
Room
  1
  |
  N
Schedule

User (PROFESSOR)
  1
  |
  N
RoomRequest
  N
  |
  1
Room

RoomRequest
  N
  |
  0..1
User (ADMIN reviewedBy)
```

- `Block` (tabla `blocks`): `id, code (único), name, description, active` + auditoría.
- `Room` (tabla `rooms`): `id, name, block (N:1), floor, capacity, type, active` + auditoría.
  **No tiene** un booleano de disponibilidad — eso nunca se persiste (ver módulo `availability`).
  `active` no se expone en los DTOs del contrato (el frontend no lo usa para salones), pero se
  conserva en la entidad por si una fase futura lo necesita.
- `Schedule` (tabla `schedules`): `id, room (N:1), dayOfWeek, startTime, endTime, subject, active` + auditoría.
- `RoomRequest` (tabla `room_requests`): `id, professor (N:1 User), room (N:1 Room), date, startTime, endTime,
  reason, note, status, reviewedBy (N:1 User, nullable, no expuesto), reviewedAt (no expuesto), reviewNote` + auditoría.
- `User` (tabla `app_users`): `id, firstName, lastName, email (único), role, active` + auditoría.

Todas las entidades extienden `shared.domain.BaseEntity` (`createdAt`/`updatedAt`,
truncados a segundos para que el JSON coincida exactamente con el formato del
contrato, vía `@PrePersist`/`@PreUpdate`). Relaciones `@ManyToOne` son
`FetchType.LAZY`; no se usan relaciones bidireccionales ni `cascade = ALL`.

## 4. Enums: dominio en inglés, JSON en español

`BACKEND_API_CONTRACT.md` exige que `status`/`type`/`reason`/`role` viajen en
los strings en español que ya usa el frontend hoy (`"pendiente"`, `"Aula"`,
`"Tutoría"`, `"profesor"`). En vez de nombrar las constantes Java en español
(acoplando el dominio a un idioma de presentación), cada enum lleva una
etiqueta y expone `@JsonValue`/`@JsonCreator`; la persistencia (`EnumType.STRING`)
sigue usando el nombre de la constante en inglés:

- `UserRole`: `STUDENT→"estudiante", PROFESSOR→"profesor", ADMIN→"administrador"`
- `RoomType`: `CLASSROOM→"Aula", LABORATORY→"Laboratorio", MEETING_ROOM→"Sala de reuniones"`
  (recortado a los 3 valores reales del contrato — antes incluía `AUDITORIUM`/`OTHER`, especulativos)
- `RoomRequestStatus`: `PENDING→"pendiente", APPROVED→"aprobada", REJECTED→"rechazada", CANCELLED→"cancelada"`
- `RoomRequestReason`: `TUTORING→"Tutoría", ACADEMIC_ADVISORY→"Asesoría", ACADEMIC_MEETING→"Reunión académica",
  EXTRACURRICULAR_ACTIVITY→"Actividad extracurricular", OTHER→"Otro"`
- `AvailabilityStatus` (`room.domain.enums`, **nunca persistido**): `AVAILABLE→"disponible", OCCUPIED→"ocupado"`

Los query params (`?type=Aula`, `?status=pendiente`) pasan por el
`ConversionService` de Spring, no por Jackson, así que `shared/config/EnumConverterConfig`
registra un `Converter<String, X>` por enum reutilizando el mismo `fromLabel(...)`
— una sola fuente de verdad para la etiqueta, tanto en el body como en la query string.

`DayOfWeek` (JDK, no se le pueden agregar anotaciones) usa
`schedule/application/mapper/ScheduleDayMapper` para el mismo mapeo hacia las
claves sin tilde del contrato (`lunes`...`sabado`, con `domingo` también válido
aunque la UI de administración no lo ofrezca).

Los DTOs con campos `LocalTime` llevan `@JsonFormat(shape = STRING, pattern = "HH:mm")`
explícito: el serializador por defecto de Jackson 3 escribe siempre los
segundos (`"16:00:00"`), y el contrato exige `"HH:mm"`.

## 5. Manejo global de errores

`shared/exception/GlobalExceptionHandler` (`@RestControllerAdvice`) centraliza
todas las traducciones excepción → respuesta HTTP. Ningún stacktrace llega
al cliente.

Jerarquía de excepciones de negocio, todas heredan de `BusinessException`
(que ya lleva `ErrorCode` + `HttpStatus`):

- `ResourceNotFoundException` → 404
- `ConflictException` → 409
- `ValidationException` → 400 (reglas de negocio que Bean Validation no puede expresar)
- `ForbiddenOperationException` → 403 (rol incorrecto, o no ser dueño del recurso)

`ErrorCode` centraliza los códigos (`BLOCK_NOT_FOUND`, `ROOM_NOT_FOUND`,
`SCHEDULE_CONFLICT`, `ROOM_NOT_AVAILABLE`, `BLOCK_HAS_ROOMS`, `ROOM_HAS_REQUESTS`,
`INVALID_TIME_RANGE`, `INVALID_REQUEST_STATE`, `FORBIDDEN_OPERATION`, etc.)
para evitar strings mágicos.

También se manejan: `MethodArgumentNotValidException`,
`ConstraintViolationException`, `HttpMessageNotReadableException` y un
fallback genérico (`Exception` → 500, mensaje genérico).

## 6. Formato de error (`ApiErrorResponse`)

```json
{
  "timestamp": "2026-08-30T15:45:00",
  "status": 404,
  "error": "NOT_FOUND",
  "code": "ROOM_NOT_FOUND",
  "message": "El salón solicitado no existe",
  "path": "/api/v1/rooms/123"
}
```

El `message` de este objeto es el mismo campo que `BACKEND_API_CONTRACT.md`
espera en los `409` de negocio (p. ej. borrar un bloque con salones) — no se
construyó un formato de error paralelo solo para esos casos.

Errores de validación añaden `fieldErrors`:

```json
{
  "status": 400,
  "error": "VALIDATION_ERROR",
  "code": "INVALID_REQUEST",
  "fieldErrors": [{ "field": "name", "message": "El nombre es obligatorio" }]
}
```

Las respuestas exitosas **no** se envuelven artificialmente
(`{ success, data }`); se devuelven DTOs REST convencionales, salvo la
paginación (`PageResponse<T>`, ver sección 8).

## 7. Estrategia de persistencia

- PostgreSQL como base de datos objetivo, configurado vía variables de entorno
  (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`) — sin credenciales hardcodeadas.
- H2 (modo `PostgreSQL`) se mantiene para tests.
- `spring.jpa.hibernate.ddl-auto=validate`: el esquema **siempre** lo crea Flyway.
- Nombres de tabla evitan palabras reservadas (`app_users` en vez de `user`).

## 8. Endpoints implementados

Ver la matriz completa en `BACKEND_CONTRACT_IMPLEMENTATION_PLAN.md`. Resumen por módulo:

- **Blocks**: `GET /blocks`, `GET/POST/PUT /blocks/{id}`, `PATCH /blocks/{id}/toggle-active`,
  `DELETE /blocks/{id}` (409 si tiene salones asignados).
- **Rooms**: `GET /rooms`, `GET/POST/PUT /rooms/{id}`, `DELETE /rooms/{id}` (cascada de
  horarios; 409 si tiene solicitudes asociadas).
- **Schedules**: `GET/POST /rooms/{roomId}/schedules`, `DELETE /schedules/{id}` — sin `PUT`
  (el contrato no edita horarios, solo los crea/borra). `POST` rechaza solapamientos con
  otro bloque del mismo salón/día (`409 SCHEDULE_CONFLICT`).
- **Availability** (`RoomAvailabilityService`, sin entidad, sin caché — siempre recalculado):
  `GET /rooms/availability`, `GET /rooms/status`, `GET /rooms/{id}/status`,
  `GET /rooms/{id}/timeline`. Único lugar donde vive la regla de solapamiento
  (`shared/util/TimeRangeUtil`) para toda la app.
- **RoomRequests**: `GET /room-requests/my` (paginado, filtros vía `Specification`),
  `GET /room-requests/my/counts`, `POST /room-requests` (valida disponibilidad, 409 si
  ocupado), `PATCH /room-requests/{id}/cancel`, `GET /room-requests` (admin, con
  `status`/`sort`/`limit`/`dateFrom`), `PATCH /room-requests/{id}/approve` (relock +
  revalida disponibilidad), `PATCH /room-requests/{id}/reject`.
- **Admin/Student/Professor Dashboard**: sin endpoints propios — se resuelven componiendo
  los anteriores, tal como pide el contrato.
- **Auth**: `POST /auth/login`, `GET /auth/me`, `POST /auth/logout` — implementados en la
  fase de seguridad, ver `SECURITY_IMPLEMENTATION_REPORT.md`. `GET /room-requests/{id}`
  (detalle suelto) fue retirado del controller: el contrato no lo pide y las pantallas
  actuales ya tienen el dato desde la lista.

## 9. Autenticación, autorización y `CurrentUserProvider`

Implementado en la fase de seguridad — detalle completo en `SECURITY_IMPLEMENTATION_PLAN.md`
y `SECURITY_IMPLEMENTATION_REPORT.md`. Resumen:

- Spring Security + JWT propio (HMAC-SHA256, vía `jjwt`), stateless, sin sesión HTTP.
- Autorización por rol vía `@PreAuthorize` en cada controller — nunca duplicada como una
  segunda lista de reglas por patrón de URL en `SecurityConfig`.

```
CurrentUserProvider (user.application.service)
    ↓ getCurrentUser()
SpringSecurityCurrentUserProvider (user.infrastructure.web)
    lee SecurityContextHolder → principal (userId) → UserService#findEntityById
```

Ningún endpoint acepta `professorId`/`reviewerId` en el body — eso permitiría que cualquier
cliente falsificara una solicitud a nombre de otro profesor; ese dato siempre se deriva de
`CurrentUserProvider`. `DevCurrentUserProvider` (leía el header `X-Demo-User-Id`) **se
eliminó** una vez confirmado que JWT funciona — no quedaron dos mecanismos de identificación
del usuario actual compitiendo. Los servicios (`RoomRequestService`) no cambiaron su forma de
usar `CurrentUserProvider`; solo se retiraron los chequeos de rol que ahora hace
`@PreAuthorize` (el chequeo de **ownership** en `cancel()` — dueño de la solicitud — se
mantuvo en el `Service`, porque no es algo que un rol pueda expresar). Endpoints que dependen
de esto: `POST /room-requests`, `PATCH /room-requests/{id}/cancel`,
`PATCH /room-requests/{id}/approve`, `PATCH /room-requests/{id}/reject`,
`GET /room-requests/my(/counts)`.

## 10. Concurrencia en la aprobación de solicitudes

`RoomRequestService#approve` toma un **lock pesimista de fila** sobre el
`Room` (`SELECT ... FOR UPDATE`, vía `RoomRepository#findByIdForUpdate`) antes
de releer la solicitud y revalidar disponibilidad. Dos aprobaciones
concurrentes sobre solicitudes distintas del mismo salón se serializan en esa
fila: la segunda, al desbloquear, vuelve a evaluar disponibilidad y ve la
aprobación que acaba de confirmar la primera, así que resulta en `409
ROOM_NOT_AVAILABLE` en vez de aprobar dos solicitudes solapadas. No hay
locking distribuido (Redis, etc.) — no hace falta para una sola instancia de
PostgreSQL.

## 11. Migraciones

- `V1__init_schema.sql`: esquema base (`app_users, blocks, rooms, schedules, room_requests`).
- `V2__contract_alignment.sql`: renombra columnas para que coincidan con los nombres del
  contrato (`schedules.subject_or_activity → subject`, `room_requests.observation → note`,
  `room_requests.rejection_reason → review_note`).
- `V3__add_user_password.sql`: agrega `app_users.password_hash` (nullable — ver comentario en
  la propia migración).

## 12. Datos de desarrollo

`shared/config/DevDataSeeder` siembra un dataset mínimo (2 bloques, 6 salones,
3 usuarios demo con password BCrypt) **solo** si `aulalibre.seed-demo-data=true` (perfil
`dev`). Deshabilitado por defecto. Credenciales demo en `SECURITY_IMPLEMENTATION_REPORT.md` §8.

## 13. Qué falta implementar

- Refresh tokens, revocación/blacklist de JWT, recuperación/cambio de contraseña, MFA,
  OAuth/Keycloak/LDAP/SSO — todos explícitamente fuera de alcance de la fase de seguridad
  (ver `SECURITY_IMPLEMENTATION_REPORT.md` §12).
- Endpoint de dashboard agregado (`GET /admin/dashboard/summary`) — el
  contrato lo deja explícitamente fuera hasta que sea un problema real de
  rendimiento.
