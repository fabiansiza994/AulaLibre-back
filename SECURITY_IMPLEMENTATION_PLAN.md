# SECURITY_IMPLEMENTATION_PLAN.md

Plan para agregar Spring Security + JWT sobre el backend actual (verde, funcional).
No se reescribe arquitectura, DTOs ni reglas de negocio existentes.

## 1. Lo que ya existe (punto de partida)

- `User` (sin password), `UserRole` (`STUDENT/PROFESSOR/ADMIN`, serializado en español vía
  `@JsonValue`/`@JsonCreator`, persistido como `STRING` en inglés).
- `CurrentUserProvider` (interfaz) + `DevCurrentUserProvider` (lee `X-Demo-User-Id`).
- `GlobalExceptionHandler` + `ApiErrorResponse` + `BusinessException` (con subtipos
  `ResourceNotFoundException` 404, `ConflictException` 409, `ValidationException` 400,
  `ForbiddenOperationException` 403).
- `RoomRequestService` ya valida rol manualmente (`requireRole`) y ya valida ownership en
  `cancel()` (`professor.id == currentUser.id`).
- Controllers sin ninguna anotación de seguridad — todo es público hoy.
- Migraciones `V1`, `V2` ya aplicadas; no se tocan.

## 2. Conflicto detectado con `BACKEND_API_CONTRACT.md` y resolución

El contrato (§2.1) documenta un login **sin contraseña** (`{ "role": "profesor" }`) y dice
explícitamente: *"Reglas de negocio: ninguna (login demo, sin contraseña) — ver
inconsistencia #2, esto debe rediseñarse antes de producción real."* Esta tarea es
exactamente ese rediseño. Por eso:

- **Request de login**: se implementa `{ email, password }` (real), no `{ role }` — un login
  sin contraseña no puede satisfacer el objetivo de esta fase (autenticación real). Es una
  evolución explícitamente anticipada por el propio contrato, no un capricho.
- **Response de login**: se sigue el contrato **al pie de la letra** donde sí es viable —
  `{ token, user: { id, name, role, initials, email } }`, exactamente la forma de §2.1 —
  en vez del ejemplo `{ accessToken, tokenType, expiresIn, user: {...} }} ` sugerido en el
  encargo de esta tarea, porque el propio encargo indica seguir el contrato cuando define
  una forma exacta. `expiresIn` no desaparece: sigue viviendo como claim `exp` dentro del
  JWT, solo no se repite como campo del body.
- `GET /auth/me` responde el mismo objeto `user` (sin envolver en `{token, user}`), tal como
  dice §2.2 ("igual forma que `user` en 2.1").

## 3. Archivos a crear

```
shared/security/
  JwtProperties.java                 (@ConfigurationProperties: secret, expirationMinutes)
  JwtTokenService.java                (emitir / parsear+validar JWT, vía jjwt)
  JwtAuthenticationFilter.java        (OncePerRequestFilter)
  JwtAuthenticationEntryPoint.java    (401 con ApiErrorResponse)
  JwtAccessDeniedHandler.java         (403 con ApiErrorResponse)
  SecurityConfig.java                 (SecurityFilterChain, PasswordEncoder, CORS)

shared/exception/
  AuthenticationFailedException.java  (401 — credenciales inválidas)

user/infrastructure/web/
  SpringSecurityCurrentUserProvider.java  (reemplaza a DevCurrentUserProvider)

auth/
  application/dto/request/LoginRequest.java
  application/dto/response/AuthResponse.java
  application/dto/response/AuthUserResponse.java
  application/service/AuthService.java
  infrastructure/web/AuthController.java

src/main/resources/db/migration/V3__add_user_password.sql

src/test/java/.../shared/security/... (tests de JWT/roles/ownership, ver §12)
```

## 4. Archivos a modificar

- `pom.xml`: `spring-boot-starter-security`, `jjwt-api/impl/jackson` (0.13.0),
  `spring-security-test` (test).
- `User.java`: campo `passwordHash` (nunca expuesto en un DTO).
- `ErrorCode.java`: `INVALID_CREDENTIALS`.
- `DevDataSeeder.java`: emails `student@aulalibre.edu` / `professor@aulalibre.edu` /
  `admin@aulalibre.edu`, contraseñas `Student123*` / `Professor123*` / `Admin123*`
  hasheadas con el `PasswordEncoder` inyectado (nunca en texto plano).
- `BlockController`, `RoomController`, `ScheduleController`, `RoomRequestController`:
  `@PreAuthorize` por endpoint (ver matriz §6).
- `RoomRequestService`: se retira `requireRole(...)` en `create`/`approve`/`reject` — pasa a
  ser responsabilidad de `@PreAuthorize` en el controller (regla del encargo §19). Se
  **mantiene intacto** el chequeo de ownership en `cancel()`.
- `OpenApiConfig.java`: esquema Bearer JWT global, con `/auth/login` exceptuado.
- `application.properties` (+ `-dev`, test): `aulalibre.jwt.secret`,
  `aulalibre.jwt.expiration-minutes`, `aulalibre.cors.allowed-origins`.
- `user/infrastructure/web/DevCurrentUserProvider.java`: **eliminado** (no queda como
  fallback de ningún profile — el encargo prefiere borrarlo una vez JWT funciona; dejarlo
  activo junto al nuevo provider dejaría dos beans `CurrentUserProvider` compitiendo).

## 5. Estrategia JWT

- Librería: `io.jsonwebtoken:jjwt` 0.13.0 (api/impl/jackson) — madura, sin criptografía manual.
- Firma: HMAC-SHA256 (`Jwts.builder().signWith(key)`), clave desde `aulalibre.jwt.secret`
  (`${JWT_SECRET}`, con un valor de desarrollo solo como fallback local, nunca para prod).
