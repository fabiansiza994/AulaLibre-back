# BACKEND_API_IMPLEMENTATION_REPORT.md

Resultado de convertir la arquitectura base (`BACKEND_ARCHITECTURE.md`) en una API
funcional según `BACKEND_API_CONTRACT.md`. El detalle endpoint-por-endpoint y el
antes/después de cada uno están en `BACKEND_CONTRACT_IMPLEMENTATION_PLAN.md`; este
documento resume decisiones, reglas de negocio, y lo que queda pendiente.

## 1. Endpoints implementados

Blocks (5), Rooms (5), Schedules (3, sin `PUT` — no está en el contrato), Availability
(4), RoomRequests (8: `my`, `my/counts`, crear, cancelar, listar-admin, aprobar,
rechazar + la reutilización de la lista-admin para el dashboard), Auth (3: `POST
/auth/login`, `GET /auth/me`, `POST /auth/logout` — agregados en la fase de seguridad,
ver `SECURITY_IMPLEMENTATION_REPORT.md`). 28 endpoints en total, todos bajo `/api/v1`.
Detalle completo en la matriz del plan.

## 2. Endpoints pendientes

- `GET /room-requests/{id}` (detalle suelto) — existía en la base pero no está en el
  contrato (las pantallas actuales ya tienen los datos desde la lista); se retiró en
  vez de mantenerlo sin uso real.

## 3. Decisiones y diferencias respecto al contrato

Todas siguen la regla de prioridad del encargo (reglas de negocio > seguridad futura >
consistencia REST > compatibilidad con frontend > simplicidad). Ninguna cambia
`BACKEND_API_CONTRACT.md`.

1. **Enums en español, dominio en inglés.** El contrato exige `"pendiente"`, `"Aula"`,
   `"profesor"`, etc. literalmente. En vez de renombrar las constantes Java (acoplando
   el modelo de dominio a un idioma de UI), cada enum lleva una etiqueta española vía
   `@JsonValue`/`@JsonCreator`, y se persiste con `EnumType.STRING` sobre el nombre en
   inglés. Los query params usan el mismo mapeo a través de `Converter<String,X>`
   (`EnumConverterConfig`), así que no hay dos lugares con la traducción.
2. **`RoomType` recortado a 3 valores** (`CLASSROOM/LABORATORY/MEETING_ROOM`). La
   arquitectura base traía también `AUDITORIUM`/`OTHER`, especulativos: el frontend no
   tiene forma de crearlos ni de renderizarlos (`ROOM_TYPES` es una lista fija de 3 en
   el cliente). Mantenerlos habría sido el tipo de sobreingeniería que el encargo pide
   evitar.
3. **`professorId`/`reviewerId` fuera de los DTOs.** El contrato es explícito: deben
   derivarse del usuario autenticado, nunca aceptarse del body (un cliente podría
   falsificar una solicitud a nombre de otro profesor). Se agregó `CurrentUserProvider`
   con una implementación temporal (`DevCurrentUserProvider`) que lee el id desde el
   header `X-Demo-User-Id` — nunca del body — y se documenta como código a borrar en
   cuanto exista JWT.
4. **`DELETE /rooms/{id}` no borra las `RoomRequest` en cascada.** El contrato cascadea
   silenciosamente todo (igual que el mock actual); se decidió sí cascadear los
   `Schedule` (como pide el contrato) pero **rechazar** el borrado con `409` si el
   salón tiene cualquier `RoomRequest` asociada, porque son un registro de auditoría de
   negocio (quién pidió qué, aprobado/rechazado por quién). El propio contrato deja esta
   asimetría abierta como "decidir si es intencional o un descuido" (§13.8); se priorizó
   la integridad del historial sobre replicar el borrado silencioso del mock.
5. **El `409` de bloques con `{"message": "..."}` no generó un formato de error
   paralelo.** El `GlobalExceptionHandler` existente ya devuelve un `message` como parte
   de `ApiErrorResponse` — cualquier cliente que lea `response.data.message` sigue
   funcionando; los campos extra (`timestamp`, `status`, `code`, `path`) son aditivos.
6. **`GET /rooms` sin query params.** La arquitectura base traía un filtro `?blockId=`;
   el contrato es explícito ("ninguno hoy"), así que se retiró del controller. El método
   de repositorio (`findByBlockId`) se conserva porque `GET /rooms/availability` sí lo
   necesita.
