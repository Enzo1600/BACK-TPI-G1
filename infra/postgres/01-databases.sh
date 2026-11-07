#!/bin/sh
set -eu
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set=ON_ERROR_STOP=1 --set=assistance_password="$ASSISTANCE_DB_PASSWORD" --set=fleet_password="$FLEET_DB_PASSWORD" --set=keycloak_password="$KEYCLOAK_DB_PASSWORD" <<'SQL'
CREATE ROLE assistance_user LOGIN PASSWORD :'assistance_password';
CREATE ROLE fleet_user LOGIN PASSWORD :'fleet_password';
CREATE ROLE keycloak_user LOGIN PASSWORD :'keycloak_password';
CREATE DATABASE assistance_db OWNER assistance_user;
CREATE DATABASE fleet_db OWNER fleet_user;
CREATE DATABASE keycloak_db OWNER keycloak_user;
REVOKE ALL ON DATABASE assistance_db FROM PUBLIC;
REVOKE ALL ON DATABASE fleet_db FROM PUBLIC;
REVOKE ALL ON DATABASE keycloak_db FROM PUBLIC;
GRANT CONNECT ON DATABASE assistance_db TO assistance_user;
GRANT CONNECT ON DATABASE fleet_db TO fleet_user;
GRANT CONNECT ON DATABASE keycloak_db TO keycloak_user;
SQL
