-- V14 (KAN-291): per-user color palette, applied across the app on top of the
-- light/dark toggle. Stored as a short preset id; the allowed ids are enforced
-- by ColorPaletteRequest and mirrored in static/palette.js + styles.css.
-- Existing users get 'ocean', the original blue palette, so nothing changes for them.

ALTER TABLE users
    ADD COLUMN color_palette VARCHAR(30) NOT NULL DEFAULT 'ocean';
