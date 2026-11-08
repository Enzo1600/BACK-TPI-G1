CREATE TABLE IF NOT EXISTS evaluations (
 id UUID PRIMARY KEY, request_id UUID NOT NULL REFERENCES assistance_requests(id),
 search_attempt_number integer NOT NULL,
 origin_latitude numeric(9,6) NOT NULL,
 origin_longitude numeric(9,6) NOT NULL,
 destination_latitude numeric(9,6) NOT NULL,
 destination_longitude numeric(9,6) NOT NULL,
 transport_distance_m numeric,
 transport_duration_seconds numeric,
 adjusted_transport_seconds integer,
 route_obtained_at timestamptz,
 vehicle_type varchar(20) NOT NULL,
 declared_weight_kg numeric(9,2) NOT NULL,
 mobility_condition varchar(20) NOT NULL,
 maximum_arrival_seconds integer NOT NULL,
 loading_seconds integer NOT NULL,
 unloading_seconds integer NOT NULL,
 operational_margin_seconds integer NOT NULL,
 configuration_revision varchar(30) NOT NULL,
 coverage_and_admission_summary text NOT NULL,
 result varchar(30) NOT NULL,
 reason_code varchar(100),
 reason_detail text,
 evaluated_at timestamptz NOT NULL
);
CREATE TABLE IF NOT EXISTS candidate_evaluations (
 id UUID PRIMARY KEY, evaluation_id UUID NOT NULL REFERENCES evaluations(id),
 tow_truck_id uuid NOT NULL,
 operator_id uuid NOT NULL,
 operator_subject varchar(100) NOT NULL,
 truck_capacity_kg numeric(9,2) NOT NULL,
 qualification_category varchar(30) NOT NULL,
 qualification_valid_until timestamptz NOT NULL,
 truck_latitude numeric(10,7) NOT NULL,
 truck_longitude numeric(10,7) NOT NULL,
 truck_location_updated_at timestamptz NOT NULL,
 compatible boolean NOT NULL,
 eligible boolean NOT NULL,
 reason_code varchar(100),
 approach_distance_m numeric,
 approach_duration_seconds numeric,
 adjusted_approach_seconds integer,
 approach_route_obtained_at timestamptz,
 transport_distance_m numeric,
 transport_duration_seconds numeric,
 adjusted_transport_seconds integer,
 transport_route_obtained_at timestamptz,
 estimated_service_end_at timestamptz,
 ranking_position integer
);
CREATE INDEX IF NOT EXISTS ix_evaluations_request ON evaluations(request_id,evaluated_at,id);
CREATE INDEX IF NOT EXISTS ix_candidate_evaluation ON candidate_evaluations(evaluation_id,ranking_position);
CREATE TABLE IF NOT EXISTS routing_budget(id INTEGER PRIMARY KEY CHECK(id=1),blocked_until TIMESTAMPTZ NOT NULL);
INSERT INTO routing_budget(id,blocked_until) VALUES(1,'1970-01-01T00:00:00Z') ON CONFLICT(id) DO NOTHING;
CREATE TABLE IF NOT EXISTS routing_calls(id UUID PRIMARY KEY,called_at TIMESTAMPTZ NOT NULL);
CREATE INDEX IF NOT EXISTS ix_routing_calls_time ON routing_calls(called_at);
