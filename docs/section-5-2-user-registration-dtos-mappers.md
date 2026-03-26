# Section 5.2 - User Registration DTOs & Mappers

## What This Section Teaches

This section introduces **DTOs (Data Transfer Objects)** — classes specifically designed for API communication. DTOs separate what the API exposes from how your domain models and database entities are structured internally. It also introduces **mapper functions** to convert between domain models and DTOs.

## What Was Created

### Request DTOs (What the client sends)

#### `RegisterRequest.kt`

```kotlin
data class RegisterRequest(
    @field:Email(message = "Must be a valid email address")
    val email: String,
    @field:Length(min = 3, max = 20, message = "Username length must be between 3 and 20 characters")
    val username: String,
    @field:Pattern(
        regexp = "^(?=.*[\\d!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?])(.{8,})$",
        message = "Password must be at least 8 characters and contain at least one digit or special character"
    )
    val password: String
)
```

**Key concepts:**

- **`@field:` target** — In Kotlin data classes, constructor parameters can be a property, a constructor parameter, or a backing field. Jakarta validation checks the *field*, so `@field:Email` ensures the annotation goes on the right target. Without `@field:`, validation silently does nothing.

- **`@Email`** — Built-in Jakarta validation that checks the string is a valid email format.

- **`@Length(min = 3, max = 20)`** — Hibernate Validator annotation that checks string length.

- **`@Pattern(regexp = ...)`** — Validates against a regex. This one requires: at least 8 characters, with at least one digit or special character. The regex breakdown:
  - `(?=.*[\\d!@#$%^&*...])` — Lookahead: must contain at least one digit or special char
  - `(.{8,})` — Must be at least 8 characters total

#### `LoginRequest.kt`

```kotlin
data class LoginRequest(
    val email: String,
    val password: String
)
```

No validation annotations — login validation is handled by the authentication logic (checking credentials against the database), not by bean validation.

### Response DTOs (What the API returns)

#### `UserDto.kt`

```kotlin
data class UserDto(
    val id: UserId,
    val email: String,
    val username: String,
    val hasVerifiedEmail: Boolean,
)
```

Notice this does **not** include `hashedPassword` — you never expose password hashes to the client.

#### `AuthenticatedUserDto.kt`

```kotlin
data class AuthenticatedUserDto(
    val user: UserDto,
    val accessToken: String,
    val refreshToken: String
)
```

Returned after successful registration or login — gives the client their user info plus JWT tokens.

### Mapper Extension Functions

#### `UserMappers.kt`

```kotlin
fun AuthenticatedUser.toAuthenticatedUserDto(): AuthenticatedUserDto {
    return AuthenticatedUserDto(
        user = user.toUserDto(),
        accessToken = accessToken,
        refreshToken = refreshToken
    )
}

fun User.toUserDto(): UserDto {
    return UserDto(
        id = id,
        email = email,
        username = username,
        hasVerifiedEmail = hasEmailVerified
    )
}
```

**Key concepts:**

- **Extension functions** — `User.toUserDto()` adds a `toUserDto()` method to the `User` class without modifying it. This is a Kotlin feature that keeps mapping logic close to the types it converts, without a separate mapper class.

- **Why not just use the domain model as the API response?** — Several reasons:
  1. **Security** — Domain models may contain sensitive fields you don't want to expose
  2. **Stability** — Your API contract stays stable even if domain models change internally
  3. **Different shapes** — The API might need a different structure than the internal model (note `hasEmailVerified` → `hasVerifiedEmail`)

## What Changed in `user/build.gradle.kts`

Added the validation starter:

```kotlin
implementation(libs.spring.boot.starter.validation)
```

This brings in Hibernate Validator and Jakarta Bean Validation, enabling annotations like `@Email`, `@Pattern`, and `@Length` on request DTOs.

## The Three-Layer Pattern

This section establishes a clear three-layer architecture for data flow:

```
Client Request                              Client Response
     │                                           ▲
     ▼                                           │
┌─────────────┐                          ┌───────────────┐
│ RegisterReq │  (API layer - DTOs)      │   UserDto     │
│ LoginReq    │                          │ AuthUserDto   │
└──────┬──────┘                          └───────┬───────┘
       │                                         ▲
       ▼                                         │ mapper
┌─────────────┐                          ┌───────────────┐
│   User      │  (Domain layer - models) │     User      │
│ AuthUser    │                          │   AuthUser    │
└──────┬──────┘                          └───────────────┘
       │
       ▼
┌─────────────┐
│ UserEntity  │  (Infra layer - DB)
└─────────────┘
```

## Package Structure

```
user/src/main/kotlin/
└── com/danzucker/chirp/
    ├── api/
    │   ├── dto/
    │   │   ├── RegisterRequest.kt       (NEW)
    │   │   ├── LoginRequest.kt          (NEW)
    │   │   ├── UserDto.kt              (NEW)
    │   │   └── AuthenticatedUserDto.kt  (NEW)
    │   └── mappers/
    │       └── UserMappers.kt           (NEW)
    ├── domain/
    │   └── model/
    │       ├── User.kt
    │       └── AuthenticatedUser.kt
    └── infra/
        ├── database/
        │   ├── entities/
        │   │   └── UserEntity.kt
        │   └── repositories/
        │       └── UserRepository.kt
        └── security/
            └── PasswordEncoder.kt
```
