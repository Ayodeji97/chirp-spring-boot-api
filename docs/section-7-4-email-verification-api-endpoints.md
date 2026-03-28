# Section 7.4 - Email Verification API Endpoints

## What This Section Teaches

This section wires the email verification system into the API layer and integrates it with the registration and login flows. After this section, registration creates a verification token, login blocks unverified users, and there's an endpoint to verify emails.

## What Was Created

### `EmailNotVerifiedException.kt` — Domain Exception

```kotlin
class EmailNotVerifiedException : RuntimeException(
    "Email is not verified"
)
```

Thrown during login when a user hasn't verified their email yet. Returns HTTP 401.

## What Changed

### `AuthController.kt` — Added Verify Endpoint

```kotlin
@GetMapping("/verify")
fun verifyEmail(
    @RequestParam token: String
) {
    emailVerificationService.verifyEmail(token)
}
```

**Key concepts:**

- **`@GetMapping`** — This is a GET endpoint, not POST. Email verification links are clicked in a browser/email client, which makes GET requests. The URL will be something like `/api/auth/verify?token=abc123...`.

- **`@RequestParam`** — Reads the `token` from the URL query string (not the request body).

- **`EmailVerificationService` injected** — The controller now has two dependencies: `AuthService` and `EmailVerificationService`.

### `AuthService.kt` — Registration Creates Token, Login Checks Verification

**Registration changes:**

```kotlin
@Transactional
fun register(email: String, username: String, password: String): User {
    val trimmedEmail = email.trim()
    // ... check for duplicates ...

    val savedUser = userRepository.saveAndFlush(  // saveAndFlush instead of save
        UserEntity(...)
    ).toUser()

    emailVerificationService.createVerificationToken(trimmedEmail)  // NEW

    return savedUser
}
```

- **`@Transactional`** — Now wraps the entire registration: user save + token creation happen in one transaction. If token creation fails, the user save is rolled back.
- **`saveAndFlush`** — Forces Hibernate to immediately write the user to the database (instead of deferring). This is needed because `createVerificationToken` queries for the user by email — if the user isn't flushed yet, the query won't find it.
- **`trimmedEmail` local variable** — Extracted to avoid calling `trim()` multiple times.

**Login changes:**

```kotlin
if (!user.hasVerifiedEmail) {
    throw EmailNotVerifiedException()
}
```

Replaces the `// TODO: Check for verified email` comment. Unverified users get a 401 with `EMAIL_NOT_VERIFIED`.

### `AuthExceptionHandler.kt` — Added Handler

```kotlin
@ExceptionHandler(EmailNotVerifiedException::class)
@ResponseStatus(HttpStatus.UNAUTHORIZED)
fun onEmailNotVerified(e: EmailNotVerifiedException) = mapOf(
    "code" to "EMAIL_NOT_VERIFIED",
    "message" to e.message
)
```

## Complete Auth API

| Method | Path | Request | Response | Auth? |
|---|---|---|---|---|
| POST | `/api/auth/register` | `RegisterRequest` (body) | `UserDto` | No |
| POST | `/api/auth/login` | `LoginRequest` (body) | `AuthenticatedUserDto` | No |
| POST | `/api/auth/refresh` | `RefreshRequest` (body) | `AuthenticatedUserDto` | No |
| POST | `/api/auth/logout` | `RefreshRequest` (body) | _(empty)_ | No |
| GET | `/api/auth/verify` | `token` (query param) | _(empty)_ | No |

## Registration → Verification → Login Flow

```
1. POST /api/auth/register
   ├─ Save user (hasVerifiedEmail = false)
   ├─ Create verification token
   └─ Return UserDto
       (Later: send email with verification link)

2. GET /api/auth/verify?token=abc123...
   ├─ Look up token in DB
   ├─ Check not used, not expired
   ├─ Mark token as used
   └─ Set user.hasVerifiedEmail = true

3. POST /api/auth/login
   ├─ Check credentials
   ├─ Check hasVerifiedEmail == true  ← blocks if not verified
   └─ Return AuthenticatedUserDto with tokens
```

## Testing

**Register (creates verification token in DB):**
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@test.com","username":"testuser","password":"Password1!"}'
```

**Try to login before verifying (should fail):**
```json
{"code": "EMAIL_NOT_VERIFIED", "message": "Email is not verified"}
```

**Verify (get token from DB for now, email sending comes later):**
```bash
curl http://localhost:8080/api/auth/verify?token=<token-from-db>
```

**Login after verifying (should work):**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@test.com","password":"Password1!"}'
```

> **Note:** Email sending hasn't been implemented yet — for now you'd need to grab the token from the `email_verification_tokens` table in Supabase to test verification.
