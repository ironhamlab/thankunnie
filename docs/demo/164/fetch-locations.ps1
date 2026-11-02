$ErrorActionPreference = 'Stop'
$plan = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'data-plan.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$directory = Join-Path $PSScriptRoot 'location-sources'
[System.IO.Directory]::CreateDirectory($directory) | Out-Null
foreach ($district in $plan.districts) {
  $name = $district[0]
  $code = $district[1]
  $required = [int]$district[2]
  $path = Join-Path $directory ($code + '.json')
  if (Test-Path -LiteralPath $path) { Write-Output "Cached $code"; continue }
  $query = '[out:json][timeout:35];area["name"="서울특별시"]["boundary"="administrative"]->.seoul;rel(area.seoul)["boundary"="administrative"]["name"="' + $name + '"];map_to_area->.district;nwr(area.district)["addr:street"]["addr:housenumber"];out center tags ' + ($required * 3) + ';'
  $uri = 'https://overpass-api.de/api/interpreter?data=' + [Uri]::EscapeDataString($query)
  $response = Invoke-RestMethod -Uri $uri -TimeoutSec 55 -Headers @{ 'User-Agent' = 'thankunnie-UNNIE-164-demo-preparation' }
  $locations = @($response.elements | Where-Object { $_.lat -or $_.center.lat })
  if ($locations.Count -lt $required) { throw "Not enough verified address locations for $name" }
  $result = [PSCustomObject]@{
    district = $name; regionCode = $code; source = $uri
    attribution = 'OpenStreetMap contributors, ODbL 1.0; https://www.openstreetmap.org/copyright'
    checkedOn = '2026-11-02'
    locations = @($locations | ForEach-Object {
      [PSCustomObject]@{
        osmType = $_.type; osmId = $_.id
        street = $_.tags.'addr:street'; houseNumber = $_.tags.'addr:housenumber'
        latitude = if ($_.lat) { $_.lat } else { $_.center.lat }
        longitude = if ($_.lon) { $_.lon } else { $_.center.lon }
      }
    })
  }
  [System.IO.File]::WriteAllText($path, (ConvertTo-Json -InputObject $result -Depth 8) + "`n", (New-Object System.Text.UTF8Encoding($false)))
  Write-Output "Checked $name ($code): $($locations.Count) public address points"
}
