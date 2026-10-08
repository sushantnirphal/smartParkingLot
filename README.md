# Smart Parking Lot System — Backend

Low-level design **and** working implementation of the parking-lot backend described in
[`requriment.txt`](requriment.txt).

| Document | What it holds |
|---|---|
| [`architecture.md`](architecture.md) | The LLD: DDL, diagrams, API contract, allocation & fee algorithms, concurrency design |
| [`planning.md`](planning.md) | The build plan: 46-task WBS, milestones, dependency graph, testing strategy, risks |
| `src/` | The implementation of that design |

**Stack:** Java 8 source · Spring Boot 2.7.18 · Spring Data JPA · Flyway · MySQL 8 (H2 in MySQL mode for local/test)

---

## Quick start

```bash
mvn verify                 # builds and runs all 111 tests + the F2 MySQL suite (skips without Docker)
mvn spring-boot:run        # starts on http://localhost:8080 against in-memory H2 (MySQL mode)
```

Real MySQL:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
# MYSQL_USER / MYSQL_PASSWORD default to root/root, database parking_lot (created if absent)
```

Flyway owns the schema completely — `ddl-auto` is `none`. Migrations:

| Migration | Contents |
|---|---|
| `V1__init.sql` | `vehicle`, `parking_floor`, `parking_spot`, `fee_rate`, `parking_ticket` + indexes |
| `V2__seed_rates.sql` | Default rate card (MOTORCYCLE/`SLAB`, CAR/`SLAB`, BUS/`FLAT`) |
| `V3__seed_lot.sql` | Demo lot: 3 floors × (4 motorcycle + 6 car + 2 bus) = 36 spots |

---

## API

Base path `/api/v1` (contract rule C7). Errors always use the same `application/problem+json`
envelope with a stable `code`, `message`, `timestamp`, `traceId`, `status` (rules C1/C2).

| # | Endpoint | Success | Main failures |
|---|---|---|---|
| 1 | `POST /parking/entry` | `201` ticket | `409 ALREADY_PARKED`, `409 PARKING_FULL`, `409 PARKING_FULL_FOR_TYPE`, `404 FLOOR_NOT_FOUND`, `400 VALIDATION_ERROR`, `503 RATE_NOT_CONFIGURED`, `503 GATE_BUSY` |
| 2 | `POST /parking/exit` | `200` receipt | `404 TICKET_NOT_FOUND`, `409 TICKET_ALREADY_CLOSED`, `400 INVALID_EXIT_TIME` |
| 3 | `GET /availability` | `200` per-floor free/total | — |
| 4 | `GET /parking/tickets/{ticketNo}` | `200` | `404 TICKET_NOT_FOUND` |
| 5 | `GET /parking/tickets?status=&plate=&page=&size=` | `200` list (default 20, max 100) | — |
| 6 | `GET /floors/{floorNo}/spots?status=&type=` | `200` list | `404 FLOOR_NOT_FOUND` |
| 7 | `POST /parking/estimate-fee` | `200` quote | `400 VALIDATION_ERROR` |
| 8 | `GET /health`, `GET /metrics`, `GET /info` | `200` | — |

Check-out is idempotent: repeat the call and you get `409 TICKET_ALREADY_CLOSED`, or the original
receipt with `"replay": true` when you pass `?replay=true` (rule C3).

### Examples

```bash
# enter
curl -X POST localhost:8080/api/v1/parking/entry \
     -H 'Content-Type: application/json' \
     -d '{"plate":"KA01AB1234","vehicleType":"CAR","entryFloor":1}'
# => 201 {"ticketNo":"PKT-20261007-76F681C5EB","spotLabel":"F1-C1","entryTime":"2026-10-07T09:11:52Z",...}

# price a 45 minute stay up front
curl -X POST localhost:8080/api/v1/parking/estimate-fee \
     -H 'Content-Type: application/json' \
     -d '{"vehicleType":"CAR","durationMinutes":45}'
# => {"amount":40.00,"currency":"INR","rateCard":"CAR-SLAB","strategy":"SLAB"}

# leave
curl -X POST localhost:8080/api/v1/parking/exit \
     -H 'Content-Type: application/json' \
     -d '{"ticketNo":"PKT-20261007-76F681C5EB","exitTime":"2026-10-07T09:56:52Z"}'
