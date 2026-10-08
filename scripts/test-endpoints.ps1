<#
.SYNOPSIS
    Exercises every REST endpoint of the Parking Lot System and asserts the documented contract.

.DESCRIPTION
    Start the application first, then run this script:

        mvn spring-boot:run                          # terminal 1
        powershell -File scripts\test-endpoints.ps1  # terminal 2

    It covers, in order:
      * the three actuator endpoints            (architecture.md section 8, endpoint #8)
      * all 7 documented fee quotes             (architecture.md section 6 worked examples)
      * POST /entry  - happy path, duplicate, plate normalisation, missing field,
                      unknown vehicle type, unknown floor, versioned base path
      * GET  /availability, /floors/{n}/spots, /parking/tickets, /parking/tickets/{no}
      * POST /exit   - exit-before-entry, receipt, double exit, replay, unknown ticket
      * PARKING_FULL_FOR_TYPE and PARKING_FULL, then the lot is restored

    The script first closes every OPEN ticket, so it is repeatable against a live server.

.PARAMETER Base
    Application base URL. Default http://localhost:8080
#>
param([string]$Base = "http://localhost:8080")

$ErrorActionPreference = 'Stop'
$script:passed = 0
$script:failed = 0

function Step($title) {
    Write-Host ""
    Write-Host ("=" * 72) -ForegroundColor DarkGray
    Write-Host $title -ForegroundColor Cyan
    Write-Host ("=" * 72) -ForegroundColor DarkGray
}

function Invoke-Api {
    param([string]$Method, [string]$Path, [string]$Body)

    $p = @{
        Method          = $Method
        Uri             = "$Base$Path"
        UseBasicParsing = $true
        TimeoutSec      = 30
        Headers         = @{ Accept = 'application/json' }
    }
    if ($Body) {
        $p.ContentType = 'application/json; charset=utf-8'
        $p.Body        = $Body
    }

    # --- happy path -------------------------------------------------------
    try {
        $resp = Invoke-WebRequest @p
    }
    catch {
        $status = 0
        $raw    = ''
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            try {
                $s = $_.Exception.Response.GetResponseStream()
                $s.Position = 0
                $raw = (New-Object System.IO.StreamReader($s)).ReadToEnd()
            }
            catch { }
        }
        if (-not $raw -and $_.ErrorDetails) { $raw = $_.ErrorDetails.Message }

        # network failure (app not running) - let it bubble up
        if ($status -eq 0) { throw }

        $json = $null
        if ($raw) { try { $json = $raw | ConvertFrom-Json } catch { } }
        return [pscustomobject]@{ Ok = $false; Status = $status; Raw = $raw; Json = $json }
    }

    $text = $resp.Content
    if ($text -is [byte[]]) { $text = [System.Text.Encoding]::UTF8.GetString($text) }
    $text = [string]$text

    # actuator may return an unknown media type - parsing must never fail the call
    $json = $null
    if ($text) { try { $json = $text | ConvertFrom-Json } catch { } }

    return [pscustomobject]@{ Ok = $true; Status = [int]$resp.StatusCode; Raw = $text; Json = $json }
}

function Check([bool]$Condition, [string]$Message) {
    if ($Condition) { $script:passed++; Write-Host "  PASS  $Message" -ForegroundColor Green }
    else            { $script:failed++; Write-Host "  FAIL  $Message" -ForegroundColor Red }
}

function Check-Status($r, [int]$Expected, [string]$Message) {
    Check ($r.Status -eq $Expected) "$Message   [HTTP $($r.Status), expected $Expected]"
}

function Check-Error($r, [string]$Code, [string]$Message) {
    $actual = '<none>'
    if ($r.Json -and $r.Json.code) { $actual = $r.Json.code }
    Check ($actual -eq $Code) "$Message   [code=$actual, expected $Code]"
}

