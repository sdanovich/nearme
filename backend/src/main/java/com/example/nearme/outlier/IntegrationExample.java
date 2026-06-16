package com.example.nearme.outlier;

/**
 * INTEGRATION EXAMPLE — how to use the outlier component in any domain.
 *
 * This is documentation-as-code: a sketch you copy into a real service. It is
 * intentionally not wired into the app (no @Service) so it stays decoupled from
 * whichever entity/field names your domain uses.
 *
 * The pattern is always three steps:
 *   1. Gather the comparable sample (DOMAIN-SPECIFIC — a query over the right group).
 *   2. Call the guard (SHARED).
 *   3. Act on the verdict (DOMAIN-SPECIFIC — reject, flag, accept-but-mark).
 *
 * ---------------------------------------------------------------------------
 * EXAMPLE A — Gas price report (reject implausible, flag suspicious):
 *
 *   // 1. gather: recent prices for THIS station + fuel, last N days
 *   List<Double> recent = priceReportRepo.recentPricesFor(stationId, fuel, sinceDays);
 *   double[] sample = recent.stream().mapToDouble(Double::doubleValue).toArray();
 *
 *   // 2. judge: gas can never be <= 0 or, say, > 20/gal -> hard bounds;
 *   //    MAD with a slightly relaxed threshold for noisy crowd data.
 *   OutlierConfig cfg = OutlierConfig.defaults()
 *           .withThreshold(5.0)
 *           .withBounds(0.10, 20.0);
 *   OutlierResult verdict = guard.check(candidatePrice, sample, cfg);
 *
 *   // 3. act
 *   if (verdict.outlier() && Double.isInfinite(verdict.score())) {
 *       throw new BadRequestException("Implausible price: " + verdict.reason());
 *   }
 *   report.setFlagged(verdict.outlier());      // keep, but mark for review
 *   report.setOutlierScore(verdict.score());
 *
 * ---------------------------------------------------------------------------
 * EXAMPLE B — Apartment rent (same component, different domain, no code change
 * to the detector):
 *
 *   double[] sample = rentRepo.recentRents(buildingId, bedrooms);
 *   OutlierResult verdict = guard.check(candidateRent, sample,
 *           OutlierConfig.defaults().withBounds(100.0, 100_000.0));
 *   if (verdict.outlier()) { ...flag for manual review... }
 *
 * ---------------------------------------------------------------------------
 * EXAMPLE C — Sensor telemetry (IQR method, stricter whiskers):
 *
 *   OutlierConfig cfg = OutlierConfig.defaults()
 *           .withMethod(OutlierConfig.Method.IQR)
 *           .withThreshold(3.0);
 *   OutlierResult verdict = guard.check(reading, last100Readings, cfg);
 *
 * The ONLY thing that changes per domain is how you build `sample` and what you
 * do with the verdict. The statistics are identical.
 */
public final class IntegrationExample {
    private IntegrationExample() {}
}
