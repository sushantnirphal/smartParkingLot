-- ===========================================================================
-- V1__init.sql  -  Smart Parking Lot schema (architecture.md section 3)
-- Portable across MySQL 8 and H2 (MODE=MySQL): no inline KEY clauses,
-- no partial indexes, constraints written with portable CONSTRAINT syntax.
-- ===========================================================================

CREATE TABLE vehicle (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  plate_no     VARCHAR(20)  NOT NULL,
  vehicle_type VARCHAR(12)  NOT NULL,
  make_model   VARCHAR(60)  NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_vehicle_plate UNIQUE (plate_no)
);

CREATE TABLE parking_floor (
  id       INT AUTO_INCREMENT PRIMARY KEY,
  floor_no INT NOT NULL,
  CONSTRAINT uk_floor_no UNIQUE (floor_no)
);

CREATE TABLE parking_spot (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  floor_id  INT NOT NULL,
  label     VARCHAR(16) NOT NULL,
  spot_type VARCHAR(12) NOT NULL,
  status    VARCHAR(10) NOT NULL DEFAULT 'FREE',
  position  INT NOT NULL,
  CONSTRAINT fk_spot_floor FOREIGN KEY (floor_id) REFERENCES parking_floor (id),
  CONSTRAINT uk_spot_label UNIQUE (label)
);

-- architecture N2: order within floor -> "nearest to entrance" is ORDER BY position
CREATE INDEX idx_spot_lookup ON parking_spot (floor_id, spot_type, status, position);

CREATE TABLE fee_rate (
  id                INT AUTO_INCREMENT PRIMARY KEY,
  vehicle_type      VARCHAR(12) NOT NULL,
  strategy          VARCHAR(10) NOT NULL DEFAULT 'SLAB',
  free_minutes      INT NOT NULL DEFAULT 0,
  base_duration_min INT NOT NULL,
  base_amount       DECIMAL(10,2) NOT NULL,
  step_duration_min INT NULL,
  step_amount       DECIMAL(10,2) NULL,
  cap_per_day       DECIMAL(10,2) NULL,
  active            BOOLEAN NOT NULL DEFAULT TRUE,
  valid_from        DATE NOT NULL
);

CREATE INDEX idx_rate_lookup ON fee_rate (vehicle_type, active, valid_from);

-- architecture N1: ticket status is the lifecycle; spot_id survives close for audit.
CREATE TABLE parking_ticket (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  ticket_no        VARCHAR(24) NOT NULL,
  vehicle_id       BIGINT NOT NULL,
  spot_id          BIGINT NULL,
  fee_rate_id      INT NULL,
  entry_time       DATETIME NOT NULL,
  exit_time        DATETIME NULL,
  status           VARCHAR(10) NOT NULL DEFAULT 'OPEN',
  amount_charged   DECIMAL(10,2) NULL,
  duration_minutes INT NULL,
  version          BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_tkt_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicle (id),
  CONSTRAINT fk_tkt_spot    FOREIGN KEY (spot_id)    REFERENCES parking_spot (id),
  CONSTRAINT fk_tkt_rate    FOREIGN KEY (fee_rate_id) REFERENCES fee_rate (id),
  CONSTRAINT uk_ticket_no UNIQUE (ticket_no)
);

CREATE INDEX idx_tkt_open    ON parking_ticket (status, vehicle_id);
CREATE INDEX idx_tkt_spot_open ON parking_ticket (spot_id, status);