# => {"amount":40.00,"durationMinutes":45,"rateCard":"CAR-SLAB","replay":false}
```

---

## How it works

**Allocation** (`AllocationService` + `AllocationPolicy`) is two-stage, exactly as in
architecture §5: an indexed `SELECT` for free spots of the right type on one floor, then an
in-memory pick by the policy.

* `nearest-entry` (default) — probe the entry floor first, lowest `position` wins, then fall
  through to the other floors ascending.
* `lowest-floor` — always fill the lowest floor first.

Both are selected with `parking.allocation-policy`. Compatibility is exact type matching
(assumption A1), with an optional `parking.allow-motorcycle-on-car` relaxation that never
preempts an empty motorcycle bay.

**Fees** (`FeeStrategy`) use the rate row stored on the ticket — a snapshot taken at check-in, so a
price change mid-stay cannot re-price an open ticket (N3/N4).

| Stay | Vehicle | Fee |
|---|---|---|
| 10 min | CAR | ₹0 (≤ 30 min grace) |
| 45 min | CAR | ₹40 |
| 3 h | CAR | ₹80 |
| 5 h | CAR | ₹120 |
| 25 h | CAR | ₹200 (capped) |
| 20 min | MOTORCYCLE | ₹10 |
| 90 min | BUS | ₹120 |

Every row is a fixture in `PricingStrategiesTest`. See architecture §6 *"Implementation
resolutions"* for the three spec inconsistencies that had to be settled to reproduce them.

**Concurrency** is four layers (architecture §7):

1. one `@Transactional` boundary per gate action;
2. JVM striped locks — per **floor** on entry, per **ticket** on exit — released *after commit*
   (`afterCompletion`), `tryLock` with `parking.lock-timeout-ms` (default 2 s) → `503 GATE_BUSY`;
3. `SELECT … FOR UPDATE` on the spot/ticket row plus `@Version` on the ticket;
4. an after-commit `SpotChangeEvent` that invalidates the availability cache.

Availability is always derived from spot rows (`GROUP BY floor, type, status`) — there is no
counter column to drift (A7/N5).

---

## Layout

```
src/main/java/com/parking/lot
├── web/        controllers, DTOs, error envelope      (Controller)
├── service/    use cases, @Transactional boundaries    (Service)
├── strategy/   allocation + pricing algorithms         (Strategy — pure, Spring-free)
├── model/      entities and enums                      (Domain — no Spring imports)
├── repository/ Spring Data JPA repositories            (Persistence)
├── lock/       LotLockManager (stripe pool)            (Infrastructure)
├── cache/      AvailabilityCache                       (Infrastructure)
├── event/      SpotChangeEvent + after-commit subscriber (Infrastructure)
└── config/     Clock bean, ParkingProperties, strategy wiring
```

## Tests

```bash
mvn test                                    # 111 tests
mvn test -Dtest=F2MySqlConcurrencyIT        # real MySQL 8 via Testcontainers (needs Docker)
```

| Suite | Covers |
|---|---|
| `AllocationPolicyTest`, `PricingStrategiesTest`, `LotLockManagerTest`, `TicketFactoryTest` | Pure units — no Spring, no DB (D3) |
| `RepositoryQueriesTest` | Indexed candidate query, lock queries, derived availability, rate selection |
| `EntryFlowIntegrationTest`, `ExitFlowIntegrationTest`, `AvailabilityIntegrationTest` | Every 409/404/503 branch, fee receipts, idempotent replay, availability |
| `ApiContractTest` | Every endpoint and error envelope over real HTTP (MockMvc) |
| `ParkingConcurrencyTest` | §7's five scenarios — 100 gates/10 spots, duplicate entry, duplicate exit, entry-vs-exit, rollback |
| `F2MySqlConcurrencyIT` | The same guarantees on real InnoDB (RK1); auto-skipped without Docker |

## Known limitations

* `requriment.txt` is truncated at *"Deliverables"* — deliverables were inferred (RK10) and still
  need stakeholder confirmation.
* Multi-day fee-cap scaling and the admin endpoints listed in architecture §9 are recorded as
  extensions, not implemented in v1.