7. **Nombres de campo (`start`/`end`, `subject`, `note`, `reviewNote`) adoptados
   literalmente del contrato**, incluida la inconsistencia intencional del propio
   contrato (`GET /rooms/availability` usa `startTime`/`endTime` a nivel raíz pero
   `start`/`end` en los objetos anidados) — se reprodujo tal cual, no se intentó
   "corregir" una decisión que el contrato ya marcó como consciente.

## 4. Reglas de negocio implementadas

- **Disponibilidad calculada, nunca persistida.** `RoomAvailabilityService` (módulo
  `availability`) es el único lugar que combina `Schedule` (por día de semana) +
  `RoomRequest APPROVED` (por fecha) para responder disponibilidad puntual, por rango,
  y el timeline del día. La regla de solapamiento (`newStart < existingEnd && newEnd >
  existingStart`) vive en un solo sitio: `shared/util/TimeRangeUtil`.
- **Corrección respecto al frontend actual** (contrato §6.1): la búsqueda evalúa el
  **rango completo** `[start, end)`, no solo el instante inicial como hace hoy
  `RoomsSearchPage.jsx`.
- **Solapamiento de horarios recurrentes** (`ScheduleService#create`): rechaza con `409
  SCHEDULE_CONFLICT` un bloque que se solape con otro del mismo salón/día.
- **Doble validación de disponibilidad en solicitudes**: al crear (`POST
  /room-requests`) y al aprobar (`PATCH .../approve`) se vuelve a calcular
  disponibilidad contra el estado actual de la base — nunca se confía en que el
  frontend haya mostrado el salón como libre.
- **Transiciones de estado válidas**: crear siempre nace `PENDING`; solo se puede
  `cancel` una `PENDING` (y solo su dueño); solo se puede `approve`/`reject` una
  `PENDING`; cualquier otra transición devuelve `409 INVALID_REQUEST_STATE`.
- **Concurrencia en la aprobación**: lock pesimista de fila sobre `Room` (ver
  `BACKEND_ARCHITECTURE.md` §10) para que dos aprobaciones concurrentes sobre el mismo
  salón no puedan ambas tener éxito.

## 5. Migraciones agregadas

- `V2__contract_alignment.sql`: renombra `schedules.subject_or_activity → subject`,
  `room_requests.observation → note`, `room_requests.rejection_reason → review_note`.
  No se tocó `V1` (ya se considera aplicada).

## 6. Tests agregados

Prioridad puesta en las reglas de negocio, no en cobertura trivial:

- `ScheduleServiceTest`: rango horario inválido, solapamiento (`409`), creación válida.
- `RoomAvailabilityServiceTest`: reproduce el ejemplo de timeline del propio contrato
  (§6.4) más los casos de `isRangeFree`/rango disponible-ocupado/estado puntual.
- `RoomRequestServiceTest`: solicitud sobre salón ocupado (`409`), rol no autorizado
  (`403`), aprobación con conflicto revalidado, aprobación exitosa, transición inválida,
  cancelación por no-dueño (`403`) y sobre solicitud ya aprobada (`409`), rechazo con
  `reviewNote`.
- `RoomRequestSpecificationsTest` (`@DataJpaTest` contra H2 real): filtros por estado,
  por salón, por rango de fechas, búsqueda por nombre de salón y por la etiqueta en
  español del motivo (no por el nombre de la constante), y paginación.
- `JsonContractShapeTest`: fija los detalles de serialización más fáciles de romper sin
  darse cuenta — etiquetas en español, `LocalTime` sin segundos, `LocalDateTime` sin
  fracción de segundo, `RoomResponse` plano (sin `block` anidado, sin `active`).
- Se mantienen los tests previos (`BlockMapperTest`, `GlobalExceptionHandlerTest`,
  `AppApplicationTests`).

## 7. Pendiente para JWT/Spring Security

- Reemplazar `DevCurrentUserProvider` por una implementación que lea el
  `SecurityContext` — ningún `Service` cambia, todos dependen de `CurrentUserProvider`.
- Implementar `POST /auth/login`, `GET /auth/me`, `POST /auth/logout` (contrato §2).
- Agregar `@PreAuthorize`/filtros de rol reales; hoy la validación de rol es manual
  dentro de cada `Service` (documentado en `BACKEND_ARCHITECTURE.md` §9).
- Una vez exista sesión real, el frontend deja de enviar cualquier identificador de
  usuario y el backend dejaría de necesitar el header `X-Demo-User-Id`.

## 8. Build

`./mvnw clean verify` — verde. Java 21, Spring Boot 4.1.1 intactos. Todos los tests
(unitarios, `@DataJpaTest`, `@SpringBootTest` de contexto) pasan.
