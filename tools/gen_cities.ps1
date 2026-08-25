# Generates additional Ukrainian city entries for Cities.kt from GeoNames data.
#
# Usage (one-time; requires previously downloaded GeoNames dumps):
#   .\tools\gen_cities.ps1 `
#     -CitiesTxt   <path>\cities5000.txt `
#     -AltNamesTxt <path>\alternateNames.txt `
#     -RepoKt      <repo>\app\src\main\java\ua\ukrainedrones\domain\Cities.kt
#
# Pipeline: UA places with population >= MinPop (default 10000), feature class P,
# excluding PPLX (city districts) -> Ukrainian name from tagged alternatenames
# (lang=uk, preferred first) -> admin1 mapped to this repo's Region stems ->
# 2 km dedupe against existing curated cities and within candidates (higher
# population wins) -> appended alphabetically inside each Region's list.
#
# Data source: GeoNames (https://www.geonames.org), licensed CC BY 4.0.
param(
    [Parameter(Mandatory = $true)] [string] $CitiesTxt,
    [Parameter(Mandatory = $true)] [string] $AltNamesTxt,
    [Parameter(Mandatory = $true)] [string] $RepoKt,
    [int] $MinPop = 10000,
    [double] $MinSeparationM = 2000,
    [double] $SameNameSeparationM = 25000
)

$ErrorActionPreference = 'Stop'

$admin1ToStem = @{
    '27' = 'Житомирськ'
    '26' = 'Запорізьк'
    '25' = 'Закарпатськ'
    '24' = 'Волинськ'
    '23' = 'Вінницьк'
    '22' = 'Тернопільськ'
    '21' = 'Сумськ'
    '20' = 'Севастополь'
    '19' = 'Рівненськ'
    '18' = 'Полтавськ'
    '17' = 'Одеськ'
    '16' = 'Миколаївськ'
    '15' = 'Львівськ'
    '14' = 'Луганськ'
    '13' = 'Київськ'
    '12' = 'Київськ'
    '11' = 'Крим'
    '10' = 'Кіровоградськ'
    '09' = 'Хмельницьк'
    '08' = 'Херсонськ'
    '07' = 'Харківськ'
    '06' = 'Івано-Франківськ'
    '05' = 'Донецьк'
    '04' = 'Дніпропетровськ'
    '03' = 'Чернівецьк'
    '02' = 'Чернігівськ'
    '01' = 'Черкаськ'
}

$okPlaceCodes = @('PPLC', 'PPLA', 'PPLA2', 'PPLA3', 'PPLA4', 'PPL')

function Get-HaversineM([double] $lat1, [double] $lon1, [double] $lat2, [double] $lon2) {
    $r = 6371000.0
    $p1 = $lat1 * [math]::PI / 180.0
    $p2 = $lat2 * [math]::PI / 180.0
    $dp = ($lat2 - $lat1) * [math]::PI / 180.0
    $dl = ($lon2 - $lon1) * [math]::PI / 180.0
    $a = [math]::Sin($dp / 2) * [math]::Sin($dp / 2) +
         [math]::Cos($p1) * [math]::Cos($p2) * [math]::Sin($dl / 2) * [math]::Sin($dl / 2)
    return 2 * $r * [math]::Asin([math]::Min(1.0, [math]::Sqrt($a)))
}

# --- 1. Candidates -----------------------------------------------------------

$candidates = @()
Get-Content -Encoding UTF8 $CitiesTxt | ForEach-Object {
    $f = $_ -split "`t"
    if ($f.Length -lt 15 -or $f[8] -ne 'UA') { return }
    if ($f[6] -ne 'P' -or $okPlaceCodes -notcontains $f[7]) { return }
    if ([int]::Parse($f[14]) -lt $MinPop) { return }
    $stem = $admin1ToStem[$f[10]]
    if (-not $stem) { Write-Warning "unmapped admin1 '$($f[10])' for $($f[1])"; return }
    $candidates += [pscustomobject]@{
        Id = [long]$f[0]; Name = $f[1]; Lat = [double]$f[4]; Lon = [double]$f[5]
        Pop = [int]$f[14]; Stem = $stem; NameUa = ''
    }
}