function Check-Envelope($r, [int]$ExpectedStatus, [string]$Message) {
    # contract rule C1/C2: every application failure carries code + message
    #                       + timestamp + traceId + status
    $j = $r.Json
    $ok = ($r.Status -eq $ExpectedStatus) -and $j -and $j.code -and $j.message -and
          $j.timestamp -and $j.traceId -and ($j.status -eq $ExpectedStatus)
    Check ([bool]$ok) "$Message   [HTTP $($r.Status) + uniform problem+json envelope]"
}

function Get-FreeTotal($availability) {
    $n = 0
    foreach ($f in $availability.Json.floors) {
        foreach ($prop in $f.free.PSObject.Properties) { $n += [int]$prop.Value }
    }
    return $n
}

function Exit-Ticket([string]$No) {
    Invoke-Api POST '/api/v1/parking/exit' ('{"ticketNo":"' + $No + '"}') | Out-Null
}

# ===========================================================================
# wait for the application
# ===========================================================================
$deadline = (Get-Date).AddSeconds(60)
$up = $false
do {
    try { if ((Invoke-RestMethod "$Base/health" -TimeoutSec 3).status -eq 'UP') { $up = $true } }
    catch { Start-Sleep -Seconds 2 }
} while (-not $up -and (Get-Date) -lt $deadline)

if (-not $up) {
    Write-Host "Application is not reachable at $Base - start it with 'mvn spring-boot:run'." -ForegroundColor Red
    exit 1
}

# ===========================================================================
Step "0. reset - close every OPEN ticket so the run starts from a full lot"
# ===========================================================================
$open = Invoke-Api GET '/api/v1/parking/tickets?status=OPEN&size=100'
$closed = 0
foreach ($t in @($open.Json)) { Exit-Ticket $t.ticketNo; $closed++ }
$av = Invoke-Api GET '/api/v1/availability'
$freeNow = Get-FreeTotal $av
Check ($freeNow -eq 36) "lot starts with all 36 bays free   [got $freeNow, closed $closed leftover ticket(s)]"

# ===========================================================================
Step "8. GET /health , /metrics , /info   (architecture.md section 8)"
# ===========================================================================
$r = Invoke-Api GET '/health'
Check-Status $r 200 'GET /health'
Check ($r.Json.status -eq 'UP') "/health reports status=UP   [got $($r.Json.status)]"
Check-Status (Invoke-Api GET '/metrics') 200 'GET /metrics'
Check-Status (Invoke-Api GET '/info') 200 'GET /info'

# ===========================================================================
Step "7. POST /api/v1/parking/estimate-fee   (the 7 worked examples, section 6)"
# ===========================================================================
$quotes = @(
    @{ v = 'CAR';        m = 10;   want = 0.00   }
    @{ v = 'CAR';        m = 45;   want = 40.00  }
    @{ v = 'CAR';        m = 180;  want = 80.00  }
    @{ v = 'CAR';        m = 300;  want = 120.00 }
    @{ v = 'CAR';        m = 1500; want = 200.00 }
    @{ v = 'MOTORCYCLE'; m = 20;   want = 10.00  }
    @{ v = 'BUS';        m = 90;   want = 120.00 }
)
foreach ($q in $quotes) {
    $body = '{"vehicleType":"' + $q.v + '","durationMinutes":' + $q.m + '}'
    $r    = Invoke-Api POST '/api/v1/parking/estimate-fee' $body
    $got  = $null
    if ($r.Json) { try { $got = [decimal]$r.Json.amount } catch { } }
    $ok = ($r.Status -eq 200) -and ($null -ne $got) -and ($got -eq [decimal]$q.want)
    Check $ok ("{0,-10} {1,4} min -> {2,7:N2}   [got {3}, HTTP {4}]" -f $q.v, $q.m, $q.want, $got, $r.Status)
}
$r = Invoke-Api POST '/api/v1/parking/estimate-fee' '{"vehicleType":"CAR"}'
Check-Envelope $r 400 'estimate-fee with missing durationMinutes'
Check-Error $r 'VALIDATION_ERROR' 'estimate-fee validation code'

