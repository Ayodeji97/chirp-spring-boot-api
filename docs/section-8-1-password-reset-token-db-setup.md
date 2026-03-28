# Section 8.1 - Password Reset Token Database Setup

## What This Section Teaches

This section creates the **database infrastructure** for password reset tokens — following the same pattern as email verification tokens. It also refactors the email verification system to use more efficient bulk database operations.

## What Was Created

### `PasswordResetTokenEntity.kt` — JPA Entity

```kotlin
@Entity
@Table(
    name = "password_reset_tokens",
    schema = "user_service",
    indexes = [
        Index(name = "idx_password_reset_token_token", columnList = "token")
    ]
)
class PasswordResetTokenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @Column(nullable = false, unique = true)
    var token: String = TokenGenerator.generateSecureToken(),
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: UserEntity,
    @Column(nullable = false)
    var expiresAt: Instant,
    @Column(nullable = true)
    var usedAt: Instant? = null,
    @CreationTimestamp
    var createdAt: Instant = Instant.now(),
)
```

Nearly identical to `EmailVerificationTokenEntity` — same pattern of auto-generated token, user relationship, expiry, and usage tracking.

### `PasswordResetTokenRepository.kt` — Repository

```kotlin
interface PasswordResetTokenRepository : JpaRepository<PasswordResetTokenEntity, Long> {
    fun findByToken(token: String): PasswordResetTokenEntity?
    fun deleteByExpiresAtLessThan(now: Instant)

    @Modifying
    @Query("""
        UPDATE PasswordResetTokenEntity p
        SET p.usedAt = CURRENT_TIMESTAMP
        WHERE p.user = :user
    """)
    fun invalidateActiveTokensForUser(user: UserEntity)
}
```

**Key concepts:**

- **`@Modifying` + `@Query`** — A custom JPQL query that performs a bulk UPDATE directly in the database. This is more efficient than loading all active tokens into memory, setting `usedAt` on each one, and saving them back individually.

- **`CURRENT_TIMESTAMP`** — A JPQL function that resolves to the database server's current time. Used instead of passing `Instant.now()` from the application.

- **`invalidateActiveTokensForUser`** — Marks all tokens for a user as "used" in one query. Called before creating a new reset token, ensuring only the latest token is valid.

## What Changed (Refactoring)

### `EmailVerificationTokenEntity.kt` — Added Index

```kotlin
@Table(
    name = "email_verification_tokens",
    schema = "user_service",
    indexes = [
        Index(name = "idx_email_verification_token_token", columnList = "token")  // NEW
    ]
)
```

The `token` column now has a database index for faster lookups via `findByToken()`.

### `EmailVerificationTokenRepository.kt` — Bulk Update Instead of Fetch-Save

```kotlin
// BEFORE: Load all tokens, mutate in memory, save back
fun findByUserAndUsedAtIsNull(user: UserEntity): List<EmailVerificationTokenEntity>

// AFTER: Single bulk UPDATE query
@Modifying
@Query("""
    UPDATE EmailVerificationTokenEntity e
    SET e.usedAt = CURRENT_TIMESTAMP
    WHERE e.user = :user
""")
fun invalidateActiveTokensForUser(user: UserEntity)
```

### `EmailVerificationService.kt` — Simplified Token Invalidation

```kotlin
// BEFORE (5 lines):
val existingTokens = emailVerificationTokenRepository.findByUserAndUsedAtIsNull(user = userEntity)
val now = Instant.now()
val usedTokens = existingTokens.map { it.apply { this.usedAt = now } }
emailVerificationTokenRepository.saveAll(usedTokens)

// AFTER (1 line):
emailVerificationTokenRepository.invalidateActiveTokensForUser(userEntity)
```

## Why `@Modifying` + `@Query` Instead of Derived Methods?

Spring Data can derive `DELETE` and `SELECT` queries from method names, but it **cannot derive `UPDATE` queries**. For bulk updates, you need a custom JPQL query with `@Modifying`.

| Approach | Pros | Cons |
|---|---|---|
| Load + mutate + save | Simple, no custom query | N+1 queries, loads all entities into memory |
| `@Modifying` + `@Query` | Single query, efficient | Bypasses Hibernate cache, requires `@Transactional` |

The bulk approach is better for invalidating tokens because you don't need the entities in memory — you just need to mark them as used.

## Token Entity Comparison

| | `EmailVerificationTokenEntity` | `PasswordResetTokenEntity` |
|---|---|---|
| Table | `email_verification_tokens` | `password_reset_tokens` |
| Token | Auto-generated (URL-safe Base64) | Auto-generated (URL-safe Base64) |
| User link | `@ManyToOne` (LAZY) | `@ManyToOne` (LAZY) |
| Expiry | Configurable (`chirp.email.verification.expiry-hours`) | Will be configurable (next section) |
| Usage tracking | `usedAt` + `isUsed`/`isExpired` | `usedAt` (computed properties can be added) |
| Index | `token` column | `token` column |

## Package Structure (New Files Only)

```
user/src/main/kotlin/
└── com/danzucker/chirp/
    └── infra/
        └── database/
            ├── entities/
            │   └── PasswordResetTokenEntity.kt        (NEW)
            └── repositories/
                └── PasswordResetTokenRepository.kt    (NEW)
```
