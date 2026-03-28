# Section 7.1 - Email Verification Token Database Setup

## What This Section Teaches

This section sets up the **database infrastructure** for email verification. When a user registers, they'll receive an email with a verification token. This section creates the entity, repository, domain model, and mapper needed to store and look up those tokens.

## What Was Created

### `EmailVerificationToken.kt` — Domain Model

```kotlin
data class EmailVerificationToken(
    val id: Long,
    val token: String,
    val user: User
)
```

A simple domain model — just the ID, the token string, and the associated user. No timestamps at the domain level (those are infrastructure concerns handled by the entity).

### `EmailVerificationTokenEntity.kt` — JPA Entity

```kotlin
@Entity
@Table(
    name = "email_verification_tokens",
    schema = "user_service",
)
class EmailVerificationTokenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @Column(nullable = false, unique = true)
    var token: String,
    @Column(nullable = false)
    var expiresAt: Instant,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: UserEntity,
    @Column
    var usedAt: Instant?,
    @CreationTimestamp
    var createdAt: Instant = Instant.now(),
)
```

**Key concepts:**

- **`@ManyToOne(fetch = FetchType.LAZY)`** — Many tokens can belong to one user (e.g., if they request verification multiple times). `LAZY` means the `UserEntity` is only loaded from the database when you actually access it — not when the token is loaded. This avoids unnecessary joins.

- **`@JoinColumn(name = "user_id")`** — Creates a `user_id` foreign key column in the `email_verification_tokens` table that references the `users` table.

- **`usedAt: Instant?`** — Nullable. `null` means the token hasn't been used yet. When the user clicks the verification link, this gets set to the current time. This prevents token reuse.

- **`expiresAt: Instant`** — Tokens have a limited lifetime. Expired tokens can be cleaned up via `deleteByExpiresAtLessThan()`.

- **`unique = true` on `token`** — Each verification token string must be unique in the database.

### `EmailVerificationTokenRepository.kt` — Repository

```kotlin
interface EmailVerificationTokenRepository : JpaRepository<EmailVerificationTokenEntity, Long> {
    fun findByToken(token: String): EmailVerificationTokenEntity?
    fun deleteByExpiresAtLessThan(now: Instant)
    fun findByUserAndUsedAtIsNull(user: UserEntity): List<EmailVerificationTokenEntity>
}
```

| Method | Purpose |
|---|---|
| `findByToken(token)` | Look up a token when user clicks the verification link |
| `deleteByExpiresAtLessThan(now)` | Clean up expired tokens (scheduled job) |
| `findByUserAndUsedAtIsNull(user)` | Find all unused tokens for a user (to invalidate old ones when sending a new one) |

### `EmailVerificationTokenMappers.kt` — Entity → Domain Mapper

```kotlin
fun EmailVerificationTokenEntity.toEmailVerificationToken(): EmailVerificationToken {
    return EmailVerificationToken(
        id = id,
        token = token,
        user = user.toUser()
    )
}
```

Converts the JPA entity to the domain model. Reuses the existing `UserEntity.toUser()` mapper from the user mappers.

## Entity Relationships

```
┌──────────────────────┐         ┌────────────────────────────────┐
│      users           │         │  email_verification_tokens     │
│──────────────────────│         │────────────────────────────────│
│ id (UUID, PK)        │◄────────│ user_id (UUID, FK)             │
│ email                │    1:N  │ id (Long, PK, auto-increment)  │
│ username             │         │ token (String, unique)          │
│ hashed_password      │         │ expires_at (Instant)            │
│ has_verified_email   │         │ used_at (Instant, nullable)     │
│ created_at           │         │ created_at (Instant)            │
│ updated_at           │         └────────────────────────────────┘
└──────────────────────┘
```

## Comparison with RefreshTokenEntity

| | `RefreshTokenEntity` | `EmailVerificationTokenEntity` |
|---|---|---|
| **User link** | `userId: UserId` (plain column) | `@ManyToOne user: UserEntity` (JPA relationship) |
| **Token storage** | Hashed (SHA-256) | Plain text (not sensitive) |
| **Usage tracking** | Deleted after use | `usedAt` timestamp set |
| **Cleanup** | Manual delete | `deleteByExpiresAtLessThan()` |

The email token uses a **JPA relationship** (`@ManyToOne`) while the refresh token uses a plain `userId` column. The JPA relationship lets you navigate from token to user object directly, which is useful for the verification flow.

## Package Structure (New Files Only)

```
user/src/main/kotlin/
└── com/danzucker/chirp/
    ├── domain/
    │   └── model/
    │       └── EmailVerificationToken.kt                (NEW)
    └── infra/
        └── database/
            ├── entities/
            │   └── EmailVerificationTokenEntity.kt      (NEW)
            ├── mappers/
            │   └── EmailVerificationTokenMappers.kt     (NEW)
            └── repositories/
                └── EmailVerificationTokenRepository.kt  (NEW)
```
