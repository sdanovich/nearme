-- V4: add rating + opening_hours columns for non-gas categories.
-- No seed data — places are created at runtime via the API/UI.

ALTER TABLE place ADD COLUMN rating DOUBLE PRECISION;
ALTER TABLE place ADD COLUMN opening_hours VARCHAR(255);
