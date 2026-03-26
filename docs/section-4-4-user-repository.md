# Section 4.4 - Building the User Repository

## What This Section Teaches

This section focuses on the **Spring Data JPA repository pattern** — how Spring can auto-generate database queries from interface method names. It also adds SSL enforcement for the Supabase connection.

> **Note:** We already created `UserRepository.kt` and `UserEntity.kt` in Section 4.3. In Philipp's course, the entity and repository were split across two branches. This section covers the repository concepts in depth and adds the SSL config change.

## The Repository (Already Created in 4.3)

```kotlin
package com.danzucker.chirp.infra.database.repositories

import com.danzucker.chirp.domain.model.UserId
import com.danzucker.chirp.infra.database.entities.UserEntity
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<UserEntity, UserId> {
    fun findByEmail(email: String): UserEntity?
    fun findByEmailOrUsername(email: String, username: String): UserEntity?
}
```

## How Spring Data Query Derivation Works

Spring Data reads your method name and generates SQL automatically. The method name follows a specific grammar:

```
findBy + PropertyName + [Keyword] + [And/Or + PropertyName + [Keyword]]
```

### Examples from our repository:

| Method | Generated SQL |
|---|---|
| `findByEmail(email)` | `SELECT * FROM users WHERE email = ?` |
| `findByEmailOrUsername(email, username)` | `SELECT * FROM users WHERE email = ? OR username = ?` |

### Other keywords you can use:

| Keyword | Example | SQL |
|---|---|---|
| `And` | `findByEmailAndUsername(e, u)` | `WHERE email = ? AND username = ?` |
| `Or` | `findByEmailOrUsername(e, u)` | `WHERE email = ? OR username = ?` |
| `OrderBy` | `findByEmailOrderByCreatedAtDesc(e)` | `WHERE email = ? ORDER BY created_at DESC` |
| `IsNull` | `findByEmailIsNull()` | `WHERE email IS NULL` |
| `Like` | `findByUsernameLike(pattern)` | `WHERE username LIKE ?` |
| `GreaterThan` | `findByCreatedAtGreaterThan(date)` | `WHERE created_at > ?` |

### What `JpaRepository<UserEntity, UserId>` gives you for free:

These methods are inherited — no code needed:

| Method | What it does |
|---|---|
| `save(entity)` | INSERT or UPDATE (upsert) |
| `findById(id)` | SELECT by primary key |
| `findAll()` | SELECT all rows |
| `deleteById(id)` | DELETE by primary key |
| `count()` | COUNT all rows |
| `existsById(id)` | Check if a row exists |

## What Changed in `application.yml`

Added `?sslmode=require` to the datasource URL:

```yaml
# Before
url: jdbc:postgresql://db.xxx.supabase.co:5432/postgres

# After
url: jdbc:postgresql://db.xxx.supabase.co:5432/postgres?sslmode=require
```

**Why?** Supabase databases are accessed over the internet. Without SSL, your database credentials and query data travel in plaintext — anyone on the network path could intercept them. `sslmode=require` forces the JDBC driver to use an encrypted TLS connection.

### SSL Mode Options

| Mode | Encryption | Verifies Server | When to use |
|---|---|---|---|
| `disable` | No | No | Never in production |
| `allow` | Maybe | No | Not recommended |
| `prefer` | If available | No | Default, but not secure enough for remote DBs |
| `require` | Yes | No | **Good for Supabase** — encrypts the connection |
| `verify-ca` | Yes | CA only | When you have the CA certificate |
| `verify-full` | Yes | CA + hostname | Maximum security |

## Why Nullable Return Types (`UserEntity?`)

```kotlin
fun findByEmail(email: String): UserEntity?
```

The `?` means this method returns `null` if no user is found with that email. This is a Kotlin safety feature — the compiler forces you to handle the null case:

```kotlin
val user = userRepository.findByEmail("test@example.com")
    ?: throw UserNotFoundException("No user with that email")
```

Without the `?`, you'd get a `NullPointerException` at runtime if the user doesn't exist. Kotlin's null safety catches this at compile time.

## Package Structure (Unchanged)

```
user/src/main/kotlin/
└── com/danzucker/user/
    ├── domain/
    │   └── model/
    │       ├── User.kt
    │       └── AuthenticatedUser.kt
    └── infra/
        └── database/
            ├── entities/
            │   └── UserEntity.kt
            └── repositories/
                └── UserRepository.kt
```
