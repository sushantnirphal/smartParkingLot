-- ===========================================================================
-- V3__seed_lot.sql  -  demo lot: 3 floors
--   per floor: 4 MOTORCYCLE + 6 CAR + 2 BUS = 12 spots, position = distance
--   from the floor entrance (architecture N2).
-- ===========================================================================

INSERT INTO parking_floor (id, floor_no) VALUES (1, 1), (2, 2), (3, 3);

-- ---- Floor 1 -------------------------------------------------------------
INSERT INTO parking_spot (floor_id, label, spot_type, status, position) VALUES
  (1, 'F1-M1', 'MOTORCYCLE', 'FREE', 1),
  (1, 'F1-M2', 'MOTORCYCLE', 'FREE', 2),
  (1, 'F1-M3', 'MOTORCYCLE', 'FREE', 3),
  (1, 'F1-M4', 'MOTORCYCLE', 'FREE', 4),
  (1, 'F1-C1', 'CAR',        'FREE', 5),
  (1, 'F1-C2', 'CAR',        'FREE', 6),
  (1, 'F1-C3', 'CAR',        'FREE', 7),
  (1, 'F1-C4', 'CAR',        'FREE', 8),
  (1, 'F1-C5', 'CAR',        'FREE', 9),
  (1, 'F1-C6', 'CAR',        'FREE', 10),
  (1, 'F1-B1', 'BUS',        'FREE', 11),
  (1, 'F1-B2', 'BUS',        'FREE', 12);

-- ---- Floor 2 -------------------------------------------------------------
INSERT INTO parking_spot (floor_id, label, spot_type, status, position) VALUES
  (2, 'F2-M1', 'MOTORCYCLE', 'FREE', 1),
  (2, 'F2-M2', 'MOTORCYCLE', 'FREE', 2),
  (2, 'F2-M3', 'MOTORCYCLE', 'FREE', 3),
  (2, 'F2-M4', 'MOTORCYCLE', 'FREE', 4),
  (2, 'F2-C1', 'CAR',        'FREE', 5),
  (2, 'F2-C2', 'CAR',        'FREE', 6),
  (2, 'F2-C3', 'CAR',        'FREE', 7),
  (2, 'F2-C4', 'CAR',        'FREE', 8),
  (2, 'F2-C5', 'CAR',        'FREE', 9),
  (2, 'F2-C6', 'CAR',        'FREE', 10),
  (2, 'F2-B1', 'BUS',        'FREE', 11),
  (2, 'F2-B2', 'BUS',        'FREE', 12);

-- ---- Floor 3 -------------------------------------------------------------
INSERT INTO parking_spot (floor_id, label, spot_type, status, position) VALUES
  (3, 'F3-M1', 'MOTORCYCLE', 'FREE', 1),
  (3, 'F3-M2', 'MOTORCYCLE', 'FREE', 2),
  (3, 'F3-M3', 'MOTORCYCLE', 'FREE', 3),
  (3, 'F3-M4', 'MOTORCYCLE', 'FREE', 4),
  (3, 'F3-C1', 'CAR',        'FREE', 5),
  (3, 'F3-C2', 'CAR',        'FREE', 6),
  (3, 'F3-C3', 'CAR',        'FREE', 7),
  (3, 'F3-C4', 'CAR',        'FREE', 8),
  (3, 'F3-C5', 'CAR',        'FREE', 9),
  (3, 'F3-C6', 'CAR',        'FREE', 10),
  (3, 'F3-B1', 'BUS',        'FREE', 11),
  (3, 'F3-B2', 'BUS',        'FREE', 12);
