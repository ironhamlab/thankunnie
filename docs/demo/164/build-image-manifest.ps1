param([switch]$ShopsOnly)

$ErrorActionPreference = 'Stop'
$plan = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'data-plan.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$prompts = @(Get-Content -LiteralPath (Join-Path $PSScriptRoot 'image-plan.json') -Raw -Encoding UTF8 | ConvertFrom-Json)
$previousPath = Join-Path $PSScriptRoot 'image-manifest.json'
$previous = if (Test-Path -LiteralPath $previousPath) { @(Get-Content -LiteralPath $previousPath -Raw -Encoding UTF8 | ConvertFrom-Json) } else { @() }
$shops = @($plan.shops)
$treatments = @($shops | ForEach-Object { $_.treatments })
if ($shops.Count -ne 50 -or @($treatments | Where-Object type -eq 'NORMAL').Count -ne 130 -or
    @($treatments | Where-Object type -eq 'MODEL').Count -ne 20) { throw 'Unexpected data counts' }
if (@($shops.regionCode | Select-Object -Unique).Count -ne 25) { throw 'Expected all 25 Seoul districts' }
if (@($shops | Where-Object normalCount -lt 2).Count) { throw 'Every shop must have two normal treatments' }
if (@($shops | Where-Object modelCount -gt 0).Count -ne 16) { throw 'Expected models at 16 shops' }
if ($prompts.Count -ne 90) { throw 'Expected 90 image prompts' }
$hashes = @{}
$requiredPrompts = @($prompts | Where-Object { -not $ShopsOnly -or $_.kind -eq 'shop' })
foreach ($prompt in $requiredPrompts) {
  $path = Join-Path $PSScriptRoot ('images/' + $prompt.id + '.jpg')
  if (-not (Test-Path -LiteralPath $path)) { throw "Image is not generated yet: $($prompt.id)" }
  $file = Get-Item -LiteralPath $path
  if ($file.Length -eq 0 -or $file.Length -gt 5MB) { throw "Invalid image size: $($prompt.id)" }
  $hashes[$prompt.id] = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
}
foreach ($group in ($treatments | Group-Object assetId)) {
  if ($group.Count -gt 5) { throw "Image reuse exceeds 5: $($group.Name)" }
}
$manifest = @()
foreach ($shop in $shops) {
  $slots = @([PSCustomObject]@{ kind = 'shop'; name = $shop.name; type = $null; assetId = $shop.assetId })
  if (-not $ShopsOnly) { $slots += @($shop.treatments | ForEach-Object {
    [PSCustomObject]@{ kind = 'treatment'; name = $_.name; type = $_.type; assetId = $_.assetId }
  }) }
  foreach ($slot in $slots) {
    $asset = @($prompts | Where-Object id -eq $slot.assetId)
    if ($asset.Count -ne 1 -or $asset[0].category -ne $shop.category) { throw 'Image category mismatch' }
    $identity = "UNNIE-164:$($shop.loginId):$($slot.kind):$($slot.name):$($hashes[$slot.assetId])"
    $algorithm = [System.Security.Cryptography.SHA256]::Create()
    try { $digest = $algorithm.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($identity)) }
    finally { $algorithm.Dispose() }
    $hex = -join ($digest[0..15] | ForEach-Object { $_.ToString('x2') })
    $fileName = '{0}-{1}-5{2}-a{3}-{4}.jpg' -f $hex.Substring(0,8), $hex.Substring(8,4),
      $hex.Substring(13,3), $hex.Substring(17,3), $hex.Substring(20,12)
    # 매장 표시명이 바뀌어도 같은 슬롯·같은 파일이면 이미 저장한 UUID를 유지한다.
    $existing = @($previous | Where-Object {
      $_.loginId -eq $shop.loginId -and $_.kind -eq $slot.kind -and $_.sha256 -eq $hashes[$slot.assetId] -and
      ($slot.kind -eq 'shop' -or ($_.treatmentName -eq $slot.name -and $_.treatmentType -eq $slot.type))
    })
    if ($existing.Count -eq 1) { $fileName = $existing[0].fileName }
    $manifest += [PSCustomObject]@{
      loginId = $shop.loginId
      kind = $slot.kind
      treatmentName = if ($slot.kind -eq 'treatment') { $slot.name } else { $null }
      treatmentType = $slot.type
      assetId = $slot.assetId
      fileName = $fileName
      sha256 = $hashes[$slot.assetId]
    }
  }
}
$expected = if ($ShopsOnly) { 50 } else { 200 }
if ($manifest.Count -ne $expected -or @($manifest.fileName | Select-Object -Unique).Count -ne $expected) { throw "Expected $expected unique S3 keys" }
$output = Join-Path $PSScriptRoot $(if ($ShopsOnly) { 'shop-image-manifest.json' } else { 'image-manifest.json' })
$json = ConvertTo-Json -InputObject $manifest -Depth 5
[System.IO.File]::WriteAllText($output, $json + "`n", (New-Object System.Text.UTF8Encoding($false)))
Write-Output "Validated data distribution, $($requiredPrompts.Count) actual image files and $expected unique object keys."
