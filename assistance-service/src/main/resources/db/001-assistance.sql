CREATE TABLE IF NOT EXISTS vehicles (
 id UUID PRIMARY KEY,owner_subject VARCHAR(100) NOT NULL,license_plate VARCHAR(10) NOT NULL UNIQUE CHECK(license_plate ~ '^[A-Z0-9]{6,10}$'),
 brand VARCHAR(100),model VARCHAR(100),vehicle_type VARCHAR(20) NOT NULL CHECK(vehicle_type IN ('CAR','SUV','PICKUP')),
 declared_weight_kg NUMERIC(9,2) NOT NULL CHECK(declared_weight_kg BETWEEN 500 AND 3000),
 registration_status VARCHAR(20) NOT NULL CHECK(registration_status IN ('ACTIVE','INACTIVE')),
 created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,version BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS assistance_requests (
 id UUID PRIMARY KEY,vehicle_id UUID NOT NULL REFERENCES vehicles(id),driver_subject VARCHAR(100) NOT NULL,
 status VARCHAR(30) NOT NULL CHECK(status IN ('REQUESTED','PENDING_EVALUATION','AWAITING_ACCEPTANCE','ASSIGNED','SEARCHING_ALTERNATIVE','DISPATCHED','ARRIVED','VEHICLE_LOADED','IN_TRANSIT','COMPLETED','REJECTED','UNFULFILLED','CANCELLED')),first_assigned_at TIMESTAMPTZ,closure_reason_code VARCHAR(100),
 origin_latitude NUMERIC(9,6) NOT NULL CHECK(origin_latitude BETWEEN -90 AND 90),origin_longitude NUMERIC(9,6) NOT NULL CHECK(origin_longitude BETWEEN -180 AND 180),
 destination_latitude NUMERIC(9,6) NOT NULL CHECK(destination_latitude BETWEEN -90 AND 90),destination_longitude NUMERIC(9,6) NOT NULL CHECK(destination_longitude BETWEEN -180 AND 180),
 declared_vehicle_type VARCHAR(20) NOT NULL CHECK(declared_vehicle_type IN ('CAR','SUV','PICKUP')),
 declared_weight_kg NUMERIC(9,2) NOT NULL CHECK(declared_weight_kg BETWEEN 500 AND 3000),
 mobility_condition VARCHAR(20) NOT NULL CHECK(mobility_condition IN ('ROLLABLE','NON_ROLLABLE')),
 description VARCHAR(1000) NOT NULL CHECK(length(trim(description))>0),search_attempt_number INTEGER NOT NULL CHECK(search_attempt_number BETWEEN 1 AND 3),
 search_deadline TIMESTAMPTZ NOT NULL,configuration_revision VARCHAR(30) NOT NULL,configuration_snapshot TEXT NOT NULL,
 idempotency_key VARCHAR(100) NOT NULL CHECK(length(trim(idempotency_key))>0),request_fingerprint VARCHAR(64) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,closed_at TIMESTAMPTZ,version BIGINT NOT NULL,
 UNIQUE(driver_subject,idempotency_key),CHECK(origin_latitude<>destination_latitude OR origin_longitude<>destination_longitude),
 CHECK(search_deadline>created_at),CHECK((closed_at IS NOT NULL)=(status IN ('COMPLETED','REJECTED','UNFULFILLED','CANCELLED'))),
 CHECK(status<>'CANCELLED' OR first_assigned_at IS NULL)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_requests_active_vehicle ON assistance_requests(vehicle_id) WHERE closed_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_requests_driver_status ON assistance_requests(driver_subject,status,created_at,id);
CREATE INDEX IF NOT EXISTS ix_vehicle_owner ON vehicles(owner_subject,created_at,id);
CREATE TABLE IF NOT EXISTS request_status_history (
 id UUID PRIMARY KEY,request_id UUID NOT NULL REFERENCES assistance_requests(id),previous_status VARCHAR(30) CHECK(previous_status IN ('REQUESTED','PENDING_EVALUATION','AWAITING_ACCEPTANCE','ASSIGNED','SEARCHING_ALTERNATIVE','DISPATCHED','ARRIVED','VEHICLE_LOADED','IN_TRANSIT','COMPLETED','REJECTED','UNFULFILLED','CANCELLED')),
 new_status VARCHAR(30) NOT NULL CHECK(new_status IN ('REQUESTED','PENDING_EVALUATION','AWAITING_ACCEPTANCE','ASSIGNED','SEARCHING_ALTERNATIVE','DISPATCHED','ARRIVED','VEHICLE_LOADED','IN_TRANSIT','COMPLETED','REJECTED','UNFULFILLED','CANCELLED')),actor_subject VARCHAR(100),actor_type VARCHAR(20) NOT NULL,
 reason TEXT,details TEXT,operation_key VARCHAR(100),request_fingerprint VARCHAR(64),correlation_id VARCHAR(100),occurred_at TIMESTAMPTZ NOT NULL,
 UNIQUE(request_id,operation_key)
);
CREATE INDEX IF NOT EXISTS ix_request_history ON request_status_history(request_id,occurred_at,id);
