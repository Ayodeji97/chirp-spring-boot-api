# Section 5.4 - User Registration API Endpoint

## What This Section Teaches

This section ties everything together by creating the **REST controller** that exposes the registration endpoint, a **Spring Security configuration** to control access, and a **global exception handler** for clean error responses. After this section, you can actually call `POST /api/auth/register` to create a user.

## What Was Created

### `AuthController.kt` — REST Controller

```kotlin
@RestController
@RequestMapping("/api/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    fun register(
        @Valid @RequestBody body: RegisterRequest
    ): UserDto {
        return authService.register(
            email = body.email,
            username = body.username,
            password = body.password
        ).toUserDto()
    }
}
```

**Key concepts:**

- **`@RestController`** — Combines `@Controller` and `@ResponseBody`. Every method's return value is automatically serialized to JSON (via Jackson) and written to the HTTP response body.

- **`@RequestMapping("/api/auth")`** — Base path for all endpoints in this controller. Combined with `@PostMapping("/register")`, the full URL is `POST /api/auth/register`.

- **`@Valid`** — Triggers Jakarta Bean Validation on the `RegisterRequest`. If validation fails (e.g., invalid email format), Spring throws `MethodArgumentNotValidException` before the method body even runs.

- **`@RequestBody`** — Tells Spring to deserialize the JSON request body into a `RegisterRequest` object using Jackson.

- **Flow:** HTTP request → validate → `AuthService.register()` → domain `User` → `.toUserDto()` → JSON response

### `SecurityConfig.kt` — Spring Security Configuration

```kotlin
@Configuration
class SecurityConfig {

    @Bean
    fun filterChain(httpSecurity: HttpSecurity): SecurityFilterChain {
        return httpSecurity
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/api/auth/**")
                    .permitAll()
                    .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD)
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }
            .exceptionHandling { configurer ->
                configurer
                    .authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }
            .build()
    }
}
```

**Key concepts:**

- **`@Configuration` + `@Bean`** — Defines a Spring Security filter chain bean. This replaces the default Spring Security config (which would block all requests and show a login page).

- **`.csrf { it.disable() }`** — Disables CSRF (Cross-Site Request Forgery) protection. CSRF is designed for browser-based sessions with cookies. Since this is a stateless REST API using JWT tokens, CSRF isn't needed.

- **`.sessionManagement { SessionCreationPolicy.STATELESS }`** — Tells Spring not to create HTTP sessions. Each request must carry its own authentication (via JWT token later). No server-side session state.

- **`.requestMatchers("/api/auth/**").permitAll()`** — Auth endpoints (register, login) are publicly accessible. No authentication required.

- **`.dispatcherTypeMatchers(ERROR, FORWARD).permitAll()`** — Allows Spring to forward requests internally (e.g., to error pages) without requiring authentication. Without this, error responses could be blocked by security.

- **`.anyRequest().authenticated()`** — Every other endpoint requires authentication. Without a valid token, you'll get a 401.

- **`HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)`** — When an unauthenticated request hits a protected endpoint, return a plain 401 status instead of redirecting to a login page.

### `AuthExceptionHandler.kt` — Global Exception Handler

```kotlin
@RestControllerAdvice
class AuthExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun onUserAlreadyExists(e: UserAlreadyExistsException) = mapOf(
        "code" to "USER_EXISTS",
        "message" to e.message
    )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun onValidationException(e: MethodArgumentNotValidException): ResponseEntity<Map<String, Any>> {
        val errors = e.bindingResult.allErrors.map {
            it.defaultMessage ?: "Invalid value"
        }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(mapOf(
                "code" to "VALIDATION_ERROR",
                "errors" to errors
            ))
    }
}
```

**Key concepts:**

- **`@RestControllerAdvice`** — A global exception handler for all controllers. Any exception thrown from any controller is caught here if a matching `@ExceptionHandler` exists.

- **`UserAlreadyExistsException` → 409 CONFLICT** — When someone tries to register with an existing email/username, returns:
  ```json
  { "code": "USER_EXISTS", "message": "A user with this username or email already exists." }
  ```

- **`MethodArgumentNotValidException` → 400 BAD_REQUEST** — When `@Valid` fails (e.g., invalid email), returns:
  ```json
  { "code": "VALIDATION_ERROR", "errors": ["Must be a valid email address"] }
  ```

- **Why `@ResponseStatus` vs `ResponseEntity`?** — `@ResponseStatus` is simpler (just sets the status code). `ResponseEntity` gives full control over headers and body. The validation handler uses `ResponseEntity` because it builds a more complex response.

## What Changed in Build Files

### `app/build.gradle.kts`

```kotlin
implementation(libs.kotlin.reflect)
implementation(libs.spring.boot.starter.security)
```

- **`kotlin-reflect`** — Required by Spring for reflection-based features (like processing annotations, creating beans from Kotlin classes).
- **`spring-boot-starter-security`** — Needed in the `app` module for `SecurityConfig` to access Spring Security classes.

### `common/build.gradle.kts`

```kotlin
api(libs.kotlin.reflect)
api(libs.jackson.module.kotlin)
```

- **`jackson-module-kotlin`** — Enables Jackson to properly serialize/deserialize Kotlin data classes (handles default values, `val` properties, etc.). Without this, Jackson can't construct Kotlin objects correctly.
- Both are `api` (not `implementation`) so all modules that depend on `common` can use them.

## Testing the Endpoint

Once the app is running with the dev profile:

**Successful registration:**
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@test.com","username":"testuser","password":"Password1!"}'
```
Response (200):
```json
{"id":"...uuid...","email":"user@test.com","username":"testuser","hasVerifiedEmail":false}
```

**Duplicate user (409):**
```json
{"code":"USER_EXISTS","message":"A user with this username or email already exists."}
```

**Invalid input (400):**
```json
{"code":"VALIDATION_ERROR","errors":["Must be a valid email address"]}
```

## Package Structure

```
app/src/main/kotlin/
└── com/danzucker/chirp/
    ├── ChirpApplication.kt
    └── security/
        └── SecurityConfig.kt                   (NEW)

user/src/main/kotlin/
└── com/danzucker/chirp/
    ├── api/
    │   ├── controllers/
    │   │   └── AuthController.kt               (NEW)
    │   ├── dto/
    │   │   ├── RegisterRequest.kt
    │   │   ├── LoginRequest.kt
    │   │   ├── UserDto.kt
    │   │   └── AuthenticatedUserDto.kt
    │   ├── exception_handling/
    │   │   └── AuthExceptionHandler.kt          (NEW)
    │   └── mappers/
    │       └── UserMappers.kt
    ├── domain/
    │   ├── exception/
    │   │   └── UserAlreadyExistsException.kt
    │   └── model/
    │       ├── User.kt
    │       └── AuthenticatedUser.kt
    ├── infra/
    │   ├── database/
    │   │   ├── entities/
    │   │   │   └── UserEntity.kt
    │   │   ├── mappers/
    │   │   │   └── UserMappers.kt
    │   │   └── repositories/
    │   │       └── UserRepository.kt
    │   └── security/
    │       └── PasswordEncoder.kt
    └── service/
        └── auth/
            └── AuthService.kt
```
