# NearMe deployment — HTTPS + JWT

## Topology

```
Phone (cellular)                          Home network
─────────────────                         ─────────────────────────────────
NearMe app  ──HTTPS──▶  danovich.ddns.net:443  ──HTTP──▶  192.168.0.115:28585
 (release)              (reverse proxy: Caddy/nginx)        (backend container)

Emulator (dev)
─────────────────
NearMe app  ──HTTP──▶  10.0.2.2:28585  ─────────────────▶  backend on the host
 (debug)               (10.0.2.2 = host as seen from the AVD)
```

The backend listens on **28585** (changed from 28085); `docker compose` publishes
that port. TLS is terminated by the reverse proxy; the backend speaks plain HTTP
on the LAN. The proxy preserves the `/api` path prefix, so the backend's
`/api/...` controllers are reached unchanged.

## Reverse proxy

Pick one and run it on the host that fronts the public IP:

- **`Caddyfile`** (recommended): automatic Let's Encrypt certs, zero cert
  management. Needs WAN tcp/80 **and** tcp/443 forwarded to it.
- **`nginx-nearme.conf`**: get a cert with certbot first, then reload nginx.

Both forward `https://danovich.ddns.net/api/*` → `http://192.168.0.115:28585`.

## Authentication (client-credentials JWT)

Every `/api/**` request must carry `Authorization: Bearer <jwt>`, except
`POST /api/auth/token` (the token exchange) and CORS preflight.

Flow: the app posts its shared client secret to `/api/auth/token`, gets a
short-lived HS256 JWT, and sends it on every call. The app refreshes
automatically on a 401.

### Secrets to change for production

These default to dev values — override them (they must agree across sides):

| Where | Setting | Notes |
|-------|---------|-------|
| Backend env | `NEARME_AUTH_CLIENT_SECRET` | the shared client secret |
| Backend env | `NEARME_JWT_SECRET` | HS256 signing key, **≥ 32 bytes** |
| Backend env | `NEARME_JWT_TTL_SECONDS` | token lifetime (default 3600) |
| Android | `AUTH_CLIENT_SECRET` (buildConfigField in `app/build.gradle.kts`) | must equal `NEARME_AUTH_CLIENT_SECRET` |

Set the backend secrets in your shell before `docker compose up` (compose reads
them), e.g.:

```bash
export NEARME_AUTH_CLIENT_SECRET='…long-random…'
export NEARME_JWT_SECRET='…≥32-byte random…'
docker compose up -d --build
```

> Note: this authenticates the **app**, not individual users (the app has no
> accounts yet). Reports are still tagged by the device. Per-user auth can layer
> on top later by issuing user-scoped tokens from a real login.