# ===========================================================================
Step "4. GET /api/v1/availability   (derived from spot rows, contract rule C5)"
# ===========================================================================
$r = Invoke-Api GET '/api/v1/availability'
Check-Status $r 200 'GET /api/v1/availability'
Check (@($r.Json.floors).Count -eq 3) "3 floors reported   [got $(@($r.Json.floors).Count)]"
Check ($r.Json.floors[0].total.CAR -eq 6) "floor 1 CAR total = 6   [got $($r.Json.floors[0].total.CAR)]"
Check ($r.Json.floors[0].free.CAR   -eq 6) "floor 1 CAR free  = 6   [got $($r.Json.floors[0].free.CAR)]"
Check ($r.Json.floors[0].free.BUS   -eq 2) "floor 1 BUS free  = 2   [got $($r.Json.floors[0].free.BUS)]"
Check ($null -ne $r.Json.asOf) 'asOf timestamp present (contract rule C5)'
Check ($null -ne $r.Json.lotVersion) 'lotVersion present (cache invalidation key)'

# ===========================================================================
Step "1. POST /api/v1/parking/entry   (endpoint #1 - happy path)"
# ===========================================================================
# read the expected spot first, so the assertion holds even if the lot is partly used
$free = Invoke-Api GET '/api/v1/floors/1/spots?status=FREE&type=CAR'
$expectedLabel = $null
if ($free.Json -and @($free.Json).Count -gt 0) { $expectedLabel = @($free.Json)[0].label }

$plate     = 'PS' + [DateTime]::UtcNow.ToString('mmssfff')
$entryBody = '{"plate":"' + $plate + '","vehicleType":"CAR","entryFloor":1}'
$entry     = Invoke-Api POST '/api/v1/parking/entry' $entryBody

Check-Status $entry 201 'POST /parking/entry -> 201 Created'
Check ($entry.Json.ticketNo -like 'PKT-*') "ticketNo minted by TicketFactory   [got $($entry.Json.ticketNo)]"
Check ($entry.Json.plate -eq $plate) "echoes the plate   [got $($entry.Json.plate)]"
Check ($entry.Json.vehicleType -eq 'CAR') 'vehicleType = CAR'
Check ($entry.Json.floorNo -eq 1) "floorNo = 1   [got $($entry.Json.floorNo)]"
Check ($entry.Json.spotLabel -eq $expectedLabel) "nearest free CAR bay   [got $($entry.Json.spotLabel), expected $expectedLabel]"
Check ($entry.Json.status -eq 'OPEN') 'status = OPEN'
Check ([string]$entry.Json.entryTime -match 'Z$') "entryTime is ISO-8601 UTC   [got $($entry.Json.entryTime)]"
Check ($null -eq $entry.Json.exitTime) 'exitTime omitted while the ticket is open (non_null)'

$ticketNo = $entry.Json.ticketNo
$entryIso = [string]$entry.Json.entryTime

# ---- duplicate entry (threat R3 / gate rule G2) ---------------------------
$r = Invoke-Api POST '/api/v1/parking/entry' $entryBody
Check-Envelope $r 409 'duplicate entry'
Check-Error $r 'ALREADY_PARKED' 'duplicate entry code'

# ---- plate normalisation (contract rule C4) -------------------------------
$r = Invoke-Api POST '/api/v1/parking/entry' ('{"plate":"  ' + $plate.ToLower() + '  ","vehicleType":"CAR"}')
Check-Envelope $r 409 'lower-case / padded plate is normalised before lookup'
Check-Error $r 'ALREADY_PARKED' 'normalised plate finds the same open ticket'

# ---- validation -----------------------------------------------------------
$r = Invoke-Api POST '/api/v1/parking/entry' '{"vehicleType":"CAR"}'
Check-Envelope $r 400 'entry with missing plate'
Check-Error $r 'VALIDATION_ERROR' 'missing plate code'

$r = Invoke-Api POST '/api/v1/parking/entry' '{"plate":"PSX1","vehicleType":"SPORTS_CAR"}'
Check-Envelope $r 400 'entry with unknown vehicleType'
Check-Error $r 'VALIDATION_ERROR' 'unknown vehicleType code'

