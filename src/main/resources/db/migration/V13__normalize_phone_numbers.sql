-- V13 (KAN-257): normalize stored phone numbers to E.164, e.g. 03000000 -> +9613000000.
-- Mirrors com.aalsikke.util.PhoneNumbers so existing users can still log in by phone.
-- Unrecognized values and values that would collide with another user are left unchanged.

WITH cleaned AS (
    SELECT id,
           regexp_replace(phone_number, '[[:space:]().-]', '', 'g') AS s
    FROM users
    WHERE phone_number IS NOT NULL
),
     prefixed AS (
         SELECT id,
                CASE
                    WHEN s LIKE '00%'                    THEN '+' || substr(s, 3)
                    WHEN s LIKE '+%'                     THEN s
                    WHEN s LIKE '961%' AND length(s) >= 10 THEN '+' || s
                    WHEN s LIKE '0%'                     THEN '+961' || substr(s, 2)
                    ELSE '+961' || s
                    END AS s
         FROM cleaned
     ),
     normalized AS (
         SELECT id,
                CASE
                    WHEN s LIKE '+9610%' THEN '+961' || substr(s, 6)   -- +961 03... -> +9613...
                    ELSE s
                    END AS n
         FROM prefixed
     )
UPDATE users u
SET phone_number = n.n
    FROM normalized n
WHERE u.id = n.id
  AND u.phone_number <> n.n
-- only valid results
  AND (n.n ~ '^\+961[0-9]{7,8}$'
   OR (n.n !~ '^\+961' AND n.n ~ '^\+[1-9][0-9]{7,14}$'))
-- never collide with a number another user already has
  AND NOT EXISTS (SELECT 1 FROM users o      WHERE o.phone_number = n.n AND o.id <> u.id)
-- never collide with another row normalizing to the same number
  AND NOT EXISTS (SELECT 1 FROM normalized n2 WHERE n2.n = n.n AND n2.id <> n.id);