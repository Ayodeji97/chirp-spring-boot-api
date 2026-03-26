# Section 6.4 - Logging In Users

## What This Section Teaches

This section wires up the **login API endpoint** in the controller and adds exception handlers for invalid credentials and missing users. After this, clients can call `POST /api/auth/login` to authenticate and receive JWT tokens.

## What Changed

### `AuthController.kt` — Added Login Endpoint

```kotlin
@PostMapping("/login")
fun login(
    @RequestBody body: LoginRequest
): AuthenticatedUserDto {
    return authService.login(
        email = body.email,
        password = body.password
    ).toAuthenticatedUserDto()
}
```

**Key concepts:**

- **No `@Valid`** — Unlike `register()`, the login endpoint doesn't use `@Valid` because `LoginRequest` has no validation annotations. The authentication logic in `AuthService.login()` handles credential checking directly.

- **Returns `AuthenticatedUserDto`** — On successful login, the response includes the user info plus both JWT tokens (access + refresh). The `toAuthenticatedUserDto()` mapper converts the domain `AuthenticatedUser` to the API DTO.

- **Flow:** `POST /api/auth/login` → deserialize `LoginRequest` → `AuthService.login()` → returns `AuthenticatedUser` → `.toAuthenticatedUserDto()` → JSON response

### `AuthExceptionHandler.kt` — Added Two Handlers

**`UserNotFoundException` → 404 NOT_FOUND:**

```kotlin
@ExceptionHandler(UserNotFoundException::class)
@ResponseStatus(HttpStatus.NOT_FOUND)
fun onUserNotFound(e: UserNotFoundException) = mapOf(
    "code" to "USER_NOT_FOUND",
    "message" to e.message
)
```

**`InvalidCredentialsException` → 401 UNAUTHORIZED:**

```kotlin
@ExceptionHandler(InvalidCredentialsException::class)
@ResponseStatus(HttpStatus.UNAUTHORIZED)
fun onInvalidCredentials(e: InvalidCredentialsException) = mapOf(
    "code" to "INVALID_CREDENTIALS",
    "message" to e.message
)
```

## Complete Exception Handler Summary

| Exception | HTTP Status | Code | When |
|---|---|---|---|
| `UserNotFoundException` | 404 | `USER_NOT_FOUND` | User ID doesn't exist |
| `InvalidCredentialsException` | 401 | `INVALID_CREDENTIALS` | Wrong email or password |
| `InvalidTokenException` | 401 | `INVALID_TOKEN` | JWT token is invalid/expired |
| `UserAlreadyExistsException` | 409 | `USER_EXISTS` | Email or username taken |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` | Request body fails validation |

## Testing the Login Endpoint

**First register a user:**
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@test.com","username":"testuser","password":"Password1!"}'
```

**Then login:**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@test.com","password":"Password1!"}'
```

**Successful response (200):**
```json
{
  "user": {
    "id": "...uuid...",
    "email": "user@test.com",
    "username": "testuser",
    "hasVerifiedEmail": false
  },
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "eyJhbGciOi..."
}
```

**Wrong password (401):**
```json
{"code": "INVALID_CREDENTIALS", "message": "The entered credentials aren't valid"}
```

## Auth Endpoints So Far

| Method | Path | Body | Response | Auth Required? |
|---|---|---|---|---|
| POST | `/api/auth/register` | `RegisterRequest` | `UserDto` | No |
| POST | `/api/auth/login` | `LoginRequest` | `AuthenticatedUserDto` | No |
