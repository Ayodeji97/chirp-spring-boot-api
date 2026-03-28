# Section 8.2 - Password Reset Service Logic

## What This Section Teaches

This section implements the **business logic** for password reset — requesting a reset token, resetting the password via token, and changing the password for authenticated users. It also invalidates all refresh tokens after a password change to force re-login on all devices.

## What Was Created

### `PasswordResetService.kt` — Reset & Change Password Logic

```kotlin
@Service
class PasswordResetService(
    private val userRepository: UserRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    @param:Value("\${chirp.email.reset-password.expiry-minutes}") private val expiryMinutes: Long,
    private val refreshTokenRepository: RefreshTokenRepository
)
```

Four methods:

#### `requestPasswordReset(email)` — Generate Reset Token

```kotlin
@Transactional
fun requestPasswordReset(email: String) {
    val user = userRepository.findByEmail(email) ?: return
    passwordResetTokenRepository.invalidateActiveTokensForUser(user)
    val token = PasswordResetTokenEntity(
        user = user,
        expiresAt = Instant.now().plus(expiryMinutes, ChronoUnit.MINUTES),
    )
    passwordResetTokenRepository.save(token)
}
```

- **Silent failure** — If the email doesn't exist, the method silently returns instead of throwing. This prevents **user enumeration** — an attacker can't probe which emails are registered by watching for error responses.
- Invalidates all existing tokens before creating a new one.
- Token expires after 30 minutes (configurable).

#### `resetPassword(token, newPassword)` — Reset Via Token

```kotlin
@Transactional
fun resetPassword(token: String, newPassword: String) {
    val resetToken = passwordResetTokenRepository.findByToken(token)
        ?: throw InvalidTokenException("Invalid password reset token")

    if (resetToken.isUsed) { throw InvalidTokenException("...") }
    if (resetToken.isExpired) { throw InvalidTokenException("...") }

    if (passwordEncoder.matches(newPassword, user.hashedPassword)) {
        throw SamePasswordException()
    }

    // Update password, mark token used, delete all refresh tokens
}
```

- Validates token: exists, not used, not expired
- Checks new password differs from old (via BCrypt `matches()`)
- Updates the user's hashed password
- Marks the token as used
- **Deletes all refresh tokens** — forces the user to re-login on all devices with the new password

#### `changePassword(userId, oldPassword, newPassword)` — Authenticated Change

```kotlin
@Transactional
fun changePassword(userId: UserId, oldPassword: String, newPassword: String) {
    val user = userRepository.findByIdOrNull(userId)
        ?: throw UserNotFoundException()

    if (!passwordEncoder.matches(oldPassword, user.hashedPassword)) {
        throw InvalidCredentialsException()
    }

    if (oldPassword == newPassword) {
        throw SamePasswordException()
    }

    refreshTokenRepository.deleteByUserId(user.id!!)
    userRepository.save(user.apply { this.hashedPassword = passwordEncoder.encode(newPassword) })
}
```

- For logged-in users changing their own password
- Requires the old password for verification
- Checks old != new (plain string comparison here, since we have the raw old password)
- Deletes all refresh tokens (force re-login)

#### `cleanupExpiredTokens()` — Daily Cleanup

```kotlin
@Scheduled(cron = "0 0 3 * * *")
fun cleanupExpiredTokens() {
    passwordResetTokenRepository.deleteByExpiresAtLessThan(now = Instant.now())
}
```

### `SamePasswordException.kt` — Domain Exception

```kotlin
class SamePasswordException : RuntimeException(
    "The new password can't be equal to the old one."
)
```

## What Changed

### `PasswordResetTokenEntity.kt` — Added Computed Properties

```kotlin
) {
    val isUsed: Boolean
        get() = usedAt != null

    val isExpired: Boolean
        get() = Instant.now() > expiresAt
}
```

### `application.yml` — Added Reset Config

```yaml
chirp:
  email:
    verification:
      expiry-hours: 24
    reset-password:
      expiry-minutes: 30    # NEW
```

## Security Design Decisions

### Why Delete All Refresh Tokens on Password Change?

If someone's password was compromised:
1. They reset their password
2. All refresh tokens are deleted → all sessions are invalidated
3. The attacker's stolen refresh token stops working
4. Everyone (including the real user) must login again with the new password

Without this, an attacker with a valid refresh token could keep accessing the account even after a password reset.

### `resetPassword` vs `changePassword`

| | `resetPassword` | `changePassword` |
|---|---|---|
| **Auth required?** | No (uses token from email) | Yes (needs userId + old password) |
| **Verification** | Token validation | Old password check |
| **Same-password check** | BCrypt `matches()` (compares raw vs hash) | Plain `==` (has both raw strings) |
| **Use case** | "Forgot password" flow | Settings page "Change password" |

## Package Structure (New Files Only)

```
user/src/main/kotlin/
└── com/danzucker/chirp/
    ├── domain/
    │   └── exception/
    │       └── SamePasswordException.kt            (NEW)
    └── service/
        └── auth/
            └── PasswordResetService.kt             (NEW)
```
