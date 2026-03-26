# Section 5.3 - User Registration Service Logic

## What This Section Teaches

This section implements the **service layer** — the business logic for user registration. The `AuthService` orchestrates the registration flow: checking for duplicate users, hashing the password, saving to the database, and returning a domain model. It also introduces a **custom exception** and an **infrastructure-layer mapper** (entity → domain model).

## What Was Created

### `AuthService.kt` — Registration Business Logic

```kotlin
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {

    fun register(email: String, username: String, password: String): User {
        val user = userRepository.findByEmailOrUsername(
            email = email.trim(),
            username = username.trim()
        )
        if (user != null) {
            throw UserAlreadyExistsException()
        }

        val savedUser = userRepository.save(
            UserEntity(
                email = email.trim(),
                username = username.trim(),
                hashedPassword = passwordEncoder.encode(password)
            )
        ).toUser()

        return savedUser
    }
}
```

**Key concepts:**

- **`@Service`** — A Spring stereotype annotation (specialization of `@Component`). It marks this class as a service-layer bean, making it available for dependency injection. Functionally identical to `@Component`, but communicates intent — this class contains business logic.

- **Constructor injection** — `UserRepository` and `PasswordEncoder` are injected via the constructor. Spring automatically resolves and provides these dependencies. No `@Autowired` needed when there's a single constructor.

- **`trim()`** — Removes leading/trailing whitespace from email and username. Prevents issues like `" user@test.com"` and `"user@test.com"` being treated as different users.

- **Duplicate check** — Uses `findByEmailOrUsername()` to check if either the email or username is already taken. If found, throws `UserAlreadyExistsException` rather than letting the database throw a constraint violation (which would be a less informative error).

- **Registration flow:**
  1. Check for existing user with same email OR username
  2. Hash the raw password with BCrypt
  3. Create and save a `UserEntity`
  4. Convert the saved entity to a domain `User` model via `.toUser()`
  5. Return the domain model

### `UserAlreadyExistsException.kt` — Custom Domain Exception

```kotlin
class UserAlreadyExistsException : RuntimeException(
    "A user with this username or email already exists."
)
```

**Key concepts:**

- **Domain exception** — Lives in `domain/exception/` because it represents a business rule violation (not a technical error). The domain knows that duplicate users aren't allowed.

- **Extends `RuntimeException`** — Unchecked exception, so callers aren't forced to catch it. Later, a global exception handler will catch this and return a proper HTTP 409 (Conflict) response.

### `UserMappers.kt` (Infrastructure Layer) — Entity to Domain Mapper

```kotlin
fun UserEntity.toUser(): User {
    return User(
        id = id!!,
        username = username,
        email = email,
        hasEmailVerified = hasVerifiedEmail
    )
}
```

**Key concepts:**

- **Two mapper files, different layers:**
  - `api/mappers/UserMappers.kt` — Domain → DTO (for API responses)
  - `infra/database/mappers/UserMappers.kt` — Entity → Domain (for database results) ← **this one**

- **`id!!`** — The entity's `id` is `UserId?` (nullable) because it's null before saving. After `save()`, Hibernate populates it, so `!!` is safe here. The domain `User` requires a non-null `id`.

- **Direction matters** — Data flows: Entity → Domain → DTO. Each mapper handles one conversion step. The infrastructure layer converts database entities to domain models; the API layer converts domain models to DTOs.

## The Registration Flow End-to-End

```
Client POST /register
        │
        ▼
┌─────────────────┐
│ RegisterRequest  │  ← JSON body deserialized (with validation)
│ (email, user,   │
│  password)       │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   AuthService    │  ← Business logic
│   .register()    │
│                  │
│  1. Check dups   │ → findByEmailOrUsername()
│  2. Hash pass    │ → passwordEncoder.encode()
│  3. Save entity  │ → userRepository.save()
│  4. Map to User  │ → .toUser()
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   User (domain)  │  ← Returned to controller (next section)
└─────────────────┘
```

## Package Structure

```
user/src/main/kotlin/
└── com/danzucker/chirp/
    ├── api/
    │   ├── dto/
    │   │   ├── RegisterRequest.kt
    │   │   ├── LoginRequest.kt
    │   │   ├── UserDto.kt
    │   │   └── AuthenticatedUserDto.kt
    │   └── mappers/
    │       └── UserMappers.kt          (Domain → DTO)
    ├── domain/
    │   ├── exception/
    │   │   └── UserAlreadyExistsException.kt   (NEW)
    │   └── model/
    │       ├── User.kt
    │       └── AuthenticatedUser.kt
    ├── infra/
    │   ├── database/
    │   │   ├── entities/
    │   │   │   └── UserEntity.kt
    │   │   ├── mappers/
    │   │   │   └── UserMappers.kt              (NEW - Entity → Domain)
    │   │   └── repositories/
    │   │       └── UserRepository.kt
    │   └── security/
    │       └── PasswordEncoder.kt
    └── service/
        └── auth/
            └── AuthService.kt                  (NEW)
```
