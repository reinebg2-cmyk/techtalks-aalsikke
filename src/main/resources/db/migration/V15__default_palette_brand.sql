-- V15 (KAN-294): the logo palette ("brand") is now the default color palette.
-- Rows still on the previous default ('ocean') move to it; users pick any preset from Settings.

ALTER TABLE users ALTER COLUMN color_palette SET DEFAULT 'brand';
UPDATE users SET color_palette = 'brand' WHERE color_palette = 'ocean';
