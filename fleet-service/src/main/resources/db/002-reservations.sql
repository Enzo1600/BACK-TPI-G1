CREATE TABLE IF NOT EXISTS resource_reservations (
  id UUID PRIMARY KEY,
  version bigint NOT NULL,
  request_id UUID NOT NULL,
  tow_truck_id UUID NOT NULL REFERENCES tow_trucks(id),
  operator_id UUID NOT NULL REFERENCES operators(id),
  status varchar(20) NOT NULL CHECK(status IN ('HELD',  'CONFIRMED',  'RELEASED',  'EXPIRED')),
  payload text NOT NULL,
  created_at timestamptz NOT NULL,
  expires_at timestamptz NOT NULL,
  search_deadline timestamptz NOT NULL,
  estimated_service_end_at timestamptz NOT NULL,
  confirmed_at timestamptz,
  released_at timestamptz,
  CHECK(expires_at<=search_deadline),
  CHECK(status<>'CONFIRMED' OR confirmed_at IS NOT NULL)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_reservation_truck ON resource_reservations(tow_truck_id) WHERE status IN ('HELD',  'CONFIRMED');

CREATE UNIQUE INDEX IF NOT EXISTS ux_reservation_operator ON resource_reservations(operator_id) WHERE status IN ('HELD',  'CONFIRMED');
