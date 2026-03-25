# Section 4.1 - User Domain Models

## What This Section Teaches

This is the start of the **User Service** module. It introduces the concept of **domain models** — plain Kotlin data classes that represent the core business entities of your application, independent of any framework (no Spring annotations, no database annotations).

## What Was Created

### `User.kt` — The Core User Model

```kotlin
package com.danzucker.user.domain.model

import java.util.UUID

typealias UserId = UUID

data class User(
    val id: UserId,
    val username: String,
    val email: String,
    val hasEmailVerified: Boolean
)
```

**Key concepts:**

- **`typealias UserId = UUID`** — Creates a type alias so you can write `UserId` instead of `UUID` throughout your code. This makes the code more readable and expressive. If you ever need to change the ID type (e.g., from UUID to Long), you only change it here.

- **`data class`** — Kotlin data classes automatically generate `equals()`, `hashCode()`, `toString()`, and `copy()` methods. Perfect for domain models that are primarily data holders.

- **No annotations** — This is a pure domain model. It doesn't know about databases, HTTP, or Spring. This is intentional — it follows **Clean Architecture** where the domain layer has zero dependencies on frameworks.

- **`val` (immutable)** — All properties are `val` (read-only), making the User object immutable. This is a best practice for domain models — you create new instances via `copy()` rather than mutating existing ones.

### `AuthenticatedUser.kt` — Authenticated Session Model

```kotlin
package com.danzucker.user.domain.model

data class AuthenticatedUser(
    val user: User,
    val accessToken: String,
    val refreshToken: String
)
```

**Key concepts:**

- **Composition over inheritance** — Instead of extending `User`, this class *contains* a `User`. This is a cleaner design because an `AuthenticatedUser` isn't a type of user — it's a user *with* authentication tokens.

- **Access + Refresh tokens** — This represents the JWT-based auth pattern:
  - **Access token**: Short-lived token sent with every API request to prove identity
  - **Refresh token**: Longer-lived token used to get a new access token when the old one expires

## What Changed in `user/build.gradle.kts`

Added a dependency on the `common` module:

```kotlin
dependencies {
    implementation(projects.common)
    // ...
}
```

This means the user module can use shared code from the common module (utility classes, shared types, etc. that will be added later).

## Package Structure

```
user/src/main/kotlin/
└── com/danzucker/user/
    └── domain/
        └── model/
            ├── User.kt
            └── AuthenticatedUser.kt
```

This follows a **layered package structure**:
- `domain/` — Business logic and models (framework-independent)
- `domain/model/` — Data classes representing business entities

Later sections will add more layers:
- `data/` — Database entities, repositories, mappers
- `presentation/` — API controllers, request/response DTOs
- `service/` — Business logic services

## Why Domain Models Are Separate from Database Entities

You'll notice these models have no `@Entity`, `@Table`, or `@Column` annotations. Later, Philipp will create separate **JPA entity classes** in the `data` layer that map to database tables. The domain models and database entities are kept separate because:

1. **Your domain shouldn't depend on your database** — If you switch from PostgreSQL to MongoDB, only the data layer changes
2. **Different shapes** — The database might store data differently than how your app thinks about it
3. **Testability** — Domain models are trivial to test since they have no framework dependencies
