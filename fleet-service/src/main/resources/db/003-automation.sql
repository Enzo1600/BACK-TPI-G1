ALTER TABLE resource_reservations ADD COLUMN IF NOT EXISTS created_hold boolean NOT NULL DEFAULT false;
UPDATE resource_reservations SET created_hold=true WHERE status IN ('HELD','CONFIRMED','RELEASED') AND created_hold=false;
