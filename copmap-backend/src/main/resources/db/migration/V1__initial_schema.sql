-- ============================================================
-- CopMap V1: Initial Schema
-- All tables for the police operations management platform
-- ============================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "postgis";  -- for geospatial queries (optional, graceful fallback)

-- ============================================================
-- ENUMS
-- ============================================================

CREATE TYPE user_role AS ENUM (
    'SUPER_ADMIN',      -- district/range level admin
    'STATION_OFFICER',  -- SHO / inspector who plans operations
    'OFFICER'           -- constable/SI who executes on ground
);

CREATE TYPE user_status AS ENUM ('ACTIVE', 'INACTIVE', 'SUSPENDED');

CREATE TYPE operation_type AS ENUM ('PATROL', 'BANDOBAST', 'NAKABANDI');

CREATE TYPE operation_status AS ENUM (
    'DRAFT',        -- being planned
    'PUBLISHED',    -- visible to officers, not yet started
    'ACTIVE',       -- in progress
    'COMPLETED',    -- closed normally
    'CANCELLED'     -- aborted
);

CREATE TYPE assignment_status AS ENUM (
    'PENDING',      -- assigned, not acknowledged
    'ACKNOWLEDGED', -- officer confirmed receipt
    'ON_DUTY',      -- officer marked self as on duty
    'COMPLETED',    -- officer marked duty done
    'ABSENT'        -- officer did not show up
);

CREATE TYPE shift_type AS ENUM ('MORNING', 'AFTERNOON', 'NIGHT', 'FULL_DAY');

CREATE TYPE alert_type AS ENUM (
    'OFFICER_OFFLINE',      -- no location ping for > threshold
    'BOUNDARY_BREACH',      -- officer left assigned zone
    'SOS',                  -- officer triggered SOS
    'OPERATION_DELAYED',    -- operation not started on time
    'CUSTOM'
);

CREATE TYPE alert_severity AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL');

CREATE TYPE alert_status AS ENUM ('OPEN', 'ACKNOWLEDGED', 'RESOLVED');

-- ============================================================
-- USERS
-- ============================================================

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    badge_number    VARCHAR(20)  NOT NULL UNIQUE,
    full_name       VARCHAR(100) NOT NULL,
    email           VARCHAR(150) UNIQUE,
    phone           VARCHAR(15)  NOT NULL UNIQUE,
    password_hash   TEXT         NOT NULL,
    role            user_role    NOT NULL DEFAULT 'OFFICER',
    status          user_status  NOT NULL DEFAULT 'ACTIVE',
    rank            VARCHAR(50),    -- e.g. "Sub-Inspector", "Head Constable"
    station_id      UUID,           -- FK to stations (future)
    station_name    VARCHAR(100),   -- denormalized for simplicity
    fcm_token       TEXT,           -- Firebase push token
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_badge     ON users(badge_number);
CREATE INDEX idx_users_role      ON users(role);
CREATE INDEX idx_users_station   ON users(station_name);

-- ============================================================
-- OPERATIONS (parent table for Patrols and Bandobast)
-- ============================================================

