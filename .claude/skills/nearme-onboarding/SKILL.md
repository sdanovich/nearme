---
name: nearme-onboarding
description: Orientation and working guide for the NearMe project — a multi-module app with a Spring Boot plus PostGIS backend, an Android (Kotlin/Jetpack Compose) client, a Redis-Streams error pipeline, and a domain-agnostic statistical outlier detector. Use this skill whenever working in the NearMe repo in IntelliJ or Android Studio — building or running either module, adding a place category, touching the nearby-search query, the price-report or outlier path, the Room cache, or the error pipeline, and ESPECIALLY before claiming any code compiles, since this project was assembled in a sandbox that could not run Gradle, so a real build is required to catch drift. Trigger this even if the user just says "build it", "run the backend", "does this compile", "add a category", or "fix the Android app", as long as the NearMe repo is open.
---

# NearMe — project working guide

NearMe is a "nearby places finder." A native **Android app** reads GPS and
calls a **Spring Boot backend** that does geospatial nearest-neighbor search with
**PostGIS**. The user picks one of six categories at a time (gas, coffee,
restaurant, hotel, mechanic, hospital). **Gas** carries a crowdsourced price;
other categories show rating, distance, and opening hours.

## Most important thing to know first

This codebase was assembled across many iterations in an environment that
**could not run Gradle, the Android SDK, or Docker**. Validation so far has been
brace-balance checks and Python ports of the math — NOT real compilation. As a
result the code may contain **drift**: places where an entity, DTO, query, or
method signature was changed in one layer but a caller in another layer wasn't
fully updated.

Therefore: **the single most valuable thing to do in IntelliJ is a real build of
each module, then fix whatever the compiler surfaces.** Do not tell the user
something compiles until you have actually run the build. When you find a
mismatch, fix it by making the layers agree — see "Known drift hot-spots" below
for where to look first.

## Repository layout

```
nearme/
├── docker-compose.yml         # postgres (PostGIS) + redis + backend
├── backend/                   # Spring Boot, Java 21, Gradle
│   └── src/main/java/com/example/nearme/
│       ├── model/             # Place, PlaceCategory, PriceReport, FuelType
│       ├── repository/        # PlaceRepository (native PostGIS query), PriceReportRepository
│       ├── service/           # StationService (nearby, createStation, reportPrice)
│       ├── controller/        # StationController (/api/stations/*, /api/prices)
│       ├── dto/               # NearbyStationResponse, request DTOs, PricePoint
│       ├── outlier/           # domain-agnostic outlier detector (pure math)
│       ├── errors/            # Redis-Streams error pipeline (publisher→consumer→table)
│       └── config/            # GeometryConfig (JTS GeometryFactory)
│   └── src/main/resources/
│       ├── application.properties
│       └── db/migration/      # Flyway V1–V4
└── android/                   # Kotlin, Jetpack Compose
    └── app/src/main/java/com/example/nearme/
        ├── NearMeApp.kt    # Application; builds Room DB + repository singletons
        ├── MainActivity.kt    # Compose UI: category selector, run/pause, cards
        ├── data/              # NearMeApi (Retrofit), ApiClient, StationRepository
        │   └── local/         # Room: CachedPlace entity, PlaceDao, AppDatabase
        └── ui/                # StationsViewModel
```

## Backend: build and run

Java 21 + Gradle. From `backend/`:

```bash
./gradlew build          # compile + tests — RUN THIS FIRST to surface drift
./gradlew bootRun        # run locally (needs Postgres+Redis reachable)
```

Full stack (Postgres with PostGIS, Redis, backend) via Docker from the repo root:

```bash
docker compose up --build
```

Notes that matter:
- **PostGIS is required** — the image is `postgis/postgis`, not plain `postgres`.
  The nearby query uses `ST_DWithin`/`ST_Distance` on a `geography(Point,4326)`
  column. Flyway V1 runs `CREATE EXTENSION postgis`.
- **Flyway owns the schema**; Hibernate is `ddl-auto=validate`. If you change an
  entity, add a migration (V5, V6, …) — do not rely on Hibernate to alter tables.
- **Redis** backs the error pipeline (`stream:errors`). The app must still start
  and serve if Redis is down — publishing is best-effort (see errors section).

## Android: build and run

Open `android/` in Android Studio (or build from CLI):

```bash
cd android
./gradlew assembleDebug
```

- `API_BASE_URL` is a `buildConfigField` in `app/build.gradle.kts`. Default
  `http://danovich.ddns.net:28085/` points a real phone at the home backend over
  DDNS (router forwards WAN 28085 → the `docker compose` host). For local emulator
  testing, override with `http://10.0.2.2:28085/` (host as seen from the emulator).
  The backend listens on **28085** (`server.port`, also the compose-published port).
