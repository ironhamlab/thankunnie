#Requires -Version 7.0
param(
  [Parameter(Mandatory = $true)][string]$Bucket,
  [Parameter(Mandatory = $true)][string]$Region,
  [Parameter(Mandatory = $true)][string]$Manifest,
  [string]$Prefix = 'shop-images/',
  [string]$Profile,
  [string]$CaBundle,
  [ValidateRange(1, 8)][int]$Concurrency = 4,
  [switch]$Apply
)

# 기본 실행은 검사·미리보기만 한다. 기존 객체 덮어쓰기나 객체 삭제는 하지 않는다.
$ErrorActionPreference = 'Stop'
if ($Bucket -notmatch '^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$') { throw 'Invalid bucket name' }
if ($Region -notmatch '^[a-z]{2}-[a-z]+-[0-9]+$') { throw 'Invalid region' }
if ($Prefix.Length -gt 300 -or $Prefix -notmatch '^[a-zA-Z0-9][a-zA-Z0-9/_-]*$') { throw 'Invalid prefix' }
if (-not $Prefix.EndsWith('/')) { $Prefix += '/' }
$manifestPath = (Resolve-Path -LiteralPath $Manifest).Path
$manifestDirectory = Split-Path -Parent $manifestPath
$entries = @(Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json)
if ($entries.Count -eq 0) { throw 'Empty manifest' }
$keys = @{}
$validated = @()
foreach ($entry in $entries) {
  if ($entry.fileName -notmatch '^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\.jpg$') { throw 'Invalid application filename' }
  if ($entry.assetId -notmatch '^(shop|treatment)-[0-9]{3}$') { throw 'Invalid asset ID' }
  if ($keys.ContainsKey($entry.fileName)) { throw 'Each entity must own a separate object key' }
  $keys[$entry.fileName] = $true
  $path = Join-Path $manifestDirectory ('images/' + $entry.assetId + '.jpg')
  $file = Get-Item -LiteralPath $path
  if ($file.Length -eq 0 -or $file.Length -gt 5MB) { throw "Invalid image size: $($entry.assetId)" }
  $hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
  if ($hash -ne $entry.sha256) { throw "Manifest hash mismatch: $($entry.assetId)" }
  $validated += [PSCustomObject]@{ path = $file.FullName; key = $Prefix + $entry.fileName; sha256 = $hash }
}
Write-Output "Validated $($validated.Count) objects for s3://$Bucket/$Prefix ($Region)"
if (-not $Apply) {
  $validated | Select-Object key, path
  Write-Output 'Preview only. Upload with -Apply after confirming the target bucket.'
  return
}

$awsArguments = @('--region', $Region, '--no-cli-pager', '--cli-connect-timeout', '10', '--cli-read-timeout', '30')
if ($Profile) { $awsArguments += @('--profile', $Profile) }
if ($CaBundle) {
  $awsArguments += @('--ca-bundle', (Resolve-Path -LiteralPath $CaBundle).Path)
}
$results = @($validated | ForEach-Object -Parallel {
  $ErrorActionPreference = 'Stop'
  $item = $_
  $Bucket = $using:Bucket
  $awsArguments = $using:awsArguments
  try {
  $headArguments = @('s3api', 'head-object', '--bucket', $Bucket, '--key', $item.key,
    '--query', 'Metadata.sha256', '--output', 'text') + $awsArguments
  $existingHash = & aws @headArguments 2>$null
  if ($LASTEXITCODE -eq 0) {
    if (($existingHash | Out-String).Trim() -ne $item.sha256) {
      throw "Existing object differs from manifest; refusing overwrite: $($item.key)"
    }
    Write-Host "Already uploaded and checked $($item.key)"
    return [PSCustomObject]@{ key = $item.key; success = $true }
  }
  # If-None-Match로 AWS에서 기존 키의 덮어쓰기를 원자적으로 막는다.
  $putArguments = @('s3api', 'put-object', '--bucket', $Bucket, '--key', $item.key,
    '--body', $item.path, '--content-type', 'image/jpeg', '--if-none-match', '*',
    '--metadata', "demo-issue=UNNIE-164,sha256=$($item.sha256)") + $awsArguments
  $null = & aws @putArguments
  if ($LASTEXITCODE -ne 0) { throw "Upload failed or object already exists: $($item.key). No DB changes were made." }
  $remoteHash = & aws @headArguments
  if ($LASTEXITCODE -ne 0 -or ($remoteHash | Out-String).Trim() -ne $item.sha256) {
    throw "Remote metadata verification failed: $($item.key)"
  }
  Write-Host "Uploaded and checked $($item.key)"
  [PSCustomObject]@{ key = $item.key; success = $true }
  } catch {
    [PSCustomObject]@{ key = $item.key; success = $false; error = $_.Exception.Message }
  }
} -ThrottleLimit $Concurrency)
$failures = @($results | Where-Object { -not $_.success })
if ($results.Count -ne $validated.Count -or $failures.Count) {
  $failures | Format-Table key, error
  throw 'Some objects failed verification. No DB changes were made; rerun to resume safely.'
}
$verification = [PSCustomObject]@{
  bucket = $Bucket
  region = $Region
  prefix = $Prefix
  count = $validated.Count
  checkedAtUtc = [DateTimeOffset]::UtcNow.ToString('o')
  objects = @($validated | Select-Object key, sha256)
}
[System.IO.File]::WriteAllText(($manifestPath + '.s3-verification.json'),
  (ConvertTo-Json -InputObject $verification -Depth 6) + "`n", (New-Object System.Text.UTF8Encoding($false)))
Write-Output 'Upload complete. Apply the image SQL only to the separately confirmed demo database.'
