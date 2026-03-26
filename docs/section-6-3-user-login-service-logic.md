# Section 6.3 - User Login Service Logic

## What This Section Teaches

This section implements the **login business logic** in `AuthService`. It verifies credentials, generates JWT tokens (access + refresh), stores the refresh token securely, and returns an `AuthenticatedUser` with both tokens. It also introduces two new domain exceptions.

## What Was Created

### `InvalidCredentialsException.kt`

```kotlin
class InvalidCredentialsException : RuntimeException(
    "The entered credentials aren't valid"
)
```

Thrown when the email doesn't exist OR the password doesn't match. Intentionally uses the **same message** for both cases — telling an attacker "email not found" vs "wrong password" leaks information about which emails are registered.

### `UserNotFoundException.kt`

```kotlin
class UserNotFoundException : RuntimeException("User not found")
```

Thrown when a user entity exists but has a null `id` (shouldn't happen in practice, but provides a safety net).

## What Changed

### `AuthService.kt` — Added Login + Refresh Token Storage

**New constructor dependencies:**

```kotlin
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,                    // NEW
    private val refreshTokenRepository: RefreshTokenRepository  // NEW
)
```

**New `login()` method:**

```kotlin
fun login(email: String, password: String): AuthenticatedUser {
    val user = userRepository.findByEmail(email.trim())
        ?: throw InvalidCredentialsException()

    if (!passwordEncoder.matches(password, user.hashedPassword)) {
        throw InvalidCredentialsException()
    }

    return user.id?.let { userId ->
        val accessToken = jwtService.generateAccessToken(userId)
        val refreshToken = jwtService.generateRefreshToken(userId)

        storeRefreshToken(userId, refreshToken)

        AuthenticatedUser(
            user = user.toUser(),
            accessToken = accessToken,
            refreshToken = refreshToken
        )
    } ?: throw UserNotFoundException()
}
```

**Login flow step by step:**

1. **Find user by email** — If not found, throw `InvalidCredentialsException`
2. **Verify password** — BCrypt `matches()` compares the raw password against the stored hash
3. **Generate tokens** — Access token (short-lived) and refresh token (30-day)
4. **Store refresh token** — Hash with SHA-256 and save to `refresh_tokens` table
5. **Return AuthenticatedUser** — Domain model with user info + both tokens

**New `storeRefreshToken()` private method:**

```kotlin
private fun storeRefreshToken(userId: UserId, token: String) {
    val hashed = hashToken(token)
    val expiryMs = jwtService.refreshTokenValidityMs
    val expiresAt = Instant.now().plusMillis(expiryMs)

    refreshTokenRepository.save(
        RefreshTokenEntity(
            userId = userId,
            expiresAt = expiresAt,
            hashedToken = hashed
        )
    )
}
```

**New `hashToken()` private method:**

```kotlin
private fun hashToken(token: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(token.encodeToByteArray())
    return Base64.getEncoder().encodeToString(hashBytes)
}
```

Uses `java.security.MessageDigest` for SHA-256 hashing and `java.util.Base64` for encoding. This is different from BCrypt password hashing — SHA-256 is fast and doesn't need salting because JWT tokens are already high-entropy.

## Security Design Decisions

### Why `InvalidCredentialsException` for Both Cases?

```kotlin
// Email not found
val user = userRepository.findByEmail(email) ?: throw InvalidCredentialsException()

// Password wrong
if (!passwordEncoder.matches(password, user.hashedPassword)) {
    throw InvalidCredentialsException()
}
```

Both throw the **same exception** with the **same message**. This prevents **user enumeration attacks** — an attacker can't probe the API to discover which email addresses are registered.

### Why SHA-256 for Refresh Tokens (Not BCrypt)?

| | BCrypt (passwords) | SHA-256 (refresh tokens) |
|---|---|---|
| **Input entropy** | Low (human-chosen passwords) | High (random JWT strings) |
| **Needs salting?** | Yes (prevents rainbow tables) | No (input is already random) |
| **Speed** | Deliberately slow (anti-brute-force) | Fast (no brute-force risk) |
| **Use case** | Hashing passwords | Hashing tokens |

### The `?.let { } ?: throw` Pattern

```kotlin
return user.id?.let { userId ->
    // ... generate tokens ...
} ?: throw UserNotFoundException()
```

This is a Kotlin idiom for handling nullable values. `user.id` is `UserId?` (nullable before save). After save it should always be non-null, but `?.let` provides a safe path while `?: throw` handles the impossible-but-type-safe case.

## Package Structure (New Files Only)

```
user/src/main/kotlin/
└── com/danzucker/chirp/
    ├── domain/
    │   └── exception/
    │       ├── InvalidCredentialsException.kt  (NEW)
    │       ├── InvalidTokenException.kt
    │       ├── UserAlreadyExistsException.kt
    │       └── UserNotFoundException.kt        (NEW)
    └── service/
        └── auth/
            ├── AuthService.kt                  (MODIFIED)
            └── JwtService.kt
```
