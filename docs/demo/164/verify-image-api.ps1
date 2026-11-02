#Requires -Version 7.2
param(
  [string]$BaseUrl = 'http://127.0.0.1:18084',
  [long]$FirstShopId = 1000
)

# 새 H2 검증 앱에서 실행한다. 실제 DB의 시퀀스 값이 다르면 첫 매장 ID를 명시해야 한다.
# 모든 요청은 GET이며 앱 데이터와 S3 객체를 변경하지 않는다.
$ErrorActionPreference = 'Stop'
$plan = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'data-plan.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$manifest = @(Get-Content -LiteralPath (Join-Path $PSScriptRoot 'image-manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json)
$requests = @()
foreach ($shop in $plan.shops) {
  $shopId = $FirstShopId + $shop.number - 1
  $detail = (Invoke-RestMethod -Uri "$BaseUrl/api/v1/explore/shops/$shopId" -TimeoutSec 30).data
  if ($detail.shopName -ne $shop.name -or $detail.regionCode -ne $shop.regionCode -or
      $detail.categoryId -ne $shop.categoryId -or @($detail.treatments).Count -ne ($shop.normalCount + $shop.modelCount)) {
    throw "Shop identity/count mismatch: $($shop.loginId). Use the correct first ID for this fresh demo database."
  }
  $slots = @($manifest | Where-Object loginId -eq $shop.loginId)
  foreach ($slot in $slots) {
    if ($slot.kind -eq 'shop') {
      $url = $detail.imageUrl
      $expected = "/api/v1/shops/$shopId/images/$($slot.fileName)"
    } else {
      $treatment = @($detail.treatments | Where-Object { $_.name -eq $slot.treatmentName -and $_.treatmentType -eq $slot.treatmentType })
      if ($treatment.Count -ne 1) { throw 'Treatment identity mismatch' }
      $url = $treatment[0].imageUrl
      $expected = "/api/v1/shops/$shopId/treatments/$($treatment[0].treatmentId)/images/$($slot.fileName)"
    }
    if ($url -ne $expected) { throw "Image URL mismatch: $($shop.loginId) $($slot.assetId)" }
    $requests += [PSCustomObject]@{ url = $BaseUrl + $url; sha256 = $slot.sha256 }
  }
}
if ($requests.Count -ne 200) { throw 'Expected 200 image requests' }
$results = @($requests | ForEach-Object -Parallel {
  $request = $_
  $client = [System.Net.Http.HttpClient]::new()
  $client.Timeout = [TimeSpan]::FromSeconds(40)
  try {
    $response = $client.GetAsync($request.url).GetAwaiter().GetResult()
    try {
      $response.EnsureSuccessStatusCode() | Out-Null
      if ($response.Content.Headers.ContentType.MediaType -ne 'image/jpeg') { throw 'Expected image/jpeg' }
      $bytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
      $hash = [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
      if ($hash -ne $request.sha256) { throw 'Image bytes differ from the generated file' }
      [PSCustomObject]@{ url = $request.url; success = $true; bytes = $bytes.Length }
    } finally { $response.Dispose() }
  } catch {
    [PSCustomObject]@{ url = $request.url; success = $false; error = $_.Exception.Message }
  } finally { $client.Dispose() }
} -ThrottleLimit 8)
$failures = @($results | Where-Object { -not $_.success })
if ($failures.Count -or $results.Count -ne 200) {
  $failures | Format-Table url,error
  throw 'Image API verification failed'
}
$record = [PSCustomObject]@{ baseUrl = $BaseUrl; shops = 50; images = 200; failures = 0; checkedAtUtc = [DateTimeOffset]::UtcNow.ToString('o') }
[System.IO.File]::WriteAllText((Join-Path $PSScriptRoot 'api-verification.json'),
  (ConvertTo-Json -InputObject $record) + "`n", (New-Object System.Text.UTF8Encoding($false)))
Write-Output 'PASS: 50 shop detail responses and all 200 public image APIs; every JPEG byte hash matches its generated source.'
