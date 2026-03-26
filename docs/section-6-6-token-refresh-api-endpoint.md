# Section 6.6 - Token Refresh API Endpoint

## What This Section Teaches

This section exposes the token refresh logic (from Section 6.5) as a REST endpoint. Clients can now exchange an expiring refresh token for a new access + refresh token pair by calling `POST /api/auth/refresh`.

## What Was Created

### `RefreshRequest.kt` — Request DTO

```kotlin
data class RefreshRequest(
    val refreshToken: String
)
```

A simple DTO with just the refresh token string. No validation annotations — the service layer handles token validation via JWT parsing and database lookup.

## What Changed

### `AuthController.kt` — Added Refresh Endpoint

```kotlin
@PostMapping("/refresh")
fun refresh(
    @RequestBody body: RefreshRequest
): AuthenticatedUserDto {
    return authService
        .refresh(body.refreshToken)
        .toAuthenticatedUserDto()
}
```

**Flow:** Client sends refresh token → `AuthService.refresh()` validates, rotates, and generates new tokens → returns `AuthenticatedUserDto` with new pair.

## Complete Auth API

| Method | Path | Request Body | Response | Auth Required? |
|---|---|---|---|---|
| POST | `/api/auth/register` | `RegisterRequest` | `UserDto` | No |
| POST | `/api/auth/login` | `LoginRequest` | `AuthenticatedUserDto` | No |
| POST | `/api/auth/refresh` | `RefreshRequest` | `AuthenticatedUserDto` | No |

All three endpoints are under `/api/auth/**` which is permitted by `SecurityConfig`.

## Testing the Refresh Flow

**1. Login to get tokens:**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@test.com","password":"Password1!"}'
```

**2. Use the refresh token to get new tokens:**
```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"eyJhbGciOi..."}'
```

**Successful response (200):**
```json
{
  "user": { "id": "...", "email": "...", "username": "...", "hasVerifiedEmail": false },
  "accessToken": "eyJ...(new)...",
  "refreshToken": "eyJ...(new)..."
}
```

**Using the same refresh token again (401):**
```json
{"code": "INVALID_TOKEN", "message": "Invalid refresh token"}
```

The old refresh token was deleted after first use (token rotation), so reusing it fails.

## Package Structure (New File Only)

```
user/src/main/kotlin/
└── com/danzucker/chirp/
    └── api/
        ├── controllers/
        │   └── AuthController.kt        (MODIFIED - added refresh endpoint)
        └── dto/
            └── RefreshRequest.kt         (NEW)
```
