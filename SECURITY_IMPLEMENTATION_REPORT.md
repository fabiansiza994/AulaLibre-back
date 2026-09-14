# SECURITY_IMPLEMENTATION_REPORT.md

Resultado de agregar Spring Security + JWT sobre el backend funcional existente. El plan
previo (con el análisis y las decisiones tomadas antes de escribir código) está en
`SECURITY_IMPLEMENTATION_PLAN.md`; este documento resume lo que quedó realmente construido.

## 1. Estrategia JWT

- Librería: `io.jsonwebtoken:jjwt` 0.13.0 (`jjwt-api`/`jjwt-impl`/`jjwt-jackson`) — sin
  criptografía manual.
- Firma: HMAC-SHA256 (`Jwts.builder().signWith(key)`), clave en `aulalibre.jwt.secret`.
- `subject` = `user.getId()` (string). Claim adicional `role`, **solo informativo**: la
  autorización real nunca confía en un claim del token — `JwtAuthenticationFilter` vuelve a
  leer el `User` desde la base en cada request y arma las `GrantedAuthority` a partir del rol
  *actual*, así que un cambio de rol o una desactivación surten efecto de inmediato, sin
  esperar a que expire un token viejo.
- `JwtTokenService.extractUserId(token)` nunca lanza: cualquier fallo (firma inválida, token
  expirado, malformado, manipulado) devuelve `Optional.empty()`, y el filtro simplemente deja
  la request como anónima.

## 2. Endpoints de auth

| Endpoint | Público | Notas |
|---|---|---|
| `POST /api/v1/auth/login` | ✔ | Body `{email, password}` real — ver §5, difiere del `{role}` demo que documenta `BACKEND_API_CONTRACT.md` §2.1 |
| `GET /api/v1/auth/me` | requiere JWT | cualquier rol autenticado |
| `POST /api/v1/auth/logout` | requiere JWT | `204`, no hace nada server-side (stateless) |

## 3. Expiración y variables de entorno

| Propiedad Spring | Variable de entorno | Default (dev) |
|---|---|---|
| `aulalibre.jwt.secret` | `JWT_SECRET` | valor de desarrollo local, **no válido para producción** |
| `aulalibre.jwt.expiration-minutes` | `JWT_ACCESS_TOKEN_EXPIRATION_MINUTES` | `60` |
| `aulalibre.cors.allowed-origins` | `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` |

`JwtProperties` (`@ConfigurationProperties(prefix = "aulalibre.jwt")`) centraliza la lectura —
no hay `System.getenv()` disperso por el código.

## 4. Roles y matriz de permisos

`STUDENT`, `PROFESSOR`, `ADMIN` (sin cambios en `User`/`UserRole` — se reutiliza el modelo
existente, ninguna tabla nueva por rol). `JwtAuthenticationFilter` mapea
`UserRole.X → ROLE_X` como `GrantedAuthority`.

Matriz completa (con endpoints públicos vs protegidos) en
`SECURITY_IMPLEMENTATION_PLAN.md` §6. Resumen: lectura (`GET`) de blocks/rooms/schedules/
availability requiere solo estar autenticado, sin importar el rol; escritura de
blocks/rooms/schedules requiere `ADMIN`; crear/listar/cancelar `RoomRequest` propias requiere
`PROFESSOR`; listar-todas/aprobar/rechazar requiere `ADMIN`.

La autorización de rol vive **exclusivamente** en `@PreAuthorize` sobre cada método de
controller (`@EnableMethodSecurity`) — no hay una segunda lista de reglas por patrón de URL en
`SecurityConfig` que pueda desincronizarse de la anterior.

## 5. Diferencia deliberada con `BACKEND_API_CONTRACT.md`

