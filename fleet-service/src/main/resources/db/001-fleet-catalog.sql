-- Additive bootstrap for new and existing phase-3 databases. Hibernate validates the result.
CREATE TABLE IF NOT EXISTS operators (
  id UUID PRIMARY KEY,
  keycloak_subject VARCHAR(100) NOT NULL UNIQUE,
  full_name VARCHAR(150) NOT NULL CHECK (length(trim(full_name)) > 0),
  contact_phone VARCHAR(40),
  registration_status VARCHAR(20) NOT NULL CHECK (registration_status IN ('ACTIVE',  'INACTIVE')),
  availability_status VARCHAR(20) NOT NULL CHECK (availability_status IN ('AVAILABLE',  'UNAVAILABLE')),
  qualification_category VARCHAR(30) CHECK (qualification_category = 'LIGHT_TOWING'),
  qualification_valid_from TIMESTAMPTZ,
  qualification_valid_until TIMESTAMPTZ,
  available_since TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL,
  CHECK (length(trim(keycloak_subject)) > 0),
  CHECK ((qualification_category IS NULL AND qualification_valid_from IS NULL AND qualification_valid_until IS NULL)
  OR (qualification_category IS NOT NULL AND qualification_valid_from IS NOT NULL AND qualification_valid_until IS NOT NULL AND qualification_valid_from < qualification_valid_until)),
  CHECK ((availability_status='AVAILABLE' AND available_since IS NOT NULL AND registration_status='ACTIVE') OR (availability_status='UNAVAILABLE' AND available_since IS NULL))
);

CREATE TABLE IF NOT EXISTS tow_trucks (
  id UUID PRIMARY KEY,
  license_plate VARCHAR(10) NOT NULL UNIQUE CHECK (license_plate ~ '^[A-Z0-9]{6,10}$'),
  operator_id UUID UNIQUE REFERENCES operators(id),
  maximum_load_kg NUMERIC(9,  2) NOT NULL CHECK (maximum_load_kg BETWEEN 500 AND 5000),
  supports_non_rollable BOOLEAN NOT NULL,
  operational_status VARCHAR(20) NOT NULL CHECK (operational_status IN ('ACTIVE',  'INACTIVE',  'MAINTENANCE')),
  latitude NUMERIC(10,  7) CHECK (latitude BETWEEN -90 AND 90),
  longitude NUMERIC(10,  7) CHECK (longitude BETWEEN -180 AND 180),
  location_updated_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL,
  CHECK ((latitude IS NULL AND longitude IS NULL AND location_updated_at IS NULL) OR (latitude IS NOT NULL AND longitude IS NOT NULL AND location_updated_at IS NOT NULL))
);

CREATE TABLE IF NOT EXISTS tow_truck_supported_types (
  tow_truck_id UUID NOT NULL REFERENCES tow_trucks(id),
  vehicle_type VARCHAR(20) NOT NULL CHECK (vehicle_type IN ('CAR',  'SUV',  'PICKUP')),
  PRIMARY KEY(tow_truck_id,  vehicle_type)
);

CREATE TABLE IF NOT EXISTS truck_status_history (
  id UUID PRIMARY KEY,
  tow_truck_id UUID NOT NULL REFERENCES tow_trucks(id),
  previous_status VARCHAR(20) NOT NULL CHECK (previous_status IN ('ACTIVE',  'INACTIVE',  'MAINTENANCE')),
  new_status VARCHAR(20) NOT NULL CHECK (new_status IN ('ACTIVE',  'INACTIVE',  'MAINTENANCE')),
  reason VARCHAR(500) NOT NULL CHECK (length(trim(reason))>0),
  changed_by_subject VARCHAR(100) NOT NULL,
  operation_key VARCHAR(100) UNIQUE,
  correlation_id VARCHAR(100),
  changed_at TIMESTAMPTZ NOT NULL,
  CHECK(previous_status<>new_status)
);

CREATE INDEX IF NOT EXISTS ix_operators_status ON operators(registration_status,  created_at,  id);

CREATE INDEX IF NOT EXISTS ix_trucks_status ON tow_trucks(operational_status,  created_at,  id);

CREATE INDEX IF NOT EXISTS ix_truck_history ON truck_status_history(tow_truck_id,  changed_at);