# --- 2. Ukrainian names from tagged alternatenames ---------------------------

$wantIds = @{}
foreach ($c in $candidates) { $wantIds[$c.Id] = $true }

$uaByBestScore = @{}   # id -> @{ score; value } ; higher score wins
$reader = New-Object System.IO.StreamReader($AltNamesTxt, [System.Text.Encoding]::UTF8)
try {
    while ($null -ne ($line = $reader.ReadLine())) {
        $f = $line -split "`t"
        if ($f.Length -lt 5) { continue }
        [long]$id = 0
        if (-not [long]::TryParse($f[1], [ref]$id)) { continue }
        if (-not $wantIds.ContainsKey($id)) { continue }
        if ($f[2] -ne 'uk') { continue }
        $score = 1
        if ($f[4] -eq '1') { $score += 10 }                 # preferred name
        if ($f[7] -eq '1') { $score -= 100 }                # historic name
        if ($f[3] -match '[\u0400-\u04FF]') { $score += 5 } # native script beats romanization
        $prev = $uaByBestScore[$id]
        if (-not $prev -or $score -gt $prev.score) {
            $uaByBestScore[$id] = @{ score = $score; value = $f[3] }
        }
    }
} finally { $reader.Close() }

# GeoNames rows with no tagged uk alternatename (verified manually).
$nameOverrides = @{
    "Novoazovs'k" = 'Новоазовськ'
    'Liubotyn'    = 'Люботин'
    "Sokolohirs'k" = 'Сокологірськ'
}

$noUaName = @()
foreach ($c in $candidates) {
    $hit = $uaByBestScore[$c.Id]
    if ($hit) { $c.NameUa = $hit.value }
    elseif ($nameOverrides.ContainsKey($c.Name)) { $c.NameUa = $nameOverrides[$c.Name] }
    else { $noUaName += $c.Name }
}
if ($noUaName.Count -gt 0) {
    Write-Warning ("no lang=uk alternatename for: " + ($noUaName -join ', '))
}

# --- 3. Existing curated set --------------------------------------------------

$kt = [System.IO.File]::ReadAllText($RepoKt, [System.Text.Encoding]::UTF8)
$existing = New-Object System.Collections.Generic.List[object]
foreach ($line in ($kt -split "`n")) {
    if ($line -match 'pop ~') { continue }  # ignore lines emitted by earlier runs
    foreach ($m in [regex]::Matches($line, 'City\("([^"]+)", (-?[\d.]+), (-?[\d.]+)')) {
        $existing.Add([pscustomobject]@{
            Name = $m.Groups[1].Value
            Lat = [double]$m.Groups[2].Value
            Lon = [double]$m.Groups[3].Value
        })
    }
}
$existingNames = @{}
foreach ($e in $existing) { $existingNames[$e.Name] = $true }

# --- 4. Dedupe ----------------------------------------------------------------

# Same-named settlements whose hand-curated coordinates drifted apart still
# must not duplicate: treat a name match within a wider radius as a dupe.
function Test-Duplicate([object] $c, [object] $other) {
    $d = Get-HaversineM $c.Lat $c.Lon $other.Lat $other.Lon
    if ($d -lt $MinSeparationM) { return $true }
    if ($c.NameUa -eq $other.Name -and $d -lt $SameNameSeparationM) { return $true }
    return $false
}

