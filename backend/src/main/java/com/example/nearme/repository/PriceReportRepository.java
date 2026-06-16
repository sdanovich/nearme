package com.example.nearme.repository;

import com.example.nearme.model.PriceReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PriceReportRepository extends JpaRepository<PriceReport, Long> {

    /** Price history for one place + price type, newest first. */
    @Query("""
            SELECT r FROM PriceReport r
            WHERE r.place.id = :placeId AND r.priceType = :priceType
            ORDER BY r.reportedAt DESC
            """)
    List<PriceReport> history(@Param("placeId") Long placeId,
                              @Param("priceType") String priceType);

    /**
     * Recent prices for a place + price type, for outlier detection. Returns
     * the raw numbers (newest first) over the last {sinceHours} hours, capped.
     * Used as the "comparable sample" fed to the OutlierGuard.
     */
    @Query(value = """
            SELECT pr.price
            FROM price_report pr
            WHERE pr.place_id = :placeId
              AND pr.price_type = :priceType
              AND pr.reported_at > now() - (:sinceHours || ' hours')::interval
            ORDER BY pr.reported_at DESC
            LIMIT :maxSamples
            """, nativeQuery = true)
    List<java.math.BigDecimal> recentPricesFor(@Param("placeId") Long placeId,
                                               @Param("priceType") String priceType,
                                               @Param("sinceHours") int sinceHours,
                                               @Param("maxSamples") int maxSamples);
}