# ---- unknown floor --------------------------------------------------------
$r = Invoke-Api POST '/api/v1/parking/entry' '{"plate":"PSFLOOR99","vehicleType":"CAR","entryFloor":99}'
Check-Envelope $r 404 'unknown entry floor'
Check-Error $r 'FLOOR_NOT_FOUND' 'unknown floor code'

# ---- versioned base path (contract rule C7) -------------------------------
$r = Invoke-Api GET '/availability'
Check-Status $r 404 'unversioned /availability is not served (contract rule C7)'

# ===========================================================================
Step "6. GET /api/v1/parking/tickets   (endpoints #4 and #5)"
# ===========================================================================
$r = Invoke-Api GET ("/api/v1/parking/tickets/$ticketNo")
Check-Status $r 200 'GET /parking/tickets/{ticketNo}'
Check ($r.Json.status -eq 'OPEN') "freshly parked ticket is OPEN   [got $($r.Json.status)]"
Check ($r.Json.plate -eq $plate) 'plate returned'
Check ($r.Json.spotLabel -eq $expectedLabel) 'spotLabel returned'

$r = Invoke-Api GET '/api/v1/parking/tickets/PKT-20261007-0000000000'
Check-Envelope $r 404 'unknown ticket number'
Check-Error $r 'TICKET_NOT_FOUND' 'unknown ticket code'

$r = Invoke-Api GET '/api/v1/parking/tickets?status=OPEN'
Check-Status $r 200 'GET /parking/tickets?status=OPEN'
$hits = @(@($r.Json) | Where-Object { $_.ticketNo -eq $ticketNo })
Check ($hits.Count -eq 1) "the new ticket appears in the OPEN list   [got $($hits.Count) match(es)]"

$r = Invoke-Api GET ("/api/v1/parking/tickets?plate=$plate")
Check-Status $r 200 'GET /parking/tickets?plate='
Check (@($r.Json).Count -eq 1) "plate filter returns exactly one ticket   [got $(@($r.Json).Count)]"

$r = Invoke-Api GET '/api/v1/parking/tickets?status=CLOSED'
Check-Status $r 200 'GET /parking/tickets?status=CLOSED'

$r = Invoke-Api GET '/api/v1/parking/tickets?status=OPEN&size=1'
Check-Status $r 200 'size=1 accepted (contract rule C8: max 100, default 20)'

$r = Invoke-Api GET '/api/v1/parking/tickets?status=NOT_A_STATUS'
Check-Envelope $r 400 'invalid status enum'
Check-Error $r 'VALIDATION_ERROR' 'invalid status enum code'

# ===========================================================================
Step "5. GET /api/v1/floors/{n}/spots   (endpoint #6)"
# ===========================================================================
$r = Invoke-Api GET '/api/v1/floors/1/spots?status=FREE&type=CAR'
Check-Status $r 200 'GET /floors/1/spots?status=FREE&type=CAR'
$freeCar = @($r.Json).Count
Check ($freeCar -eq 5) "5 free CAR bays left on floor 1 (one is occupied)   [got $freeCar]"
Check ($r.Json[0].floorNo -eq 1) "floorNo reported   [got $($r.Json[0].floorNo)]"
Check ($r.Json[0].status -eq 'FREE') 'status filter honoured'
Check ($r.Json[0].spotType -eq 'CAR') 'type filter honoured'
Check ($r.Json[0].label -like 'F1-C*') "label format F1-Cn   [got $($r.Json[0].label)]"

$r = Invoke-Api GET '/api/v1/floors/99/spots'
Check-Envelope $r 404 'unknown floor on spot listing'
Check-Error $r 'FLOOR_NOT_FOUND' 'unknown floor code'

$r = Invoke-Api GET '/api/v1/floors/1/spots?status=NONSENSE'
Check-Envelope $r 400 'invalid status enum on spot listing'
Check-Error $r 'VALIDATION_ERROR' 'invalid status enum code'

