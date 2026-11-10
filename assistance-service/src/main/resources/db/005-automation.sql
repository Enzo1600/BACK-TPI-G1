ALTER TABLE pending_actions ADD COLUMN IF NOT EXISTS failure_count integer NOT NULL DEFAULT 0;

ALTER TABLE pending_actions ADD COLUMN IF NOT EXISTS next_attempt_at timestamptz NOT NULL DEFAULT now();

CREATE INDEX IF NOT EXISTS ix_pending_actions_due ON pending_actions(next_attempt_at) WHERE status<>'COMPLETED';

ALTER TABLE assistance_requests ADD COLUMN IF NOT EXISTS next_evaluation_at timestamptz NOT NULL DEFAULT now();

ALTER TABLE assistance_requests ADD COLUMN IF NOT EXISTS next_reconciliation_at timestamptz NOT NULL DEFAULT now();

CREATE INDEX IF NOT EXISTS ix_requests_automation ON assistance_requests(status,  next_evaluation_at,  created_at);

ALTER TABLE assistance_requests ADD COLUMN IF NOT EXISTS evaluation_lease_until timestamptz NOT NULL DEFAULT '1970-01-01T00:00:00Z';

ALTER TABLE assistance_requests ADD COLUMN IF NOT EXISTS replacement_pending boolean NOT NULL DEFAULT false;
