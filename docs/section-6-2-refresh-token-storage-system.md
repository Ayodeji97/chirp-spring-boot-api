# Section 6.2 - Refresh Token Storage System

## What This Section Teaches

This section covers how refresh tokens are **securely stored** in the database. Rather than storing raw JWT strings, the system hashes them with SHA-256 before persisting — so even if the database is compromised, the tokens can't be used.

> **Note:** The code for this section was already implemented in Section 6.1, where we combined the JWT service and refresh token storage into a single branch. This doc focuses on the concepts behind refresh token storage.

## Why Store Refresh Tokens?

Access tokens are **stateless** — the server never stores them. It just validates the signature and expiration. But refresh tokens need to be **revocable**:

- User logs out → delete their refresh token from DB
- User changes password → delete all refresh tokens (force re-login everywhere)
- Suspicious activity → revoke specific tokens

Without storing them, you can't revoke them.

## Why Hash Before Storing?

```
Raw refresh token:  eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOi...
                           │
                    SHA-256 hash
                           │
                           ▼
Stored in DB:       a3f2b8c1d4e5f6a7b8c9d0e1f2a3b4c5...
```

If an attacker gains read access to your database (SQL injection, backup leak, etc.):
- **Raw tokens stored** → Attacker can use them immediately to impersonate users
- **Hashed tokens stored** → Attacker has useless hashes (SHA-256 is one-way)

This is the same principle as password hashing, but SHA-256 is used instead of BCrypt because:
- Refresh tokens are already high-entropy random strings (no need for salting)
- SHA-256 is fast — BCrypt's deliberate slowness isn't needed here

## The RefreshTokenEntity (Created in 6.1)

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

### Column Breakdown

| Column | Type | Purpose |
|---|---|---|
| `id` | `Long` (auto-increment) | Primary key — simple sequential ID is fine here |
| `userId` | `UUID` | Links to the user who owns this refresh token |
| `expiresAt` | `Instant` | When the token expires (30 days from creation) |
| `hashedToken` | `String` | SHA-256 hash of the raw JWT refresh token |
| `createdAt` | `Instant` | When the token was issued (audit trail) |

### Index Strategy

| Index | Columns | Purpose |
|---|---|---|
| `idx_refresh_tokens_user_id` | `user_id` | Fast lookup of all tokens for a user (logout-all) |
| `idx_refresh_tokens_user_token` | `user_id, hashed_token` | Fast lookup when validating a specific refresh token |

The composite index `(user_id, hashed_token)` is used during token refresh — you need to find the specific token for a specific user.

## The Repository (Created in 6.1)

```kotlin
interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, Long> {
    fun findByUserIdAndHashedToken(userId: UserId, hashedToken: String): RefreshTokenEntity?
    fun deleteByUserIdAndHashedToken(userId: UserId, hashedToken: String)
    fun deleteByUserId(userId: UserId)
}
```

### How Each Method Will Be Used

| Method | Use Case |
|---|---|
| `findByUserIdAndHashedToken()` | Token refresh — verify the refresh token exists and isn't expired |
| `deleteByUserIdAndHashedToken()` | Single logout — invalidate one refresh token (one device) |
| `deleteByUserId()` | Logout all — invalidate every refresh token for a user (all devices) |

## Refresh Token Lifecycle

```
1. LOGIN / REGISTER
   ├─ Generate refresh token JWT
   ├─ Hash it with SHA-256
   ├─ Store hash + userId + expiresAt in DB
   └─ Return raw JWT to client

2. TOKEN REFRESH
   ├─ Client sends raw refresh token
   ├─ Server hashes it
   ├─ Lookup hash in DB (findByUserIdAndHashedToken)
   ├─ If found and not expired:
   │   ├─ Delete old refresh token from DB
   │   ├─ Generate new access + refresh tokens
   │   ├─ Store new refresh token hash in DB
   │   └─ Return new tokens to client
   └─ If not found or expired: return 401

3. LOGOUT
   ├─ Hash the refresh token
   └─ Delete from DB (deleteByUserIdAndHashedToken)

4. LOGOUT ALL DEVICES
   └─ Delete all tokens for userId (deleteByUserId)
```

## No New Code Changes

All code for this section was already implemented in Section 6.1. The files involved:
- `infra/database/entities/RefreshTokenEntity.kt`
- `infra/database/repositories/RefreshTokenRepository.kt`
