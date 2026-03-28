# Section 8.3 - Password Reset API Endpoints

## What This Section Teaches

This section exposes the password reset logic as REST endpoints, introduces new DTOs with validation, creates a reusable `@Password` validation annotation, and adds the `SamePasswordException` handler.

## What Was Created

### `Password.kt` — Custom Validation Annotation

```kotlin
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [])
@Pattern(
    regexp = "^(?=.*[\\d!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?])(.{8,})$",
    message = "Password must be at least 8 characters and contain at least one digit or special character"
)
annotation class Password(...)
```

**Key concepts:**

- **Reusable annotation** — Extracts the password regex that was previously inlined in `RegisterRequest`. Now any DTO can use `@field:Password` instead of duplicating the regex.
- **`@Constraint(validatedBy = [])`** — Required by Jakarta Validation. `validatedBy = []` means the validation is delegated to the composed `@Pattern` annotation, not a custom validator class.
- **Composed annotation** — `@Password` is a meta-annotation that wraps `@Pattern`. Jakarta Validation resolves the chain automatically.

### `EmailRequest.kt` — Forgot Password DTO

```kotlin
data class EmailRequest(
    @field:Email
    val email: String
)
```

### `ResetPasswordRequest.kt` — Reset Password DTO

```kotlin
data class ResetPasswordRequest(
    @field:NotBlank
    val token: String,
    @field:Password
    val newPassword: String
)
```

### `ChangePasswordRequest.kt` — Change Password DTO

```kotlin
data class ChangePasswordRequest(
    @field:NotBlank
    val oldPassword: String,
    @field:Password
    val newPassword: String
)
```

## What Changed

### `RegisterRequest.kt` — Uses `@Password` Annotation

```kotlin
// Before:
@field:Pattern(regexp = "^(?=.*[\\d!@#$%^&*()...])(.{8,})$", message = "...")
val password: String

// After:
@field:Password
val password: String
```

Same validation, just DRYer.

### `AuthController.kt` — Three New Endpoints

```kotlin
@PostMapping("/forgot-password")
fun forgotPassword(@Valid @RequestBody body: EmailRequest) {
    passwordResetService.requestPasswordReset(body.email)
}

@PostMapping("/reset-password")
fun resetPassword(@Valid @RequestBody body: ResetPasswordRequest) {
    passwordResetService.resetPassword(token = body.token, newPassword = body.newPassword)
}

@PostMapping("/change-password")
fun changePassword(@Valid @RequestBody body: ChangePasswordRequest) {
    // TODO: Extract userId from JWT auth filter (implemented in a later section)
}
```

- **`forgot-password`** — Always returns 200 (even if email doesn't exist) to prevent user enumeration
- **`reset-password`** — Validates the token and sets the new password
- **`change-password`** — Body is a TODO — needs the JWT auth filter to extract the authenticated userId (comes in a later section)

### `AuthExceptionHandler.kt` — Added `SamePasswordException` Handler

```kotlin
@ExceptionHandler(SamePasswordException::class)
@ResponseStatus(HttpStatus.CONFLICT)
fun onSamePassword(e: SamePasswordException) = mapOf(
    "code" to "SAME_PASSWORD",
    "message" to e.message
)
```

## Complete Auth API

| Method | Path | Request | Response | Auth? |
|---|---|---|---|---|
| POST | `/api/auth/register` | `RegisterRequest` | `UserDto` | No |
| POST | `/api/auth/login` | `LoginRequest` | `AuthenticatedUserDto` | No |
| POST | `/api/auth/refresh` | `RefreshRequest` | `AuthenticatedUserDto` | No |
| POST | `/api/auth/logout` | `RefreshRequest` | _(empty)_ | No |
| GET | `/api/auth/verify` | `token` (query param) | _(empty)_ | No |
| POST | `/api/auth/forgot-password` | `EmailRequest` | _(empty)_ | No |
| POST | `/api/auth/reset-password` | `ResetPasswordRequest` | _(empty)_ | No |
| POST | `/api/auth/change-password` | `ChangePasswordRequest` | _(empty)_ | Yes (TODO) |

## Complete Exception Handler Summary

| Exception | HTTP Status | Code |
|---|---|---|
| `UserNotFoundException` | 404 | `USER_NOT_FOUND` |
| `InvalidCredentialsException` | 401 | `INVALID_CREDENTIALS` |
| `InvalidTokenException` | 401 | `INVALID_TOKEN` |
| `EmailNotVerifiedException` | 401 | `EMAIL_NOT_VERIFIED` |
| `SamePasswordException` | 409 | `SAME_PASSWORD` |
| `UserAlreadyExistsException` | 409 | `USER_EXISTS` |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` |

## Package Structure (New Files Only)

```
user/src/main/kotlin/
└── com/danzucker/chirp/
    └── api/
        ├── dto/
        │   ├── ChangePasswordRequest.kt    (NEW)
        │   ├── EmailRequest.kt             (NEW)
        │   └── ResetPasswordRequest.kt     (NEW)
        └── util/
            └── Password.kt                 (NEW)
```
