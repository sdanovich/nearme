# NearMe

Find nearby places by category — gas, coffee, restaurants, hotels, mechanics,
hospitals — near your location. Gas stations show crowdsourced prices; other
categories show rating, distance, and opening hours. A native **Android app**
(Kotlin + Jetpack Compose) scans your location on a timer and talks to a
**Spring Boot backend** that does nearest-place geo-queries with **PostGIS**.
Prices are **crowdsourced**.

The data model is **generalized** (a `Place` has a `category`), so the same
search / cache / history machinery can serve coffee shops or restaurants later
— only `GAS` is wired into the UI today.

## Key features

- **Run / Pause scanning** — a foreground service scans every 60 seconds while
  running; pause stops it. Runs even when the app is backgrounded (with caveats —
  see below).
- **Local cache (Room)** — station locations and observed prices are cached on
  the phone, so the list shows instantly on launch and works offline; it refreshes
  as scans complete.
- **Price history** — every report is kept server-side; the app shows a station's
  history (from the backend, falling back to the local cache offline).
- **Generalized backend** — `place` + `category` and a generic `price_type`, ready
  for new categories with no structural change.

## Why crowdsourced prices

There is no genuinely free, real-time, station-level gas price API for the US.
GasBuddy has the data but no open API; commercial feeds (Zyla, GlobalPetrolPrices)
are paid and often only state-level. So prices here come from user reports, which
is free, works anywhere, and is how GasBuddy itself stays current. The trade-off:
coverage is sparse until people report. Seed data for a few Miami stations is
included so the app shows something on first run. The schema is designed so a paid
feed could be added later as another price source.

## Architecture

```
  Android app (Kotlin/Compose)
        │  GPS (FusedLocationProvider)
        │  GET /api/stations/nearby?lat=&lon=&fuel=
        ▼
  Spring Boot backend ──── PostGIS nearest-neighbor query (ST_DWithin / ST_Distance)
        │                         │
        │  POST /api/prices       ▼
        └──────────────────►  PostgreSQL + PostGIS
                              (gas_station w/ geography point, price_report)
```

Two containers: **postgres** (PostGIS image) and **backend**. The Android app
runs on a device/emulator and points at the backend.

## Backend

### Run

```bash
docker compose up --build
```

PostGIS extension, tables, a GiST spatial index, and seed stations are created by
Flyway migration V1. Hibernate runs in `validate` mode — Flyway owns the schema.

### Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/stations/nearby?lat=&lon=&category=GAS&priceType=REGULAR&radiusMeters=8000&limit=20` | Nearest places of a category + latest price |
| GET | `/api/stations/{id}/history?priceType=REGULAR` | Price history for a place, newest first |
| POST | `/api/stations` | Add a place not yet in the DB |
| POST | `/api/prices` | Submit a crowdsourced price report |

The schema is owned by Flyway: **V1** creates PostGIS + the original tables;
**V2** generalizes `gas_station`→`place` (+ `category`) and `price_report`
columns (`station_id`→`place_id`, `fuel_type`→`price_type`).

Try it:

```bash
curl "http://localhost:28085/api/stations/nearby?lat=25.77&lon=-80.19&fuel=REGULAR"

curl -X POST http://localhost:28085/api/prices \
  -H "Content-Type: application/json" \
  -d '{"stationId":1,"fuelType":"REGULAR","price":3.29,"reporterId":"me"}'
```

### How "nearest" works

`gas_station.location` is a PostGIS `geography(Point,4326)`. The nearby query uses
`ST_DWithin` (index-assisted radius filter) plus `ST_Distance` (ordering), both on
geography so distances are in meters. A lateral join pulls each station's most
recent non-stale price for the requested fuel.

## Android app

Kotlin + Jetpack Compose, in `android/`. Open it in Android Studio.

- Requests location permission, gets a fix via `FusedLocationProviderClient`.
- Calls the backend, lists stations sorted by distance with price and a fuel-type
  filter, and lets users submit a price (the crowdsourcing loop).

### Local caching with Room (offline-first)

The app uses a **Room** database on the device as a read cache, with **Postgres
as the single source of truth**. The data flow is one-directional for reads:

```
Postgres (backend, source of truth)
   │  refresh() reads /api/stations/nearby
   ▼
Room (on-device cache)  ──observed as a Flow──►  UI
```

- The UI **only ever reads from Room** (a `Flow`, so it paints instantly on open —
  even with no signal — and updates the moment fresh data lands).
