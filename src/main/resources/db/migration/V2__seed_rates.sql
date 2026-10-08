-- ===========================================================================
-- V2__seed_rates.sql  -  default rate card (architecture.md section 6)
--
-- Resolution note (see architecture.md section 6 "Implementation resolutions"):
--   * MOTORCYCLE / CAR use SLAB  = pure block pricing
--         total = base_amount * ceil(billable_minutes / base_duration_min)
--   * BUS uses FLAT = base block + flat step rate
--         total = base_amount + step_amount * ceil(remaining / step_duration_min)
--   * cap_per_day acts as the session ceiling (see resolution R4-1)
-- ===========================================================================

INSERT INTO fee_rate
  (vehicle_type, strategy, free_minutes, base_duration_min, base_amount,
   step_duration_min, step_amount, cap_per_day, active, valid_from)
VALUES
  ('MOTORCYCLE', 'SLAB', 15,  60, 10.00, 30,  5.00,  60.00, TRUE, '2026-01-01'),
  ('CAR',        'SLAB', 30, 120, 40.00, 60, 20.00, 200.00, TRUE, '2026-01-01'),
  ('BUS',        'FLAT',  0,  60, 80.00, 60, 40.00, 500.00, TRUE, '2026-01-01');
