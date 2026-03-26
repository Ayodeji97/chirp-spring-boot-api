# Section 6.1 - Understanding JWT Token Refresh

## What This Section Teaches

This section implements **JWT (JSON Web Token) authentication** — the mechanism that lets clients prove their identity without server-side sessions. It covers generating access tokens, refresh tokens, validating them, and storing refresh tokens securely in the database.

## How JWT Auth Works

```
1. User registers/logs in
   └─→ Server generates access token (short-lived) + refresh token (long-lived)
   └─→ Both returned to client

2. Client makes API requests
   └─→ Sends access token in Authorization header: "Bearer <token>"
   └─→ Server validates token, extracts userId

3. Access token expires
   └─→ Client sends refresh token to get a new access token
   └─→ Server validates refresh token against DB, issues new pair

4. Refresh token expires or user logs out
   └─→ Refresh token deleted from DB
   └─→ User must log in again
```

### Why Two Tokens?

| | Access Token | Refresh Token |
|---|---|---|
| **Lifetime** | Short (15 min prod, 1000 min dev) | Long (30 days) |
| **Stored in DB?** | No | Yes (hashed) |
| **Sent with every request?** | Yes | Only when refreshing |
| **Can be revoked?** | No (expires naturally) | Yes (delete from DB) |

**Why not just one long-lived token?** If an access token is stolen, the attacker has limited time. The refresh token is only sent occasionally and is stored hashed in the DB, so it can be revoked.

## What Was Created

### `JwtService.kt` — Token Generation & Validation

```kotlin
@Service
class JwtService(
    @param:Value("\${jwt.secret}") private val secretBase64: String,
    @param:Value("\${jwt.expiration-minutes}") private val expirationMinutes: Int,
) {
    private val secretKey = Keys.hmacShaKeyFor(Base64.decode(secretBase64))
    private val accessTokenValidityMs = expirationMinutes * 60 * 1000L
    val refreshTokenValidityMs = 30 * 24 * 60 * 60 * 1000L

    fun generateAccessToken(userId: UserId): String { ... }
    fun generateRefreshToken(userId: UserId): String { ... }
    fun validateAccessToken(token: String): Boolean { ... }
    fun validateRefreshToken(token: String): Boolean { ... }
    fun getUserIdFromToken(token: String): UserId { ... }
}
```

**Key concepts:**

- **`@param:Value("\${jwt.secret}")`** — Injects the `jwt.secret` config value. The `@param:` target is needed in Kotlin so the annotation goes on the constructor *parameter* (not the property), which is what Spring reads during construction.

- **HMAC-SHA256 signing** — `Keys.hmacShaKeyFor(Base64.decode(secretBase64))` creates a signing key from a Base64-encoded secret. Every token is signed with this key, and only someone with the key can create valid tokens.

- **Token types** — Each token has a `"type"` claim (`"access"` or `"refresh"`). This prevents using a refresh token as an access token and vice versa.

- **`parseAllClaims()`** — Handles the `"Bearer "` prefix automatically. Returns `null` on any parsing failure (expired, invalid signature, malformed) instead of throwing.

- **JJWT library** — Uses `io.jsonwebtoken:jjwt-api` (0.12.6) for token creation and parsing. `jjwt-impl` and `jjwt-jackson` are `runtimeOnly` since your code only uses the API interfaces.

### JWT Token Structure