- Uses **KSP** for Room codegen. Room is the most likely place a real build
  surfaces an error the sandbox could not (the annotation processor runs only at
  compile time). If the Room schema looks off, this is the first suspect.
- The DB is currently version 2 with `fallbackToDestructiveMigration()`, so a
  schema bump wipes the local cache rather than crashing — fine for dev.

## How the core flow works (so changes stay consistent)

**Nearby search.** `StationController.nearby` → `StationService.findNearby` →
`PlaceRepository.findNearby` (native PostGIS query). The query LEFT JOINs the
latest non-stale price, which is only populated for gas; other categories return
null price and instead carry `rating` + `openingHours`. The query's selected
columns must match the `NearbyPlaceRow` projection interface AND the
`NearbyStationResponse` DTO AND the Android `NearbyPlace` model — if you add a
field, update all four.

**Price reporting + outlier guard.** `reportPrice` gathers recent comparable
prices and runs them through `outlier/OutlierGuard`. Values outside hard bounds
are rejected; statistically-suspicious-but-possible values are accepted. The
outlier package is **domain-agnostic** (takes a `double[]` sample, knows nothing
about gas) — see `outlier/IntegrationExample.java`. The math was validated by a
Python port; trust it, but the wiring still needs a real compile.

**Room cache (offline-first).** Postgres is the source of truth. The Android
repository's `refresh()` reads the backend and writes through to Room; the UI
observes Room as a `Flow`. Reads never write to Postgres — only `reportPrice`
(gas) does. Each refresh replaces that category's cached set.

**Error pipeline.** `GlobalExceptionHandler` captures exceptions → `ErrorPublisher`
ALWAYS logs locally first, then fire-and-forget publishes to a Redis Stream →
`ErrorStreamConsumer` lands events in the `error_event` table → analytics at
`/api/errors/*`. The rule: error capture must never break a request or block on
the broker. Preserve that discipline if you touch this.

## Known drift hot-spots (check these first when builds fail)

The naming carries history: the app began gas-specific ("Station") and was
generalized to "Place" + category. Both vocabularies linger. When the compiler
complains, it's usually one of these:

1. **Station vs Place naming.** Backend largely uses `Place`/`PlaceCategory`, but
   some class/field/DTO names still say "Station" (e.g. `NearbyStationResponse`,
   `StationController`, `StationService`, `StationRepository` on the Android
   side). Don't mass-rename — just make callers consistent with whatever the
   definition actually is.
2. **The four-place field chain for nearby results.** A field must exist in: the
   SQL `SELECT`, the `NearbyPlaceRow` projection, `NearbyStationResponse`, and the
   service mapping that constructs it. Android then needs it in `NearbyPlace` and
   `CachedPlace`. Add-a-field changes commonly miss one.
3. **`category` vs `fuel`/`priceType`.** The nearby endpoint takes `category` plus
   a gas-only `priceType` (the fuel grade). Earlier code sometimes used `fuel`.
   Make the Android `NearMeApi` query params match the backend `@RequestParam` names.
4. **Room layer.** `CachedPlace` is keyed by `(stationId, category)`; the DAO is
   `PlaceDao`; `NearMeApp` must call `database.placeDao()`. Older code said
   `CachedStation`/`StationDao`/`stationDao()`.
5. **Flyway vs entity.** If `Place` has a field with no column (or vice-versa),
   `ddl-auto=validate` fails at startup. Reconcile entity ↔ migration.

When you fix drift, verify the **whole chain** compiles, not just the file you
touched.

## Adding a new place category (worked example)

1. Add the value to `model/PlaceCategory` (and `hasPrice()` if it should carry a
   price — only gas does today).
2. Add seed rows in a **new** Flyway migration (follow V4's pattern;
   `ST_MakePoint(lon, lat)`).
3. Android: add the value to `StationsViewModel.CATEGORIES` and a label in
   `MainActivity`'s `CategorySelector`. No backend query change is needed — the
   nearby query is already category-parameterized.
4. Build both modules and confirm the new category returns seeded results.

## When the user asks "does this compile?" or "build it"

Actually run the build (`./gradlew build` in `backend/`, `./gradlew assembleDebug`
in `android/`). Report real compiler output. If it fails, fix the drift (above),
re-run, and only then confirm. If the user is offline from Postgres/Redis, you can
still compile; running needs the services (use `docker compose up`).