- `refresh()` fetches from the backend and **writes through to Room**. It never
  writes to Postgres. A failed refresh leaves the last cached list on screen with
  a small "showing cached data" note.
- Each refresh **replaces** the cached set for that fuel, so stale stations from a
  previous location don't accumulate.
- The **one** path that writes to Postgres is an explicit user **price report**
  (`reportPrice`). Caching does not enrich Postgres — only deliberate reports do.

Because a stale *gas price* is genuinely misleading (unlike a stale station
location), every card shows how old its price is ("12m ago", "3h ago", "no recent
price"), and the backend's `maxAgeHours` filter keeps very old prices out entirely.

Architecture: `NearMeApp` builds the Room DB + repository singletons;
`StationRepository` is the single source of truth the `StationsViewModel` talks to;
the `Composable` UI observes the repository's Room `Flow`.

### Pointing the app at the backend

`app/build.gradle.kts` sets `API_BASE_URL`:
- **Default (real phone):** `http://danovich.ddns.net:28085/` — the backend runs
  at home and is reached over DDNS, so the phone can connect from any network. The
  home router must forward WAN port `28085` to the host running `docker compose`.
- **Local emulator testing:** override with `http://10.0.2.2:28085/` (`10.0.2.2`
  is the host machine as seen from the Android emulator).

> The app sets `usesCleartextTraffic="true"` because the backend is plain HTTP.
> Exposing it over DDNS means it is reachable from the public internet without TLS
> or auth — fine for a personal dev setup, but put it behind HTTPS (and ideally a
> reverse proxy / auth) before treating it as anything but throwaway.

## Seeding real stations (optional)

The seed data is a handful of Miami stations. To populate real stations for an
area, you can import from OpenStreetMap (free) — query Overpass for
`amenity=fuel` nodes and POST them to `/api/stations`. (Not automated here to keep
the build self-contained; the `osm_id` column exists for de-duping such imports.)

## Honest limitations

- **Background scanning is throttled by Android.** The foreground service keeps
  the 60s scan running when the app is backgrounded, but many OEMs (Samsung
  included) aggressively battery-optimize background location. For reliable
  background scanning, the user must exempt the app from battery optimization
  (Settings → Apps → NearMe → Battery → Unrestricted). Even then, Android may
  defer location updates when the screen is off — this is an OS policy, not a bug.
  Background location also requires the user to grant "Allow all the time" (not
  just "While using") in the location permission prompt.
- **Prices depend on users.** Early on, most stations show "—" (no recent price).
  Inherent to crowdsourcing.
- **No accounts/auth in v1.** Reports carry an opaque device id. Add auth + rate
  limiting + outlier rejection before any real deployment.
- Seed prices are illustrative, not live.

## Reusing for coffee / restaurants later

The backend is already category-agnostic: `Place.category` + a generic
`price_type`, and the nearby/history endpoints take a `category` param. To add
coffee shops you'd: seed/import `COFFEE` places, pass `category=COFFEE` from the
app, and add a category switch to the UI. No schema or query changes needed.

## Statistical outlier detection (domain-agnostic)

Crowdsourced data needs protection from bad entries (a fat-fingered $33/gal, a
malicious $0.30). The backend includes a reusable outlier detector in
`com.example.nearme.outlier` that is **not tied to gas** — it judges any
numeric value against a sample of comparable values.

### Components

- `OutlierDetector` — the pure-math core. Two robust methods: **MAD** (median
  absolute deviation; modified z-score, good default for small noisy samples) and
  **IQR** (box-plot whisker rule). No model, no external calls, instant.
- `OutlierConfig` — method, threshold, minimum sample size, and optional hard
  absolute floor/ceiling (e.g. gas price must be > 0 and < $20).
- `OutlierResult` — verdict: outlier flag, score, computed bounds, reason.
- `OutlierGuard` — convenience facade most callers use.
- `IntegrationExample` — documented examples for gas, rents, and sensor data.

### How "works for anything" is achieved

The statistics are identical across domains; only two things change per domain:
1. **How you gather the comparable sample** (a query over the right grouping —
   e.g. recent prices for *this station + fuel*). Domain-specific, stays in the
   domain's repository.
2. **What you do with the verdict** (reject, flag, accept). Domain-specific.

Everything between — the actual anomaly judgement — is shared. To use it for a new
domain (rents, sensor readings, anything numeric), gather a `double[]` sample and
call `guard.check(candidate, sample, config)`. No change to the detector.

### Where it's wired in

`StationService.reportPrice` gathers the last 14 days of prices for the place +
fuel, then calls the guard with hard bounds (0.10–20.00) and a relaxed MAD
threshold. Impossible values (outside the absolute bounds) are rejected;
statistically suspicious-but-possible values are accepted (and could be flagged
for review). Tune the threshold to taste — tighter catches more, but risks
rejecting legitimate price swings.

## Error event pipeline (publish to broker, not direct-to-DB)

App exceptions and failed requests are captured as events, published to a
**Redis Stream**, and consumed into a Postgres `error_event` table for analytics.
Errors are NOT written directly to the DB by the failing request — that would
make error capture depend on the DB (often the thing that's broken) and add write
load during incidents.

### The safety discipline (why this is robust)

```
request fails
   │
   ├─► structured log to stdout     ALWAYS, synchronous, can't fail meaningfully
   │
   └─► ErrorEvent → Redis Stream    async, fire-and-forget, best-effort
                       │
                       ▼
                 consumer ──► Postgres error_event table   (decoupled, off the
                                                            request path)
```

- **Always log locally first.** `ErrorPublisher.capture()` writes to the log
  before anything else. The log is the floor that never depends on external infra.
- **Broker publish is async + best-effort.** It runs on a small bounded thread
  pool (`errorPublisherExecutor`); under flood it discards rather than blocks. A
  broker failure is swallowed and logged locally — it can never become a new error
  in the error path, and never turns into a user-facing 500.
- **Persistence is decoupled.** The consumer lands events in Postgres and acks
  only after success; if the DB is slow/down, only the consumer waits — the app
  keeps serving, and unacked events stay pending for retry.

This satisfies both instincts that led here: the data is **queryable for
analysis** (it lands in Postgres) and the app stays **available** even when the
broker or DB is partitioned away (publishing never blocks the request).

### Capture

A `@RestControllerAdvice` (`GlobalExceptionHandler`) catches exceptions across all
controllers, builds an `ErrorEvent` (id, time, method, path, exception, trimmed
message + stack, optional `X-Trace-Id`), captures it, and still returns a clean
error response to the client.

### Analytics endpoints (http://localhost:28085)

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/errors/recent` | Last 100 errors, newest first |
| GET | `/api/errors/by-path?hours=24` | Which endpoints fail most |
| GET | `/api/errors/by-exception?hours=24` | What's breaking, by exception type |

### Note on scope

This is a pragmatic in-house pipeline. For high-volume production error
observability you'd typically also ship the stdout logs to Loki/ELK or use Sentry;
the broker→table pipeline here is for queryable, app-specific error analytics, and
the stream is capped (`MAXLEN ~50000`) so it can't grow unbounded.

## Categories (multi-category places finder)

The app finds six categories of place; the user picks **one at a time**:
**gas, coffee, restaurant, hotel, mechanic, hospital**.

- **Gas** is the only category with a crowdsourced **price** (and a fuel-grade
  selector + price reporting). This is by design: a single $/gallon number is
  genuinely crowdsourceable, whereas "the price" of a hospital or restaurant is
  not. Forcing a price onto those would be meaningless.
- **All other categories** show **rating, distance, and opening hours** instead.
  Rating is shown when available.

### Run / Pause

A **Run/Pause** control in the top bar toggles periodic auto-refresh (every 60s)
for the selected category. Manual refresh is always available too. Switching
category or fuel grade triggers an immediate refresh.

### Data source for non-gas places

Place data (locations, ratings, hours) is designed to come from
**OpenStreetMap / Overpass** (free, no API key) — chosen over Google Places to
keep the project free and self-hostable. The honest tradeoff: OSM ratings are
sparse, so rating is shown only when present; distance and hours always work.
Seed data for all six categories (Miami-area) is loaded by Flyway migration V4 so
the app shows results immediately; wiring a live Overpass import is the path to
real coverage (the `osm_id` column de-dupes such imports).

### Backend changes for categories

- `PlaceCategory` enum expanded to six values, with `hasPrice()` (gas only).
- `Place` gains `rating` and `openingHours` columns (Flyway V4).
- The nearby query returns rating + hours; price is still LEFT-joined and only
  populated for gas (other categories simply have no price reports).
- `GET /api/stations/nearby?lat=&lon=&category=COFFEE` — category is the selector.
# nearme
