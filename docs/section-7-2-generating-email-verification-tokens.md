# Section 7.2 - Generating Email Verification Tokens

## What This Section Teaches

This section creates a **secure token generator** utility and wires it into the email verification entity so tokens are auto-generated on creation. The token is a cryptographically random string used in verification emails.

## What Was Created

### `TokenGenerator.kt` — Secure Random Token Utility

```kotlin
object TokenGenerator {
    fun generateSecureToken(): String {
        val bytes = ByteArray(32) { 0 }

        val secureRandom = SecureRandom()
        secureRandom.nextBytes(bytes)

        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
    }
}
```

**Key concepts:**

- **`object`** — Kotlin singleton. Unlike a `@Component`, this has no Spring lifecycle — it's a plain utility. Used as `TokenGenerator.generateSecureToken()` without injection.

- **`SecureRandom`** — Java's cryptographically secure random number generator. Unlike `Random`, it uses OS entropy sources (e.g., `/dev/urandom`) and is safe for security-sensitive operations like token generation.

- **`ByteArray(32)`** — 32 bytes = 256 bits of randomness. This is the same strength as an AES-256 key — practically impossible to guess or brute-force.

- **`Base64.getUrlEncoder().withoutPadding()`** — URL-safe Base64 encoding. Uses `-` and `_` instead of `+` and `/`, and no `=` padding. This produces a **43-character string** that's safe to include in URLs (email verification links).

### Why Not UUID?

UUIDs (v4) only have 122 bits of randomness. A 32-byte `SecureRandom` token has 256 bits — more than double. For email verification tokens that might be guessable attack targets, more entropy is better.

## What Changed

### `EmailVerificationTokenEntity.kt` — Auto-generated Token

```kotlin
// Before:
var token: String,

// After:
var token: String = TokenGenerator.generateSecureToken(),
```

Now when you create an entity:
```kotlin
EmailVerificationTokenEntity(
    user = userEntity,
    expiresAt = Instant.now().plusSeconds(3600),
    usedAt = null
)
// token is automatically generated — no need to pass it
```

## How the Token Will Be Used (Preview)

```
1. User registers
   └─→ Create EmailVerificationTokenEntity (token auto-generated)
   └─→ Send email with link: /verify?token=abc123...

2. User clicks link
   └─→ Look up token in DB (findByToken)
   └─→ Check not expired, not already used
   └─→ Set usedAt = now, set user.hasVerifiedEmail = true
```

## Package Structure (New File Only)

```
user/src/main/kotlin/
└── com/danzucker/chirp/
    └── infra/
        └── security/
            ├── PasswordEncoder.kt
            └── TokenGenerator.kt        (NEW)
```