A JWT has three parts separated by dots: `header.payload.signature`

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI1NTBl...
│                      │                    │
│                      │                    └── Signature (HMAC-SHA256)
│                      └── Payload (Base64-encoded JSON)
└── Header (Base64-encoded JSON)
```

Payload contains:
```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",  // userId
  "type": "access",                                 // token type
  "iat": 1711411200,                                // issued at
  "exp": 1711412100                                 // expiration
}
```

### `InvalidTokenException.kt` — Domain Exception

```kotlin
class InvalidTokenException(
    override val message: String?
) : RuntimeException(message ?: "Invalid token")
```

Thrown when `getUserIdFromToken()` fails to parse a token. The exception handler returns HTTP 401 with `INVALID_TOKEN` code.

### `RefreshTokenEntity.kt` — Database Entity for Refresh Tokens

```kotlin
@Entity
@Table(
    name = "refresh_tokens",
    schema = "user_service",
    indexes = [
        Index(name = "idx_refresh_tokens_user_id", columnList = "user_id"),
        Index(name = "idx_refresh_tokens_user_token", columnList = "user_id,hashed_token"),
    ]
)
class RefreshTokenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @Column(nullable = false)
    var userId: UserId,
    @Column(nullable = false)
    var expiresAt: Instant,
    @Column(nullable = false)
    var hashedToken: String,
    @CreationTimestamp
    var createdAt: Instant = Instant.now()
)
```

**Key concepts:**

- **`hashedToken`** — The refresh token JWT is hashed (with SHA-256) before storing. This way, even if the database is compromised, attackers can't use the stolen hashes as valid refresh tokens.

- **`GenerationType.IDENTITY`** — Uses auto-increment `Long` for the ID (not UUID). Refresh tokens are frequently inserted/deleted and don't need globally unique IDs.

- **Composite index** `(user_id, hashed_token)` — Optimizes the lookup when validating a refresh token (you search by both user and token hash).

- **`expiresAt`** — Stored so expired tokens can be cleaned up via a scheduled job later.

### `RefreshTokenRepository.kt` — Repository

```kotlin
interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, Long> {
    fun findByUserIdAndHashedToken(userId: UserId, hashedToken: String): RefreshTokenEntity?
    fun deleteByUserIdAndHashedToken(userId: UserId, hashedToken: String)
    fun deleteByUserId(userId: UserId)
}
```

| Method | Purpose |
|---|---|
| `findByUserIdAndHashedToken()` | Validate a refresh token during token refresh |
| `deleteByUserIdAndHashedToken()` | Invalidate a single refresh token (logout from one device) |
| `deleteByUserId()` | Invalidate all refresh tokens for a user (logout from all devices) |

## Config Changes

### `application.yml` — Added JWT secret

```yaml
jwt:
  secret: ${JWT_SECRET_BASE64}
```

The secret is a Base64-encoded string loaded from an environment variable. Generate one with:
```bash
openssl rand -base64 32
```

### `application-dev.yml` — Long token expiry for dev

```yaml
jwt:
  expiration-minutes: 1000
```

~16 hours — so you don't have to keep re-authenticating during development.

### `application-prod.yml` — Short token expiry for prod

```yaml
jwt:
  expiration-minutes: 15
```

15 minutes — standard for production. Refresh tokens handle getting new access tokens.

## Updated Exception Handler

Added `InvalidTokenException` handler to `AuthExceptionHandler`:

```kotlin
@ExceptionHandler(InvalidTokenException::class)
@ResponseStatus(HttpStatus.UNAUTHORIZED)
fun onInvalidToken(e: InvalidTokenException) = mapOf(
    "code" to "INVALID_TOKEN",
    "message" to e.message
)
```

## Dependencies Added

### `user/build.gradle.kts`

```kotlin
implementation(libs.jwt.api)        // io.jsonwebtoken:jjwt-api:0.12.6
runtimeOnly(libs.jwt.impl)          // io.jsonwebtoken:jjwt-impl:0.12.6
runtimeOnly(libs.jwt.jackson)       // io.jsonwebtoken:jjwt-jackson:0.12.6
```

- **`jjwt-api`** — The interfaces you code against (`Jwts.builder()`, `Jwts.parser()`)
- **`jjwt-impl`** — The actual implementation (loaded at runtime via ServiceLoader)
- **`jjwt-jackson`** — JSON serializer for JWT claims (uses Jackson under the hood)

## Package Structure

```
user/src/main/kotlin/
└── com/danzucker/chirp/
    ├── api/
    │   ├── controllers/
    │   │   └── AuthController.kt
    │   ├── dto/ ...
    │   ├── exception_handling/
    │   │   └── AuthExceptionHandler.kt         (MODIFIED - added InvalidTokenException handler)
    │   └── mappers/ ...
    ├── domain/
    │   ├── exception/
    │   │   ├── InvalidTokenException.kt        (NEW)
    │   │   └── UserAlreadyExistsException.kt
    │   └── model/ ...
    ├── infra/
    │   ├── database/
    │   │   ├── entities/
    │   │   │   ├── RefreshTokenEntity.kt       (NEW)
    │   │   │   └── UserEntity.kt
    │   │   ├── mappers/ ...
    │   │   └── repositories/
    │   │       ├── RefreshTokenRepository.kt   (NEW)
    │   │       └── UserRepository.kt
    │   └── security/
    │       └── PasswordEncoder.kt
    └── service/
        └── auth/
            ├── AuthService.kt
            └── JwtService.kt                   (NEW)
```

## What You Need to Do

Set the `JWT_SECRET_BASE64` environment variable before running. Generate a secret:
```bash
openssl rand -base64 32
```
Then export it:
```bash
export JWT_SECRET_BASE64=your_generated_base64_string
```
