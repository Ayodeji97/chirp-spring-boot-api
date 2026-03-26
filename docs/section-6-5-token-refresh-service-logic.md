# Section 6.5 - Token Refresh Service Logic

## What This Section Teaches

This section implements **token rotation** — the process of exchanging an expiring refresh token for a new access + refresh token pair. Each refresh token is single-use: once used, it's deleted and a new one is issued. This limits the damage if a refresh token is stolen.

## What Changed

### `AuthService.kt` — Added `refresh()` Method

```kotlin
@Transactional
fun refresh(refreshToken: String): AuthenticatedUser {
    if (!jwtService.validateRefreshToken(refreshToken)) {
        throw InvalidTokenException(message = "Invalid refresh token")
    }

    val userId = jwtService.getUserIdFromToken(refreshToken)
    val user = userRepository.findByIdOrNull(userId)
        ?: throw UserNotFoundException()

    val hashed = hashToken(refreshToken)

    return user.id?.let { userId ->
        refreshTokenRepository.findByUserIdAndHashedToken(
            userId = userId,
            hashedToken = hashed
        ) ?: throw InvalidTokenException("Invalid refresh token")

        refreshTokenRepository.deleteByUserIdAndHashedToken(
            userId = userId,
            hashedToken = hashed
        )

        val newAccessToken = jwtService.generateAccessToken(userId)
        val newRefreshToken = jwtService.generateRefreshToken(userId)

        storeRefreshToken(userId, newRefreshToken)

        AuthenticatedUser(
            user = user.toUser(),
            accessToken = newAccessToken,
            refreshToken = newRefreshToken
        )
    } ?: throw UserNotFoundException()
}
```

## Refresh Flow Step by Step

```
Client sends: POST /api/auth/refresh { "refreshToken": "eyJ..." }
                                │
                                ▼
         ┌──────────────────────────────────────┐
    1.   │ Validate JWT signature + expiry       │
         │ jwtService.validateRefreshToken()     │
         │ Is it a "refresh" type token?         │
         └──────────┬───────────────────────────┘
                    │ ✓ valid
                    ▼
         ┌──────────────────────────────────────┐
    2.   │ Extract userId from token             │
         │ Look up user in database              │
         └──────────┬───────────────────────────┘
                    │ ✓ user exists
                    ▼
         ┌──────────────────────────────────────┐
    3.   │ Hash the token with SHA-256           │
         │ Look up hash in refresh_tokens table  │
         │ Does this token exist in our DB?      │
         └──────────┬───────────────────────────┘
                    │ ✓ found in DB
                    ▼
         ┌──────────────────────────────────────┐
    4.   │ DELETE old refresh token from DB      │
         │ (single-use: can't be reused)         │
         └──────────┬───────────────────────────┘
                    │
                    ▼
         ┌──────────────────────────────────────┐
    5.   │ Generate NEW access token             │
         │ Generate NEW refresh token            │
         │ Store NEW refresh token hash in DB    │
         └──────────┬───────────────────────────┘
                    │
                    ▼
         ┌──────────────────────────────────────┐
    6.   │ Return AuthenticatedUser              │
         │ (user info + new token pair)          │
         └──────────────────────────────────────┘
```

## Key Concepts

### Token Rotation

Every time a refresh token is used, it's **deleted** and a **new one** is created. This means:

- Each refresh token can only be used **once**
- If an attacker steals a refresh token and the real user uses it first, the attacker's copy becomes invalid
- If the attacker uses it first, the real user's next refresh will fail, alerting them to a compromise

### `@Transactional`

```kotlin
@Transactional
fun refresh(refreshToken: String): AuthenticatedUser {
```

This annotation wraps the entire method in a database transaction. If any step fails (e.g., saving the new token), the delete of the old token is **rolled back**. Without this, you could end up in a state where the old token is deleted but the new one wasn't saved — locking the user out.

**Why `login()` and `register()` don't need `@Transactional`:**
- `register()` does a single `save()` — one operation, no need for a transaction
- `login()` does a `save()` of the refresh token — again, one write operation
- `refresh()` does a `delete` + `save` — two operations that must succeed or fail together

### `findByIdOrNull()` — Spring Data Kotlin Extension

```kotlin
import org.springframework.data.repository.findByIdOrNull

val user = userRepository.findByIdOrNull(userId) ?: throw UserNotFoundException()
```

This is a Kotlin extension function provided by Spring Data. The standard `findById()` returns `Optional<T>` (Java-style). `findByIdOrNull()` unwraps it to `T?` (Kotlin-style), which works naturally with the `?:` Elvis operator.

### Double Validation (JWT + Database)

The refresh token is validated **twice**:

1. **JWT validation** (`jwtService.validateRefreshToken()`) — Checks the signature is valid, the token hasn't expired, and it's a "refresh" type token
2. **Database validation** (`findByUserIdAndHashedToken()`) — Checks the token hash exists in the database and hasn't been revoked

Both are needed because:
- JWT validation alone can't handle revocation (JWTs are stateless)
- Database validation alone can't check expiry or signature integrity

## No Other Changes

Only `AuthService.kt` was modified in this section. The controller endpoint for refresh comes in the next section.
