# Section 6.7 - Logging Out Users

## What This Section Teaches

This section implements **logout** by deleting the user's refresh token from the database. Since access tokens are stateless (can't be revoked), logout effectively means "stop issuing new access tokens" — the current access token will expire naturally.

## What Changed

### `AuthService.kt` — Added `logout()` Method

```kotlin
@Transactional
fun logout(refreshToken: String) {
    val userId = jwtService.getUserIdFromToken(refreshToken)
    val hashed = hashToken(refreshToken)
    refreshTokenRepository.deleteByUserIdAndHashedToken(userId, hashed)
}
```

**Key concepts:**

- **Extract userId from token** — Uses `jwtService.getUserIdFromToken()` without checking if the token is expired. This is intentional — a user should be able to log out even if their refresh token has expired.

- **Hash and delete** — Hashes the token with SHA-256 (same `hashToken()` utility used everywhere) and deletes the matching record from the database.

- **No error if token doesn't exist** — If the token was already deleted (e.g., user logged out twice), the delete is a no-op. No exception is thrown — this is idempotent behavior.

- **`@Transactional`** — Ensures the delete operation is wrapped in a database transaction.

### `AuthController.kt` — Added Logout Endpoint

```kotlin
@PostMapping("/logout")
fun logout(
    @RequestBody body: RefreshRequest
) {
    authService.logout(body.refreshToken)
}
```

**Key concepts:**

- **Reuses `RefreshRequest` DTO** — No new DTO needed. Logout just needs the refresh token.

- **Returns nothing** — HTTP 200 with no body. The client doesn't need any data back on logout.

- **No `@Valid`** — No validation annotations on `RefreshRequest`, so none needed here.

## How Logout Works

```
Client: POST /api/auth/logout { "refreshToken": "eyJ..." }
           │
           ▼
    ┌─────────────────────────┐
    │ Extract userId from JWT │
    │ Hash the refresh token  │
    │ Delete from DB          │
    └─────────────────────────┘
           │
           ▼
    HTTP 200 (no body)
```

After logout:
- The refresh token is **deleted from the database** — it can no longer be used to get new tokens
- The current **access token is still valid** until it expires (15 min in prod, ~16 hours in dev)
- The user must **login again** to get new tokens

## Why Can't We Revoke Access Tokens?

Access tokens are **stateless** — the server doesn't store them. It only validates the signature and expiry. To "revoke" an access token, you'd need to maintain a blocklist and check it on every request, which defeats the purpose of stateless JWTs.

The tradeoff: keep access tokens **short-lived** (15 minutes) so even if one is compromised, the window of exposure is small.

## Complete Auth API

| Method | Path | Request Body | Response | Auth Required? |
|---|---|---|---|---|
| POST | `/api/auth/register` | `RegisterRequest` | `UserDto` | No |
| POST | `/api/auth/login` | `LoginRequest` | `AuthenticatedUserDto` | No |
| POST | `/api/auth/refresh` | `RefreshRequest` | `AuthenticatedUserDto` | No |
| POST | `/api/auth/logout` | `RefreshRequest` | _(empty)_ | No |

## Testing

**1. Login to get tokens:**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@test.com","password":"Password1!"}'
```

**2. Logout with the refresh token:**
```bash
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"eyJhbGciOi..."}'
```
Response: HTTP 200 (empty body)

**3. Try to refresh with the same token (should fail):**
```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"eyJhbGciOi..."}'
```
Response: HTTP 401 `{"code":"INVALID_TOKEN","message":"Invalid refresh token"}`
