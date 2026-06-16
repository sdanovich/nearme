package com.example.nearme.repository;

import com.example.nearme.model.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    Optional<Place> findByOsmId(Long osmId);

    /** Of the given OSM ids, which we already store — used to insert only new ones. */
    @Query("SELECT p.osmId FROM Place p WHERE p.osmId IN :ids")
    List<Long> findExistingOsmIds(@Param("ids") Collection<Long> ids);

    /**
     * Insert a discovered OSM place, ignoring it if its osmId is already stored.
     * The {@link #findExistingOsmIds} pre-filter skips most of these, but two
     * concurrent discovery runs over the same area can both pass that check and
     * race to insert the same osmId; ON CONFLICT makes the loser a no-op instead
     * of raising a constraint violation that would poison the whole transaction
     * and fail the nearby request. Nullable text/number params are cast so
     * Postgres can infer their type when bound null. Returns rows inserted (0/1).
     */
    @Modifying
    @Query(value = """
            INSERT INTO place (category, osm_id, name, brand, address, location, created_at, rating, opening_hours)
            VALUES (
                CAST(:category AS varchar),
                :osmId,
                CAST(:name AS text),
                CAST(:brand AS text),
                CAST(:address AS text),
                ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                COALESCE(CAST(:createdAt AS timestamptz), now()),
                CAST(:rating AS double precision),
                CAST(:openingHours AS text)
            )
            ON CONFLICT (osm_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("category") String category,
                       @Param("osmId") Long osmId,
                       @Param("name") String name,
                       @Param("brand") String brand,
                       @Param("address") String address,
                       @Param("lon") double lon,
                       @Param("lat") double lat,
                       @Param("createdAt") Instant createdAt,
                       @Param("rating") Double rating,
                       @Param("openingHours") String openingHours);

    /**
     * Ids of the nearest GAS places within radius that have NO recent price for
     * the given type — candidates to seed with an external average price.
     */
    @Query(value = """
            SELECT p.id
            FROM place p
            WHERE p.category = 'GAS'
              AND ST_DWithin(p.location, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :radiusMeters)
              AND NOT EXISTS (
                  SELECT 1 FROM price_report pr
                  WHERE pr.place_id = p.id
                    AND pr.price_type = :priceType
                    AND pr.reported_at > now() - (:maxAgeHours || ' hours')::interval
              )
            ORDER BY ST_Distance(p.location, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography) ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findGasIdsMissingRecentPrice(@Param("lon") double lon,
                                            @Param("lat") double lat,
                                            @Param("radiusMeters") double radiusMeters,
                                            @Param("priceType") String priceType,
                                            @Param("maxAgeHours") int maxAgeHours,
                                            @Param("limit") int limit);

    /**
     * Nearest places of a category within {radiusMeters} that still have no
     * address — candidates to backfill via reverse geocoding so coverage fills
     * in over repeated requests (OSM often stores POIs with no addr:* tags).
     */
    @Query(value = """
            SELECT p.*
            FROM place p
            WHERE p.category = :category
              AND p.address IS NULL
              AND ST_DWithin(p.location, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :radiusMeters)
            ORDER BY ST_Distance(p.location, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography) ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Place> findMissingAddressNear(@Param("lon") double lon,
                                       @Param("lat") double lat,
                                       @Param("category") String category,
                                       @Param("radiusMeters") double radiusMeters,
                                       @Param("limit") int limit);

    /**
     * Nearest places of a category within {radiusMeters} of (lon,lat), with a
     * gas price computed crowd-first per station. PostGIS: ST_DWithin filters by
     * radius (index-assisted), ST_Distance orders.
     *
     * The price (GAS only) cascades, newest-comparable wins:
     *   1. median of THIS station's own recent non-stale user reports
     *      (excludes the EIA seed) -> a real crowdsourced price ('crowd');
     *   2. else the median of recent user reports at OTHER gas stations within
     *      {estimateRadiusMeters} -> a local crowd estimate ('crowd-local');
     *   3. else the seeded EIA regional average ('eia-avg') -> regional estimate.
     * Medians are robust to the suspicious-but-accepted reports the report-time
     * OutlierGuard lets through. Rounded to mills (3 dp) to keep the $x.xx9 form.
     */
    @Query(value = """
            SELECT p.id              AS id,
                   p.name            AS name,
                   p.brand           AS brand,
                   p.address         AS address,
                   p.rating          AS rating,
                   p.opening_hours   AS openingHours,
                   ST_Y(p.location::geometry) AS lat,
                   ST_X(p.location::geometry) AS lon,
                   ST_Distance(p.location, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography) AS distance_m,
                   lp.price          AS price,
                   lp.reported_at    AS price_reported_at,
                   lp.reporter       AS price_reporter
            FROM place p
            LEFT JOIN LATERAL (
                SELECT CASE WHEN own.median   IS NOT NULL THEN own.median
                            WHEN local.median IS NOT NULL THEN local.median
                            ELSE eia.price END                          AS price,
                       CASE WHEN own.median   IS NOT NULL THEN own.latest_at
                            WHEN local.median IS NOT NULL THEN local.latest_at
                            ELSE eia.reported_at END                    AS reported_at,
                       CASE WHEN own.median   IS NOT NULL THEN 'crowd'
                            WHEN local.median IS NOT NULL THEN 'crowd-local'
                            ELSE eia.reporter_id END                    AS reporter
                FROM
                    -- 1. this station's own crowdsourced reports
                    (SELECT round((percentile_cont(0.5) WITHIN GROUP (ORDER BY pr.price::double precision))::numeric, 3) AS median,
                            max(pr.reported_at) AS latest_at
                     FROM price_report pr
                     WHERE pr.place_id = p.id
                       AND pr.price_type = :priceType
                       AND pr.reporter_id <> :eiaSource
                       AND pr.reported_at > now() - (:maxAgeHours || ' hours')::interval) own
                CROSS JOIN
                    -- 2. nearby stations' crowdsourced reports (gas only)
                    (SELECT round((percentile_cont(0.5) WITHIN GROUP (ORDER BY pr.price::double precision))::numeric, 3) AS median,
                            max(pr.reported_at) AS latest_at
                     FROM price_report pr
                     JOIN place op ON op.id = pr.place_id
                     WHERE :category = 'GAS'
                       AND op.category = 'GAS'
                       AND pr.place_id <> p.id
                       AND pr.price_type = :priceType
                       AND pr.reporter_id <> :eiaSource
                       AND pr.reported_at > now() - (:maxAgeHours || ' hours')::interval
                       AND ST_DWithin(op.location, p.location, :estimateRadiusMeters)) local
                LEFT JOIN LATERAL (
                    -- 3. seeded regional average for this station
                    SELECT pr.price, pr.reported_at, pr.reporter_id
                    FROM price_report pr
                    WHERE pr.place_id = p.id
                      AND pr.price_type = :priceType
                      AND pr.reporter_id = :eiaSource
                      AND pr.reported_at > now() - (:maxAgeHours || ' hours')::interval
                    ORDER BY pr.reported_at DESC
                    LIMIT 1
                ) eia ON true
            ) lp ON true
            WHERE p.category = :category
              AND ST_DWithin(p.location, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :radiusMeters)
            ORDER BY distance_m ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<NearbyPlaceRow> findNearby(@Param("lon") double lon,
                                    @Param("lat") double lat,
                                    @Param("category") String category,
                                    @Param("radiusMeters") double radiusMeters,
                                    @Param("priceType") String priceType,
                                    @Param("maxAgeHours") int maxAgeHours,
                                    @Param("estimateRadiusMeters") double estimateRadiusMeters,
                                    @Param("eiaSource") String eiaSource,
                                    @Param("limit") int limit);

    interface NearbyPlaceRow {
        Long getId();
        String getName();
        String getBrand();
        String getAddress();
        Double getRating();
        String getOpeningHours();
        double getLat();
        double getLon();
        double getDistanceM();
        java.math.BigDecimal getPrice();
        java.time.Instant getPriceReportedAt();
        /**
         * Source of the computed price (null if none): "crowd" = median of this
         * station's own user reports (a real crowdsourced price); "crowd-local" =
         * median of nearby stations' reports (a local estimate); "eia-avg" = the
         * seeded regional average (a regional estimate).
         */
        String getPriceReporter();
    }
}
