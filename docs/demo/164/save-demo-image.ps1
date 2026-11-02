param(
  [Parameter(Mandatory = $true)][string]$Source,
  [Parameter(Mandatory = $true)][string]$AssetId,
  [ValidateSet('shop', 'treatment')][string]$Kind = 'shop'
)

$ErrorActionPreference = 'Stop'
if ($AssetId -notmatch '^(shop|treatment)-[0-9]{3}$') { throw 'Invalid asset ID' }
Add-Type -AssemblyName System.Drawing
$destinationDirectory = Join-Path $PSScriptRoot 'images'
[System.IO.Directory]::CreateDirectory($destinationDirectory) | Out-Null
$destination = Join-Path $destinationDirectory ($AssetId + '.jpg')
if (Test-Path -LiteralPath $destination) { throw "Asset already exists: $destination" }
$sourceImage = [System.Drawing.Image]::FromFile($Source)
try {
  $maximumEdge = if ($Kind -eq 'shop') { 1280 } else { 1024 }
  $scale = [Math]::Min([double]1, [double]$maximumEdge / [Math]::Max($sourceImage.Width, $sourceImage.Height))
  $width = [Math]::Max(1, [int]($sourceImage.Width * $scale))
  $height = [Math]::Max(1, [int]($sourceImage.Height * $scale))
  $bitmap = New-Object System.Drawing.Bitmap($width, $height)
  try {
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
      $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
      $graphics.DrawImage($sourceImage, 0, 0, $width, $height)
    } finally { $graphics.Dispose() }
    $codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object MimeType -eq 'image/jpeg'
    $parameters = New-Object System.Drawing.Imaging.EncoderParameters(1)
    try {
      $parameters.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter([System.Drawing.Imaging.Encoder]::Quality, [long]85)
      $bitmap.Save($destination, $codec, $parameters)
    } finally { $parameters.Dispose() }
  } finally { $bitmap.Dispose() }
} finally { $sourceImage.Dispose() }
$file = Get-Item -LiteralPath $destination
if ($file.Length -gt 5MB) { throw 'Image exceeds application upload limit' }
Write-Output "$AssetId $width x $height $($file.Length) bytes"