# ===========================================================================
Step "2. POST /api/v1/parking/exit   (endpoint #2)"
# ===========================================================================
# negative stay must be rejected BEFORE the ticket is consumed (rule R5:
# never a negative fee, never a negative duration)
$beforeIso = ([DateTimeOffset]::Parse($entryIso).UtcDateTime).AddMinutes(-10).ToString("yyyy-MM-ddTHH:mm:ssZ")
$r = Invoke-Api POST '/api/v1/parking/exit' ('{"ticketNo":"' + $ticketNo + '","exitTime":"' + $beforeIso + '"}')
Check-Envelope $r 400 'exit before entry'
Check-Error $r 'INVALID_EXIT_TIME' 'negative-stay code'
$r = Invoke-Api GET ("/api/v1/parking/tickets/$ticketNo")
Check ($r.Json.status -eq 'OPEN') 'the rejected exit left the ticket OPEN'

# +45 minutes, derived from the entry timestamp, whole seconds
$exitIso  = ([DateTimeOffset]::Parse($entryIso).UtcDateTime).AddMinutes(45).ToString("yyyy-MM-ddTHH:mm:ssZ")
$exitBody = '{"ticketNo":"' + $ticketNo + '","exitTime":"' + $exitIso + '"}'

$r = Invoke-Api POST '/api/v1/parking/exit' $exitBody
Check-Status $r 200 'POST /parking/exit'
$amount = $null
if ($r.Json) { try { $amount = [decimal]$r.Json.amount } catch { } }
Check ($amount -eq [decimal]40.00) "45 min CAR stay bills 40.00   [got $amount]"
Check ($r.Json.durationMinutes -eq 45) "durationMinutes = 45   [got $($r.Json.durationMinutes)]"
Check ($r.Json.rateCard -eq 'CAR-SLAB') 'rateCard snapshot = CAR-SLAB (rule N4)'
Check ($r.Json.currency -eq 'INR') 'currency = INR (contract rule C6)'
Check ($r.Json.replay -eq $false) 'replay = false on the first exit'
Check ($r.Json.exitTime -eq $exitIso) "exitTime echoed back   [got $($r.Json.exitTime)]"
Check ($r.Json.floorNo -eq 1 -and $r.Json.spotLabel) "spot/floor on the receipt   [got $($r.Json.floorNo) $($r.Json.spotLabel)]"

# ---- double exit (threat R4) ---------------------------------------------
$r = Invoke-Api POST '/api/v1/parking/exit' $exitBody
Check-Envelope $r 409 'second exit of the same ticket'
Check-Error $r 'TICKET_ALREADY_CLOSED' 'double exit code'

# ---- idempotent replay (contract rule C3) --------------------------------
$r = Invoke-Api POST '/api/v1/parking/exit?replay=true' $exitBody
Check-Status $r 200 '?replay=true returns the original receipt'
Check ($r.Json.replay -eq $true) 'replay = true'
Check ([decimal]$r.Json.amount -eq [decimal]40.00) 'same amount - never charged twice'
Check ($r.Json.exitTime -eq $exitIso) 'same exitTime as the original receipt'
Check ($r.Json.durationMinutes -eq 45) 'same durationMinutes as the original receipt'

# ---- unknown ticket -------------------------------------------------------
$r = Invoke-Api POST '/api/v1/parking/exit' '{"ticketNo":"PKT-20261007-0000000000"}'
Check-Envelope $r 404 'exit with unknown ticketNo'
Check-Error $r 'TICKET_NOT_FOUND' 'unknown ticket code'

# ---- closed ticket visible in listings -----------------------------------
$r = Invoke-Api GET ("/api/v1/parking/tickets/$ticketNo")
Check ($r.Json.status -eq 'CLOSED') "ticket is CLOSED after exit   [got $($r.Json.status)]"
Check ([string]$r.Json.exitTime -match 'Z$') "exitTime recorded (rule G2)   [got $($r.Json.exitTime)]"