- `subject` = `user.getId()` (como string). Se prefiere el id sobre el email: es estable
  incluso si el email cambiara en el futuro, y evita tener que normalizar mayúsculas/espacios
  en cada verificación.
- Claim adicional: `role` (informativo). **La autorización real nunca confía en este claim**:
  el filtro vuelve a leer el `User` desde la base en cada request y construye las
  `GrantedAuthority` a partir del rol *actual* en BD — así un cambio de rol o una
  desactivación surten efecto de inmediato, no solo cuando expire el token viejo.
- Expiración: `aulalibre.jwt.expiration-minutes` (`${JWT_ACCESS_TOKEN_EXPIRATION_MINUTES:60}`).
- Sin refresh token (fuera de alcance explícito).

## 6. Endpoints públicos vs protegidos y matriz de permisos

| Endpoint | Público | STUDENT | PROFESSOR | ADMIN |
|---|---|---|---|---|
| `POST /auth/login` | ✔ | — | — | — |
| `GET /auth/me`, `POST /auth/logout` | | ✔ | ✔ | ✔ |
| `/swagger-ui/**`, `/v3/api-docs/**` | ✔ | — | — | — |
| `GET /blocks`, `GET /blocks/{id}` | | ✔ | ✔ | ✔ |
| `POST/PUT/PATCH/DELETE /blocks/**` | | ✘ | ✘ | ✔ |
| `GET /rooms`, `GET /rooms/{id}` | | ✔ | ✔ | ✔ |
| `POST/PUT/DELETE /rooms/**` | | ✘ | ✘ | ✔ |
| `GET /rooms/{roomId}/schedules` | | ✔ | ✔ | ✔ |
| `POST/DELETE .../schedules/**` | | ✘ | ✘ | ✔ |
| `GET /rooms/availability`, `/rooms/status`, `/rooms/{id}/status`, `/rooms/{id}/timeline` | | ✔ | ✔ | ✔ |
| `POST /room-requests` | | ✘ | ✔ | ✘ |
| `GET /room-requests/my`, `/my/counts` | | ✘ | ✔ | ✘ |
| `PATCH /room-requests/{id}/cancel` | | ✘ | ✔ (solo dueño, en servicio) | ✘ |
| `GET /room-requests` (admin) | | ✘ | ✘ | ✔ |
| `PATCH /room-requests/{id}/approve`, `/reject` | | ✘ | ✘ | ✔ |

Todo lo no listado como público requiere `authenticated()` como mínimo; los roles de
escritura se aplican con `@PreAuthorize("hasRole('ADMIN')")` /
`@PreAuthorize("hasRole('PROFESSOR')")` en cada método de controller — no hay una matriz
paralela de `antMatchers` en `SecurityConfig` para evitar mantener el permiso en dos sitios.

## 7. Logout

Stateless: el backend no guarda sesión ni token emitido. `POST /auth/logout` responde `204`
sin hacer nada server-side — la sesión termina cuando el frontend borra el token. Un JWT
emitido sigue siendo válido hasta su expiración natural aunque el usuario haga logout; no se
agrega blacklist ni Redis (fuera de alcance).

## 8. `CurrentUserProvider`

`SpringSecurityCurrentUserProvider` lee `SecurityContextHolder.getContext().getAuthentication()`,
toma el `principal` (el `userId` que el filtro puso ahí) y llama a
`UserService#findEntityById` — mismo patrón que ya usaba `DevCurrentUserProvider`, así que
`RoomRequestService` no cambia ninguna línea más allá de quitar los `requireRole(...)` que
ahora son responsabilidad de `@PreAuthorize`.

## 9. 401 vs 403

- **401** (`JwtAuthenticationEntryPoint`): no hay token, token inválido/expirado/manipulado,
  o el usuario del token ya no existe/está inactivo. Mensaje genérico, sin diferenciar causa.
- **403** (`JwtAccessDeniedHandler` a nivel de Security; `ForbiddenOperationException` a
  nivel de negocio/ownership dentro de un `Service`): usuario autenticado pero sin el rol o
  la propiedad requerida.
- Ambos se serializan con la misma clase `ApiErrorResponse` que ya usa `GlobalExceptionHandler`
  — no se inventa un formato de error paralelo.

## 10. Usuarios demo DEV

Reutiliza `DevDataSeeder` (ya gateado por `aulalibre.seed-demo-data=true`, perfil `dev`).
Se actualizan sus 3 usuarios a los emails pedidos, con password hasheada vía el
`PasswordEncoder` inyectado — nunca en texto plano ni hardcodeada en `AuthService`.

## 11. Tests a agregar

- `AuthServiceTest`/`AuthControllerTest`: login correcto por rol, password incorrecta,
  usuario inexistente, usuario inactivo (los tres últimos → 401 genérico), `/auth/me`.
- Filtro JWT: token válido, expirado, con firma inválida (manipulado), request sin token a
  endpoint protegido.
- Autorización: STUDENT en `GET /rooms` (200) vs `POST /blocks` (403); PROFESSOR creando
  `RoomRequest` (201) vs aprobando una (403); ADMIN aprobando (200).
- Ownership: profesor A no puede cancelar una solicitud del profesor B (403).

## 12. No se implementa en esta fase

Refresh tokens, forgot/reset password, verificación de email, MFA, OAuth/Keycloak/LDAP/SSO,
blacklist de tokens (Redis u otro), registro público, cambio de contraseña.
