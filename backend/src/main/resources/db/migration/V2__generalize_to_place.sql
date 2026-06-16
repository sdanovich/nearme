-- V2: generalize the schema so the same machinery serves any place category
-- (GAS now; COFFEE / RESTAURANT later). Renames in place — no data loss.

-- gas_station -> place, with a category column (default existing rows to GAS).
ALTER TABLE gas_station RENAME TO place;
ALTER TABLE place ADD COLUMN category VARCHAR(32) NOT NULL DEFAULT 'GAS';
ALTER TABLE place ALTER COLUMN category DROP DEFAULT;
CREATE INDEX idx_place_category ON place (category);

-- price_report: station_id -> place_id, fuel_type -> price_type.
ALTER TABLE price_report RENAME COLUMN station_id TO place_id;
ALTER TABLE price_report RENAME COLUMN fuel_type TO price_type;

-- Refresh the composite index to match the new column names.
DROP INDEX IF EXISTS idx_report_station_fuel_time;
CREATE INDEX idx_report_place_type_time ON price_report (place_id, price_type, reported_at);
