ALTER TABLE assignments ADD COLUMN IF NOT EXISTS dispatched_at timestamptz;

ALTER TABLE assignments ADD COLUMN IF NOT EXISTS arrived_at timestamptz;

ALTER TABLE assignments ADD COLUMN IF NOT EXISTS loaded_at timestamptz;

ALTER TABLE assignments ADD COLUMN IF NOT EXISTS transport_started_at timestamptz;

ALTER TABLE assignments ADD COLUMN IF NOT EXISTS completed_at timestamptz;

ALTER TABLE assignments ADD COLUMN IF NOT EXISTS completion_note varchar(500);