CREATE TABLE operations (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    type            operation_type   NOT NULL,
    title           VARCHAR(200)     NOT NULL,
    description     TEXT,
    status          operation_status NOT NULL DEFAULT 'DRAFT',

    -- Temporal
    planned_start   TIMESTAMPTZ      NOT NULL,
    planned_end     TIMESTAMPTZ      NOT NULL,
    actual_start    TIMESTAMPTZ,
    actual_end      TIMESTAMPTZ,

    -- Geography (stored as text GeoJSON for portability; use PostGIS if available)
    area_geojson    TEXT,           -- polygon defining the operation area
    location_name   VARCHAR(200),   -- human-readable location

    -- Organisational
    station_name    VARCHAR(100)     NOT NULL,
    created_by      UUID             NOT NULL REFERENCES users(id),
    closed_by       UUID             REFERENCES users(id),
    closure_notes   TEXT,

    -- Shift (for Nakabandi/Bandobast)
    shift_type      shift_type,

    -- Audit
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ops_type    ON operations(type);
CREATE INDEX idx_ops_status  ON operations(status);
CREATE INDEX idx_ops_station ON operations(station_name);
CREATE INDEX idx_ops_start   ON operations(planned_start);
CREATE INDEX idx_ops_creator ON operations(created_by);

-- ============================================================
-- PATROL-SPECIFIC CONFIG (1-to-1 with operations where type=PATROL)
-- ============================================================

CREATE TABLE patrol_configs (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    operation_id    UUID NOT NULL UNIQUE REFERENCES operations(id) ON DELETE CASCADE,

    -- Route as ordered GeoJSON LineString
    route_geojson   TEXT,

    -- Patrol beat name (e.g., "Beat No. 7")
    beat_name       VARCHAR(100),

    -- Expected frequency: how many times the route should be covered
    expected_rounds INTEGER DEFAULT 1,

    -- Interval between rounds in minutes
    round_interval  INTEGER,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- BANDOBAST / NAKABANDI CHECKPOINTS
-- ============================================================

CREATE TABLE checkpoints (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    operation_id    UUID         NOT NULL REFERENCES operations(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,        -- e.g., "Main Gate", "Naka Point 3"
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    radius_meters   INTEGER DEFAULT 50,           -- geofence radius for auto check-in
    sequence_order  INTEGER DEFAULT 0,            -- for ordered patrol checkpoints
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_checkpoints_op ON checkpoints(operation_id);

-- ============================================================
-- OFFICER ASSIGNMENTS
-- ============================================================

CREATE TABLE assignments (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    operation_id    UUID             NOT NULL REFERENCES operations(id) ON DELETE CASCADE,
    officer_id      UUID             NOT NULL REFERENCES users(id),
    assigned_by     UUID             NOT NULL REFERENCES users(id),

    status          assignment_status NOT NULL DEFAULT 'PENDING',

    -- Optional: assign an officer to a specific checkpoint
    checkpoint_id   UUID             REFERENCES checkpoints(id),

    -- Role within the operation (e.g., "Beat Officer", "Naka Head")
    duty_role       VARCHAR(100),

    -- Temporal
    duty_start      TIMESTAMPTZ,
    duty_end        TIMESTAMPTZ,
    acknowledged_at TIMESTAMPTZ,
    checked_in_at   TIMESTAMPTZ,
    checked_out_at  TIMESTAMPTZ,

    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    UNIQUE(operation_id, officer_id)
);

CREATE INDEX idx_assignments_op      ON assignments(operation_id);
CREATE INDEX idx_assignments_officer ON assignments(officer_id);
CREATE INDEX idx_assignments_status  ON assignments(status);

-- ============================================================
-- LOCATION TRACKING (persistent trail)
-- Most recent location is in Redis; this is the audit trail
-- ============================================================

CREATE TABLE location_pings (
    id              BIGSERIAL PRIMARY KEY,
    officer_id      UUID             NOT NULL REFERENCES users(id),
    operation_id    UUID             REFERENCES operations(id),
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    accuracy_meters DOUBLE PRECISION,
    speed_kmh       DOUBLE PRECISION,
    heading_degrees DOUBLE PRECISION,
    battery_percent INTEGER,
    recorded_at     TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

-- Partition by month for scalability (conceptual; implement if PostGIS available)
CREATE INDEX idx_location_officer ON location_pings(officer_id, recorded_at DESC);
CREATE INDEX idx_location_op      ON location_pings(operation_id, recorded_at DESC);

-- ============================================================
-- CHECKPOINT VISITS (audit of officer reaching a checkpoint)
-- ============================================================

CREATE TABLE checkpoint_visits (
    id              BIGSERIAL PRIMARY KEY,
    checkpoint_id   UUID             NOT NULL REFERENCES checkpoints(id),
    officer_id      UUID             NOT NULL REFERENCES users(id),
    operation_id    UUID             NOT NULL REFERENCES operations(id),
    visited_at      TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    auto_detected   BOOLEAN          DEFAULT FALSE,  -- geofence vs manual
    notes           TEXT
);

CREATE INDEX idx_visits_checkpoint ON checkpoint_visits(checkpoint_id);
CREATE INDEX idx_visits_officer    ON checkpoint_visits(officer_id);
CREATE INDEX idx_visits_op         ON checkpoint_visits(operation_id);

-- ============================================================
-- ALERTS
-- ============================================================

CREATE TABLE alerts (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    operation_id    UUID            REFERENCES operations(id),
    officer_id      UUID            REFERENCES users(id),
    alert_type      alert_type      NOT NULL,
    severity        alert_severity  NOT NULL DEFAULT 'MEDIUM',
    status          alert_status    NOT NULL DEFAULT 'OPEN',
    title           VARCHAR(200)    NOT NULL,
    message         TEXT,
    metadata        JSONB,          -- flexible extra data (e.g., coordinates, breach details)
    acknowledged_by UUID            REFERENCES users(id),
    acknowledged_at TIMESTAMPTZ,
    resolved_by     UUID            REFERENCES users(id),
    resolved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_alerts_op       ON alerts(operation_id);
CREATE INDEX idx_alerts_officer  ON alerts(officer_id);
CREATE INDEX idx_alerts_type     ON alerts(alert_type);
CREATE INDEX idx_alerts_status   ON alerts(status);
CREATE INDEX idx_alerts_severity ON alerts(severity);

-- ============================================================
-- AUDIT LOG (immutable event log for compliance)
-- ============================================================

CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50)  NOT NULL,   -- 'OPERATION', 'ASSIGNMENT', etc.
    entity_id   UUID         NOT NULL,
    action      VARCHAR(50)  NOT NULL,   -- 'CREATED', 'STATUS_CHANGED', 'DELETED'
    actor_id    UUID         NOT NULL REFERENCES users(id),
    old_value   JSONB,
    new_value   JSONB,
    ip_address  INET,
    user_agent  TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_actor  ON audit_log(actor_id);
CREATE INDEX idx_audit_time   ON audit_log(created_at DESC);

-- ============================================================
-- REFRESH TOKENS
-- ============================================================

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    token       TEXT        NOT NULL UNIQUE,
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_token  ON refresh_tokens(token);
CREATE INDEX idx_refresh_user   ON refresh_tokens(user_id);

-- ============================================================
-- SEED: default SUPER_ADMIN account (password: Admin@123)
-- ============================================================
INSERT INTO users (badge_number, full_name, email, phone, password_hash, role, rank, station_name)
VALUES (
    'ADMIN001',
    'System Administrator',
    'admin@copmap.in',
    '9999999999',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/Lex.MfVHuXNTi.kCO',  -- Admin@123
    'SUPER_ADMIN',
    'Superintendent of Police',
    'HQ'
);
