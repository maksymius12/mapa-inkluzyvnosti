<#
.SYNOPSIS
  Резервна копія всіх таблиць схеми public (Supabase) у JSON — у папку ПОЗА репозиторієм.

.DESCRIPTION
  Коли запускати: перед кожною міграцією і щонайменше раз на тиждень (CLAUDE.md, розділ 4).

  Ключ береться лише зі змінної середовища (за замовчуванням SUPABASE_SERVICE_ROLE_KEY: sb_secret_… або
  старий JWT service_role), у файли копії й вивід він не потрапляє. sb_secret_/sb_publishable_ — лише заголовок
  apikey; JWT — apikey + Authorization: Bearer.
  Якщо схема API (GET /rest/v1/) для ключа недоступна — передайте таблиці через -Tables (перелік у README). Service-ключ потрібен, щоб RLS не приховала рядки.
  URL — із SUPABASE_URL або з local.properties (URL не секрет).

  Результат: <OutRoot>\<yyyy-MM-dd_HHmmss>\<таблиця>.json + manifest.json + counts.txt.
  Кількість рядків кожної таблиці звіряється з точним підрахунком сервера; за розбіжності чи помилки — код виходу 1.

  Не копіюються: файли зі Storage (фото), auth.users. Копію не комітити й не вантажити як GitHub-артефакт.

.EXAMPLE
  $env:SUPABASE_SERVICE_ROLE_KEY = '<вставити ключ лише в цю сесію PowerShell>'
  powershell -ExecutionPolicy Bypass -File tools\backup\backup-db.ps1
  Remove-Item Env:SUPABASE_SERVICE_ROLE_KEY

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\backup\backup-db.ps1 -OutRoot D:\MapaBackups -Tables places,checks
#>
[CmdletBinding()]
param(
    [string]$OutRoot = (Join-Path $env:USERPROFILE 'MapaBackups'),
    [string]$SupabaseUrl = $env:SUPABASE_URL,
    [string]$KeyEnvVar = 'SUPABASE_SERVICE_ROLE_KEY',
    [string[]]$Tables,
    [int]$PageSize = 1000
)

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# Службові таблиці/представлення PostGIS — не дані застосунку.
$Excluded = @('spatial_ref_sys', 'geometry_columns', 'geography_columns')

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

# «-Tables a,b» через powershell -File приходить одним рядком — розбиваємо самі.
if ($Tables) { $Tables = @($Tables | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ }) }

function Fail([string]$Message) {
    Write-Host "ПОМИЛКА: $Message" -ForegroundColor Red
    exit 1
}

# --- Ключ і URL ---------------------------------------------------------------------------------
$Key = [Environment]::GetEnvironmentVariable($KeyEnvVar)
if ([string]::IsNullOrWhiteSpace($Key)) {
    Fail "Змінна середовища $KeyEnvVar порожня. Задайте її лише для цієї сесії: `$env:$KeyEnvVar = '...'"
}
if ([string]::IsNullOrWhiteSpace($SupabaseUrl)) {
    $props = Join-Path $RepoRoot 'local.properties'
    if (Test-Path $props) {
        $line = Get-Content $props | Where-Object { $_ -match '^\s*SUPABASE_URL\s*=' } | Select-Object -First 1
        if ($line) { $SupabaseUrl = ($line -split '=', 2)[1].Trim() }
    }
}
if ([string]::IsNullOrWhiteSpace($SupabaseUrl)) { Fail 'Не знайдено SUPABASE_URL (змінна середовища або local.properties).' }
$SupabaseUrl = $SupabaseUrl.TrimEnd('/')