El contrato (§2.1) documenta un login sin contraseña (`{ "role": "profesor" }`) y dice
explícitamente que debe rediseñarse antes de producción real (§13, inconsistencia #2). Esta
fase es ese rediseño:

- **Request**: `{ email, password }` reales — un login sin contraseña no puede satisfacer
  "autenticación real".
- **Response**: se siguió el contrato al pie de la letra donde sí era viable —
  `{ token, user: { id, name, role, initials, email } }`, igual que §2.1, en vez del
  `{ accessToken, tokenType, expiresIn, ... }` sugerido como ejemplo en el encargo de esta
  tarea (que explícitamente pedía priorizar el contrato cuando define una forma exacta).
  `expiresIn` no desaparece: vive como el claim `exp` dentro del JWT.
- `GET /auth/me` devuelve el mismo objeto `user` sin envolver, tal como pide §2.2.

## 6. `CurrentUserProvider`

```
CurrentUserProvider (interfaz, sin cambios)
    ↓ getCurrentUser()
SpringSecurityCurrentUserProvider (nuevo, único bean activo)
    lee SecurityContextHolder → principal (userId) → UserService#findEntityById
```

`DevCurrentUserProvider` (leía `X-Demo-User-Id`) **se eliminó** — no quedó ni siquiera detrás
de un profile, tal como el encargo prefería, para no tener dos mecanismos de identificación
del usuario actual compitiendo. `RoomRequestService` no cambió su forma de usar
`CurrentUserProvider`; solo se le quitaron los chequeos de rol (`requireRole(...)` en
`create`/`approve`/`reject`) porque esa responsabilidad pasó a `@PreAuthorize` en el
controller. El chequeo de **ownership** en `cancel()`
(`roomRequest.getProfessor().getId().equals(currentUser.getId())`) se dejó intacto — no es
algo que un rol pueda expresar.

## 7. 401 y 403

Ambos casos devuelven el mismo `ApiErrorResponse` que ya usaba `GlobalExceptionHandler` — no
se creó un formato de error paralelo.

- **401** (sin token, token inválido/expirado/manipulado, o usuario ya inactivo):
  `JwtAuthenticationEntryPoint`, invocado por Spring Security *antes* de que la request
  llegue al `DispatcherServlet` (por eso no pasa por `GlobalExceptionHandler`).
- **403** (`@PreAuthorize` deniega): aquí hubo un detalle no obvio — la excepción
  `AccessDeniedException` que lanza `@PreAuthorize` ocurre *dentro* de la invocación del
  método del controller (vía proxy AOP), así que Spring MVC la resuelve con
  `GlobalExceptionHandler` **antes** de que pueda llegar al
  `ExceptionTranslationFilter`/`JwtAccessDeniedHandler` de Spring Security. Sin manejarla
  explícitamente, caía en el `catch-all` de `Exception` y devolvía `500`. Se agregó
  `GlobalExceptionHandler#handleAccessDenied(AccessDeniedException)` con el mismo cuerpo 403.
  `JwtAccessDeniedHandler` se conserva igual en `SecurityConfig` (cubre una denegación
  expresada como regla de `SecurityFilterChain`, que sí ocurre antes del `DispatcherServlet`)
  — quedan dos manejadores complementarios, no uno redundante.

## 8. Usuarios demo DEV

Se cargan vía `src/main/resources/db/dev/data-dev-users.sql`
(`spring.sql.init.mode=always` + `spring.sql.init.data-locations`, solo activo con el
perfil `dev`; ver `application-dev.properties`). El script corre en cada arranque —
`ON CONFLICT (email) DO NOTHING` lo hace idempotente. Contraseñas pre-hasheadas con
`BCryptPasswordEncoder` (el mismo que usa `AuthService`) — nunca en texto plano, nunca
hardcodeadas dentro de `AuthService`.

Antes esto vivía dentro de `DevDataSeeder` (clase Java, ya eliminada): al estar atado a un
guard de `blockRepository.count() > 0`, si esa tabla ya tenía filas los usuarios demo nunca
se creaban — de ahí el bug reportado. Blocks y rooms sufrían el mismo problema y también se
movieron a SQL (`data-dev-blocks.sql`, `data-dev-rooms.sql`), así que ya no queda seeding en
Java — los tres scripts corren independientes entre sí (salvo el orden blocks→rooms por la
FK) y cada uno con su propio guard de idempotencia.

| Email | Rol | Password (dev) |
|---|---|---|
| `student@aulalibre.edu` | STUDENT | `Student123*` |
| `professor@aulalibre.edu` | PROFESSOR | `Professor123*` |
| `admin@aulalibre.edu` | ADMIN | `Admin123*` |

## 9. Estrategia de logout

Stateless: `POST /auth/logout` responde `204` sin tocar estado en el servidor. La sesión
termina cuando el cliente borra el token; un JWT ya emitido sigue siendo válido hasta su
expiración natural (no hay blacklist, ni Redis, ni tabla de tokens revocados — fuera de
alcance de esta fase, como pedía el encargo).

## 10. Migración

`V3__add_user_password.sql`: agrega `app_users.password_hash` (nullable — ver el comentario
en la propia migración: una fila creada antes de este cambio simplemente nunca podrá
autenticar con hash nulo, en vez de romper la migración contra una base de datos dev ya
sembrada). No se tocaron `V1`/`V2`.

## 11. Tests realizados

- `AuthenticationIntegrationTest` (`MockMvc`, HTTP real a través del filtro): login correcto
  (verifica la forma exacta `{token, user:{id,name,role,initials,email}}`), password
  incorrecta → 401 genérico, email inexistente → el mismo 401 genérico, usuario inactivo →
  401, `GET /auth/me` con token válido, endpoint protegido sin token → 401 con el
  `ApiErrorResponse` del proyecto, token expirado → 401, token con formato inválido → 401,
  token con firma manipulada → 401.
- `AuthorizationIntegrationTest`: STUDENT lee `/rooms` (200) vs crea un block (403); PROFESSOR
  crea una `RoomRequest` (201) vs intenta aprobar una (403); ADMIN aprueba una pendiente
  (200); profesor A no puede cancelar una solicitud del profesor B (403, ownership).
- Tests previos (`ScheduleServiceTest`, `RoomAvailabilityServiceTest`,
  `RoomRequestServiceTest`, `RoomRequestSpecificationsTest`, `JsonContractShapeTest`,
  `BlockMapperTest`, `GlobalExceptionHandlerTest`, `AppApplicationTests`) siguen pasando sin
  cambios de fondo — el único ajuste fue retirar
  `RoomRequestServiceTest#create_rejectsWhenTheCallerIsNotAProfessor`, que probaba un chequeo
  de rol que se movió deliberadamente del `Service` al `@PreAuthorize` del controller; ese
  comportamiento ahora lo cubre `AuthorizationIntegrationTest`.

## 12. Limitaciones actuales / fuera de alcance

- Sin refresh tokens: el access token dura `aulalibre.jwt.expiration-minutes` y no hay forma
  de renovarlo sin volver a hacer login.
- Sin revocación: un JWT robado sigue siendo válido hasta su expiración natural (no hay
  blacklist).
- Sin registro público, recuperación de contraseña, verificación de email ni cambio de
  contraseña — ningún endpoint para eso existe todavía.
- Sin MFA, OAuth, Keycloak, LDAP ni SSO.
- El secret de desarrollo en `application.properties` es un placeholder legible en el
  repositorio — cualquier despliegue real **debe** sobreescribir `JWT_SECRET` por variable de
  entorno; no hay ninguna validación en el arranque que lo obligue (podría agregarse en una
  fase posterior, p. ej. fallando el arranque si el perfil es `prod` y el secret coincide con
  el valor por defecto).