$av = Invoke-Api GET '/api/v1/availability'
$freeAfter = Get-FreeTotal $av
Check ($freeAfter -eq 36) "the occupied bay was returned to the pool   [got $freeAfter free]"

# ===========================================================================
Step "capacity - PARKING_FULL_FOR_TYPE   (fills the 6 BUS bays, then restores)"
# ===========================================================================
$busTickets = @()
for ($i = 1; $i -le 6; $i++) {
    $b = Invoke-Api POST '/api/v1/parking/entry' ('{"plate":"PSBUS00' + $i + '","vehicleType":"BUS","entryFloor":1}')
    if ($b.Status -eq 201) { $busTickets += $b.Json.ticketNo }
}
Check ($busTickets.Count -eq 6) "all 6 BUS bays in the lot can be filled   [got $($busTickets.Count)]"

$r = Invoke-Api POST '/api/v1/parking/entry' '{"plate":"PSBUSX9","vehicleType":"BUS","entryFloor":1}'
Check-Envelope $r 409 'the 7th BUS is rejected'
Check-Error $r 'PARKING_FULL_FOR_TYPE' 'type shortage code (not a full lot)'

$av = Invoke-Api GET '/api/v1/availability'
Check ($av.Json.floors[0].free.CAR -gt 0) 'CAR bays are still free while BUS is short'
Check ($av.Json.floors[0].free.MOTORCYCLE -gt 0) 'MOTORCYCLE bays are still free while BUS is short'

foreach ($t in $busTickets) { Exit-Ticket $t }
$av = Invoke-Api GET '/api/v1/availability'
$freeNow = Get-FreeTotal $av
Check ($freeNow -eq 36) "lot restored after the BUS capacity test   [got $freeNow]"

# ===========================================================================
Step "FULL - PARKING_FULL   (blocks every spot, then restores)"
# ===========================================================================
$all = @()
for ($i = 1; $i -le 12; $i++) {
    $b = Invoke-Api POST '/api/v1/parking/entry' ('{"plate":"PSM' + ('00' + $i) + '","vehicleType":"MOTORCYCLE"}')
    if ($b.Status -eq 201) { $all += $b.Json.ticketNo }
}
for ($i = 1; $i -le 18; $i++) {
    $b = Invoke-Api POST '/api/v1/parking/entry' ('{"plate":"PSC' + ('00' + $i) + '","vehicleType":"CAR"}')
    if ($b.Status -eq 201) { $all += $b.Json.ticketNo }
}
for ($i = 1; $i -le 6; $i++) {
    $b = Invoke-Api POST '/api/v1/parking/entry' ('{"plate":"PSB' + ('00' + $i) + '","vehicleType":"BUS"}')
    if ($b.Status -eq 201) { $all += $b.Json.ticketNo }
}
Check ($all.Count -eq 36) "all 36 bays can be occupied   [got $($all.Count)]"

$av   = Invoke-Api GET '/api/v1/availability'
$free = Get-FreeTotal $av
Check ($free -eq 0) "availability reports 0 free bays   [got $free]"

$r = Invoke-Api POST '/api/v1/parking/entry' '{"plate":"PSFULL99","vehicleType":"CAR"}'
Check-Envelope $r 409 'entry into a completely full lot'
Check-Error $r 'PARKING_FULL' 'full lot code'

foreach ($t in $all) { Exit-Ticket $t }
$av = Invoke-Api GET '/api/v1/availability'
$freeAfter = Get-FreeTotal $av
Check ($freeAfter -eq 36) "lot restored to 36 free bays after the churn   [got $freeAfter]"

# ===========================================================================
Write-Host ""
Write-Host ("=" * 72) -ForegroundColor DarkGray
if ($script:failed -eq 0) {
    Write-Host "  ALL $script:passed CHECKS PASSED" -ForegroundColor Green
}
else {
    Write-Host "  $script:failed FAILED, $script:passed passed" -ForegroundColor Red
}
Write-Host ("=" * 72) -ForegroundColor DarkGray
exit $script:failed
