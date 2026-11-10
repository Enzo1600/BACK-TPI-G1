ALTER TABLE assistance_requests ADD COLUMN IF NOT EXISTS resource_release_pending boolean NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS assignments (
  id uuid PRIMARY KEY,
  request_id uuid NOT NULL REFERENCES assistance_requests(id),
  candidate_evaluation_id uuid NOT NULL REFERENCES candidate_evaluations(id),
  tow_truck_id uuid NOT NULL,
  operator_id uuid NOT NULL,
  operator_subject varchar(100) NOT NULL,
  status varchar(20) NOT NULL CHECK(status IN ('RESERVING',  'PROPOSED',  'ACCEPTED',  'REJECTED',  'EXPIRED',  'CANCELLED',  'CLOSED')),
  created_at timestamptz NOT NULL,
  expires_at timestamptz,
  accepted_at timestamptz,
  closed_at timestamptz,
  response_key varchar(100),
  response_fingerprint varchar(64),
  response_kind varchar(20),
  search_attempt_number integer NOT NULL,
  reserve_payload text NOT NULL,
  reason text
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_assignment_active ON assignments(request_id) WHERE status IN ('RESERVING',  'PROPOSED',  'ACCEPTED');

CREATE INDEX IF NOT EXISTS ix_assignment_operator ON assignments(operator_subject,  created_at,  id);

CREATE TABLE IF NOT EXISTS pending_actions (
  id uuid PRIMARY KEY,
  assignment_id uuid NOT NULL REFERENCES assignments(id),
  kind varchar(20) NOT NULL CHECK(kind IN ('RESERVE',  'CONFIRM',  'RELEASE')),
  status varchar(20) NOT NULL CHECK(status IN ('PENDING',  'COMPLETED',  'RETRYING')),
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  UNIQUE(assignment_id,  kind)
);
