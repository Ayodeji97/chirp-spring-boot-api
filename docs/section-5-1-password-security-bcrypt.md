# Section 5.1 - Password Security With BCrypt

## What This Section Teaches

This section introduces **password hashing** — the practice of never storing raw passwords in the database. Instead, passwords are run through a one-way hashing algorithm (BCrypt) that produces an irreversible hash. When a user logs in, you hash their input and compare it to the stored hash.

## What Was Created

### `PasswordEncoder.kt` — BCrypt Wrapper

```kotlin
package com.danzucker.chirp.infra.security

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

@Component
class PasswordEncoder {

    private val bcrypt = BCryptPasswordEncoder()

    fun encode(rawPassword: String): String = bcrypt.encode(rawPassword)!!

    fun matches(rawPassword: String, hashedPassword: String): Boolean {
        return bcrypt.matches(rawPassword, hashedPassword)
    }
}
```

**Key concepts:**

- **`BCryptPasswordEncoder`** — Spring Security's implementation of the BCrypt hashing algorithm. It automatically handles salt generation and embeds the salt in the hash output.

- **`encode(rawPassword)`** — Takes a plain text password like `"MyPassword123"` and returns a hash like `"$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"`. Every call produces a **different hash** for the same password (because the salt is random).

- **`matches(rawPassword, hashedPassword)`** — Extracts the salt from the stored hash, re-hashes the raw password with that same salt, and compares. Returns `true` if they match.

- **`@Component`** — Registers this class as a Spring bean so it can be injected into other classes via constructor injection.

- **`!!` (non-null assertion)** — Spring Security's `encode()` returns `String?` in Kotlin (due to Java interop), but BCrypt will always return a non-null value. The `!!` asserts this.

## What Changed in `user/build.gradle.kts`

Added the Spring Security starter:

```kotlin
implementation(libs.spring.boot.starter.security)
```

This brings in `spring-security-crypto` which contains `BCryptPasswordEncoder`.

## Why BCrypt?

### The Problem With Plain Text Passwords

If your database is compromised and passwords are stored in plain text, every user's password is immediately exposed. This is catastrophic because users often reuse passwords across services.

### How BCrypt Solves This

BCrypt is a **one-way hash function** — you can go from password to hash, but never from hash to password.

```
"MyPassword123" → encode() → "$2a$10$N9qo8uLO..."  ✅ Easy
"$2a$10$N9qo8uLO..." → ???   → "MyPassword123"     ❌ Impossible
```

### What Makes BCrypt Special

| Feature | Benefit |
|---|---|
| **Automatic salting** | Each password gets a unique random salt, so identical passwords produce different hashes |
| **Configurable cost factor** | The `10` in `$2a$10$...` means 2^10 (1024) rounds of hashing — can be increased as hardware gets faster |
| **Deliberately slow** | Unlike SHA-256 (designed to be fast), BCrypt is intentionally slow to make brute-force attacks impractical |

### BCrypt Hash Anatomy

```
$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
 │  │  │                     │
 │  │  │                     └── Hash output (31 chars)
 │  │  └── Salt (22 chars, Base64-encoded)
 │  └── Cost factor (2^10 = 1024 rounds)
 └── Algorithm version (2a = BCrypt)
```

## How It Will Be Used

When a user **registers**:
```kotlin
val hashedPassword = passwordEncoder.encode(request.password)
val user = UserEntity(
    email = request.email,
    username = request.username,
    hashedPassword = hashedPassword  // Store the hash, never the raw password
)
userRepository.save(user)
```

When a user **logs in**:
```kotlin
val user = userRepository.findByEmail(request.email)
if (user != null && passwordEncoder.matches(request.password, user.hashedPassword)) {
    // Login successful
} else {
    // Invalid credentials
}
```

## Package Structure

```
user/src/main/kotlin/
└── com/danzucker/chirp/
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
            └── PasswordEncoder.kt          (NEW)
```
