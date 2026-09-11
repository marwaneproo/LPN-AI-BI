# Authentication architecture

The active authentication path is owned by `services-java/llm-orchestrator`. The older
Keycloak/YARP entries in planning documents describe a possible future deployment, not the
current runtime.

## Runtime flow

1. The React application calls `/api/v1/auth/login` or `/api/v1/auth/signup` through the
   same-origin Vite proxy in local development.
2. Spring validates and normalizes the professional identifier. New passwords are encoded
   with BCrypt. Valid legacy PBKDF2 hashes are upgraded after the next successful login.
3. A successful login rotates the user's sessions and returns an opaque 256-bit token only
   in an `HttpOnly`, `SameSite=Strict` cookie. PostgreSQL stores only its SHA-256 digest.
4. `ApiSecurityFilter` requires an approved session for all other `/v1/**` routes. Mutating
   authenticated requests must also send `X-LPN-Request: web`.
5. Logout revokes the server-side session and expires the browser cookie. The frontend never
   stores an authentication token in web storage.

The session is the long-lived credential; this design does not use an access/refresh JWT
pair. Standard sessions expire after 12 hours. “Rester connecté” sessions expire after 30
days. A new login revokes earlier sessions for that user.

## First administrator

Set these values before starting the orchestrator for the first time:

```dotenv
AUTH_BOOTSTRAP_ADMIN_USERNAME=admin
AUTH_BOOTSTRAP_ADMIN_PASSWORD=<unique password of at least 12 characters>
AUTH_COOKIE_SECURE=false
```

Set `AUTH_COOKIE_SECURE=true` behind an HTTPS gateway. When a bootstrap password is present,
startup creates or rotates that administrator without logging the password. With no password,
existing administrators remain unchanged and no default account is created. The historical
`admin/admin` credential is explicitly rejected.

## Persistence and compatibility

`infra/postgres/init/00-roles-and-schemas.sql` creates the user/session tables for new
volumes. `AuthRepository.ensureTable()` also applies additive columns and indexes to existing
development volumes. Existing plaintext UUID session rows are not accepted by the new lookup;
users authenticate again and receive a hashed opaque session.

## Operational limits

Login and signup throttling is in-memory and bounded by the remote address, which matches the
current single-node deployment. A multi-instance deployment must move these counters to a
shared store or enforce equivalent limits at the gateway. Password reset and enterprise SSO
also require mail/identity-provider infrastructure and are not simulated in this codebase.