$kept = New-Object System.Collections.Generic.List[object]
$droppedNearExisting = New-Object System.Collections.Generic.List[string]
$droppedIntra = New-Object System.Collections.Generic.List[string]
foreach ($c in ($candidates | Sort-Object Pop -Descending)) {
    if (-not $c.NameUa) { continue }
    $near = $false
    foreach ($e in $existing) {
        if (Test-Duplicate $c $e) { $near = $true; break }
    }
    if ($near) { $droppedNearExisting.Add("$($c.Name) (~$($c.Pop))"); continue }
    foreach ($k in $kept) {
        if ((Get-HaversineM $c.Lat $c.Lon $k.Lat $k.Lon) -lt $MinSeparationM) {
            $droppedIntra.Add("$($c.Name) vs $($k.Name)")
            $near = $true; break
        }
    }
    if ($near) { continue }
    $kept.Add($c)
}

# --- 5. Emit into Cities.kt ----------------------------------------------------

function Format-KtCity([object] $c) {
    $lat = [math]::Round($c.Lat, 4); $lon = [math]::Round($c.Lon, 4)
    $latS = $lat.ToString([System.Globalization.CultureInfo]::InvariantCulture)
    $lonS = $lon.ToString([System.Globalization.CultureInfo]::InvariantCulture)
    return "            City(`"$($c.NameUa)`", $latS, $lonS, pop = $($c.Pop)), // pop ~$($c.Pop)"
}

$byStem = $kept | Group-Object Stem
$report = New-Object System.Collections.Generic.List[string]
$total = 0
# Match the repo file's own line endings for inserted text.
$nl = if ($kt.Contains("`r`n")) { "`r`n" } else { "`n" }
foreach ($g in ($byStem | Sort-Object Name)) {
    $lines = $g.Group | Sort-Object NameUa | ForEach-Object { Format-KtCity $_ }
    $pattern = 'Region\("' + [regex]::Escape($g.Name) + '", listOf\((\r?\n)((?s).*?)\r?\n(\s*\)\)[,]?)'
    $matchesHere = [regex]::Matches($kt, $pattern)
    if ($matchesHere.Count -ne 1) {
        $codes = ($g.Name.ToCharArray() | ForEach-Object { [int]$_ }) -join ','
        throw "region '$($g.Name)' [$codes] matched $($matchesHere.Count) times, expected 1"
    }
    $m = $matchesHere[0]
    $body = $m.Groups[2].Value
    if ($body.Contains('pop ~')) {
        # Regenerating over an earlier run: strip previous generated lines first.
        $body = (($body -split "\r?\n") | Where-Object { $_ -notmatch 'pop ~' }) -join $nl
    }
    # Kotlin needs the previous line comma-separated before appended entries.
    $bodyLines = @($body -split "\r?\n" | Where-Object { $_.Trim() -ne '' })
    $lastLine = $bodyLines[$bodyLines.Count - 1]
    if ($lastLine -notmatch ',\s*$') {
        $bodyLines[$bodyLines.Count - 1] = $lastLine.TrimEnd() + ','
    }
    # Re-emit the region header: the regex match starts at 'Region("' and the
    # replacement must preserve everything the pattern consumed.
    $header = 'Region("' + $g.Name + '", listOf('
    $newRegion = $header + $m.Groups[1].Value + ($bodyLines -join $nl) + $nl +
        ($lines -join $nl) + $nl + $m.Groups[3].Value
    $kt = $kt.Remove($m.Index, $m.Length).Insert($m.Index, $newRegion)
    $total += $g.Count
    $report.Add(("  {0,-22} +{1}" -f $g.Name, $g.Count))
}

Copy-Item $RepoKt "$RepoKt.bak" -Force
[System.IO.File]::WriteAllText($RepoKt, $kt, (New-Object System.Text.UTF8Encoding($false)))

$nameCollisions = $kept | Where-Object { $existingNames.ContainsKey($_.NameUa) }
Write-Host ""
Write-Host "Candidates: $($candidates.Count), kept: $total, dropped near existing: $($droppedNearExisting.Count), dropped intra: $($droppedIntra.Count)"
Write-Host ($report -join "`n")
if ($nameCollisions) {
    Write-Host "Name collisions with curated set (allowed, coords differ): $(($nameCollisions | ForEach-Object NameUa) -join ', ')"
}