# --- Папка поза репозиторієм ----------------------------------------------------------------------
$OutRootFull = [IO.Path]::GetFullPath($OutRoot)
$repoPrefix = $RepoRoot.TrimEnd('\') + '\'
if (($OutRootFull.TrimEnd('\') + '\').StartsWith($repoPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    Fail "Папка копій ($OutRootFull) всередині репозиторію. Оберіть іншу: -OutRoot D:\MapaBackups"
}
$stamp = Get-Date -Format 'yyyy-MM-dd_HHmmss'
$OutDir = Join-Path $OutRootFull $stamp
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

# --- HTTP -----------------------------------------------------------------------------------------
# Заголовки залежать від формату ключа:
#   sb_secret_… / sb_publishable_… (нові ключі) — ЛИШЕ apikey; Bearer із таким ключем шлюз відхиляє (401);
#   eyJ… (старий JWT service_role/anon)         — apikey + Authorization: Bearer.
if ($Key.StartsWith('sb_secret_')) {
    $KeyKind = 'secret'
    $Headers = @{ apikey = $Key }
} elseif ($Key.StartsWith('sb_publishable_')) {
    $KeyKind = 'publishable (RLS — неповна копія!)'
    $Headers = @{ apikey = $Key }
} elseif ($Key.StartsWith('eyJ')) {
    $KeyKind = 'jwt'
    $Headers = @{ apikey = $Key; Authorization = "Bearer $Key" }
} else {
    Fail "Невідомий формат ключа в $KeyEnvVar (очікується sb_secret_…, sb_publishable_… або JWT eyJ…)."
}

# Власний User-Agent обов'язковий: стандартний у PowerShell починається з «Mozilla/5.0», і Supabase
# відхиляє такі запити з sb_secret_ як «Forbidden use of secret API key in browser» (HTTP 401).
# Усі HTTP-виклики скрипта — лише через Invoke-Api.
$UserAgent = 'mapa-backup/1.0'

function Invoke-Api([string]$PathAndQuery, [hashtable]$Extra = @{}, [string]$Accept = 'application/json') {
    $h = $Headers.Clone()
    foreach ($k in $Extra.Keys) { $h[$k] = $Extra[$k] }
    $h['Accept'] = $Accept
    try {
        return Invoke-WebRequest -Uri "$SupabaseUrl$PathAndQuery" -Headers $h -Method Get -UseBasicParsing `
            -UserAgent $UserAgent -TimeoutSec 120
    } catch {
        $status = $null
        $resp = $_.Exception.Response
        if ($resp) { try { $status = [int]$resp.StatusCode } catch { } }
        # PowerShell 5.1 кладе тіло відповіді з помилкою в ErrorDetails; потік відповіді зазвичай уже прочитано.
        $body = $null
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) { $body = $_.ErrorDetails.Message }
        if (-not $body -and $resp) {
            try {
                $stream = $resp.GetResponseStream()
                if ($stream.CanSeek) { $stream.Position = 0 }
                $body = (New-Object IO.StreamReader($stream)).ReadToEnd()
            } catch { }
        }
        if ($body) {
            $body = $body.Trim()
            if ($body.Length -gt 500) { $body = $body.Substring(0, 500) + '…' }
        } else {
            $body = "(порожнє тіло; $($_.Exception.Message))"
        }
        # Про всяк випадок: ключ ніколи не потрапляє у вивід, навіть якщо сервер повторить його у відповіді.
        $body = $body.Replace($Key, '***')
        $code = if ($status) { "HTTP $status" } else { 'немає відповіді' }
        throw "GET $PathAndQuery — ${code}: $body"
    }
}

# PowerShell 5.1 віддає тіло з незнайомим типом (application/openapi+json) як байти.
function Get-Body($Response) {
    $c = $Response.Content
    if ($c -is [byte[]]) { return [Text.Encoding]::UTF8.GetString($c) }
    return [string]$c
}

function Get-ContentRange($Response) {
    $raw = $Response.Headers['Content-Range']
    if ($raw -is [array]) { $raw = $raw[0] }
    # «0-999/1234» або «*/0»
    if ($raw -match '^(\*|(\d+)-(\d+))/(\d+|\*)$') {
        $rows = if ($Matches[1] -eq '*') { 0 } else { [int64]$Matches[3] - [int64]$Matches[2] + 1 }
        $total = if ($Matches[4] -eq '*') { $null } else { [int64]$Matches[4] }
        return @{ Rows = $rows; Total = $total }
    }
    throw "Несподіваний Content-Range: '$raw'"
}

# --- Список таблиць і первинні ключі (зі схеми OpenAPI PostgREST) ---------------------------------
# Запасний варіант, якщо схема недоступна (буває із sb_secret_…): таблиці з -Tables (перелік — у README).
$Definitions = $null
$SchemaSource = 'openapi'
try {
    $spec = Get-Body (Invoke-Api '/rest/v1/' -Accept 'application/openapi+json') | ConvertFrom-Json
    $Definitions = $spec.definitions
    if (-not $Definitions) { throw 'відповідь без definitions' }
} catch {
    $SchemaSource = 'tables-param'
    $why = $_.Exception.Message
    if (-not $Tables) {
        Fail "Схема API недоступна ($why). Передайте таблиці явно: -Tables <перелік із README, розділ «Резервні копії бази»>"
    }
    Write-Host "Схема API недоступна ($why) — беру таблиці з -Tables, порядок: за id (якщо є)." -ForegroundColor Yellow
}

if (-not $Tables) {
    $Tables = @($Definitions.PSObject.Properties | ForEach-Object { $_.Name } | Where-Object { $Excluded -notcontains $_ } | Sort-Object)
}
if ($Tables.Count -eq 0) { Fail 'Не знайдено жодної таблиці.' }

function Get-OrderClause([string]$Table) {
    if (-not $Definitions) { return $null }
    $def = $Definitions.$Table
    if (-not $def) { return $null }
    $cols = @($def.properties.PSObject.Properties)
    $pk = @($cols | Where-Object { $_.Value.description -and $_.Value.description -like '*<pk/>*' } | ForEach-Object { $_.Name })
    $order = if ($pk.Count -gt 0) { $pk } else { @($cols | ForEach-Object { $_.Name }) }
    return ($order | ForEach-Object { [Uri]::EscapeDataString($_) + '.asc' }) -join ','
}

# --- Вивантаження -------------------------------------------------------------------------------
$utf8 = New-Object Text.UTF8Encoding($false)
$results = @()
$failed = $false

foreach ($table in $Tables) {
    $file = Join-Path $OutDir "$table.json"
    $order = Get-OrderClause $table
    # Без схеми первинний ключ невідомий: пробуємо id, а якщо такої колонки немає — без порядку
    # (це безпечно лише для таблиці, що вміщується в одну сторінку; інакше — помилка нижче).
    $guessedOrder = $false
    if (-not $Definitions) { $order = 'id.asc'; $guessedOrder = $true }
    $writer = New-Object IO.StreamWriter($file, $false, $utf8)
    $written = [int64]0
    $expected = $null
    $err = $null
    try {
        $writer.Write('[')
        $first = $true
        $offset = [int64]0
        while ($true) {
            $base = "/rest/v1/$([Uri]::EscapeDataString($table))?select=*&limit=$PageSize&offset=$offset"
            try {
                $resp = Invoke-Api $(if ($order) { "$base&order=$order" } else { $base }) @{ Prefer = 'count=exact' }
            } catch {
                if ($guessedOrder -and $offset -eq 0 -and $_.Exception.Message -like '*42703*') {
                    $order = $null
                    $guessedOrder = $false
                    $resp = Invoke-Api $base @{ Prefer = 'count=exact' }
                } else {
                    throw
                }
            }
            $range = Get-ContentRange $resp
            if ($null -eq $expected) { $expected = $range.Total }
            if (-not $order -and $expected -gt $PageSize) {
                throw "без стабільного порядку (немає схеми й колонки id), а рядків $expected більше за сторінку; запустіть із -PageSize $expected"
            }
            $body = (Get-Body $resp).Trim()
            if ($range.Rows -gt 0) {
                if (-not ($body.StartsWith('[') -and $body.EndsWith(']'))) { throw "Відповідь для $table — не масив JSON" }
                if (-not $first) { $writer.Write(',') }
                $writer.Write($body.Substring(1, $body.Length - 2))
                $first = $false
                $written += $range.Rows
            }
            if ($range.Rows -lt $PageSize) { break }
            $offset += $range.Rows
        }
        $writer.Write(']')
    } catch {
        $err = $_.Exception.Message
    } finally {
        $writer.Dispose()
    }

    $ok = (-not $err) -and ($null -ne $expected) -and ($written -eq $expected)
    if (-not $ok) { $failed = $true }
    $hash = (Get-FileHash -Algorithm SHA256 -Path $file).Hash.ToLower()
    $results += [pscustomobject]@{
        table    = $table
        rows     = $written
        expected = $expected
        ok       = $ok
        order    = $order
        file     = "$table.json"
        sha256   = $hash
        error    = $err
    }
    $color = if ($ok) { 'Gray' } else { 'Red' }
    $status = if ($ok) { 'OK' } elseif ($err) { "ПОМИЛКА: $err" } else { "РОЗБІЖНІСТЬ: очікувалось $expected" }
    Write-Host ("{0,-28} {1,8} рядків  {2}" -f $table, $written, $status) -ForegroundColor $color
}

# --- Маніфест (без ключа) -----------------------------------------------------------------------
$manifest = [pscustomobject]@{
    created_at   = (Get-Date).ToString('o')
    supabase_url = $SupabaseUrl
    key_kind     = $KeyKind
    tables_from  = $SchemaSource
    page_size    = $PageSize
    total_rows   = ($results | Measure-Object -Property rows -Sum).Sum
    all_ok       = -not $failed
    tables       = $results
}
[IO.File]::WriteAllText((Join-Path $OutDir 'manifest.json'), ($manifest | ConvertTo-Json -Depth 5), $utf8)
$countsText = ($results | ForEach-Object { "{0}`t{1}`t{2}" -f $_.table, $_.rows, $(if ($_.ok) { 'ok' } else { 'FAIL' }) }) -join "`r`n"
[IO.File]::WriteAllText((Join-Path $OutDir 'counts.txt'), $countsText + "`r`n", $utf8)

Write-Host ''
Write-Host "Копія: $OutDir  (таблиць: $($results.Count), рядків: $($manifest.total_rows))"
if ($failed) {
    Write-Host 'Є помилки або розбіжності — копія НЕПОВНА.' -ForegroundColor Red
    exit 1
}
Write-Host 'Усі таблиці збігаються з підрахунком сервера.' -ForegroundColor Green
exit 0
