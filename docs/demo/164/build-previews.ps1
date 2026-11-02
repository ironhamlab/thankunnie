$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$plan = @(Get-Content -LiteralPath (Join-Path $PSScriptRoot 'image-plan.json') -Raw -Encoding UTF8 | ConvertFrom-Json)
foreach ($kind in @('shop', 'treatment')) {
  $assets = @($plan | Where-Object { $_.kind -eq $kind -and (Test-Path -LiteralPath (Join-Path $PSScriptRoot ('images/' + $_.id + '.jpg'))) })
  if ($assets.Count -eq 0) { continue }
  $tileWidth = 256
  $imageHeight = if ($kind -eq 'shop') { 160 } else { 256 }
  $tileHeight = $imageHeight + 28
  $rows = [int][Math]::Ceiling($assets.Count / 5.0)
  $sheet = New-Object System.Drawing.Bitmap(($tileWidth * 5), ($rows * $tileHeight))
  $graphics = [System.Drawing.Graphics]::FromImage($sheet)
  $font = New-Object System.Drawing.Font('Arial', 11)
  try {
    $graphics.Clear([System.Drawing.Color]::White)
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    for ($index = 0; $index -lt $assets.Count; $index++) {
      $asset = $assets[$index]
      $x = ($index % 5) * $tileWidth
      $y = [int][Math]::Floor($index / 5.0) * $tileHeight
      $image = [System.Drawing.Image]::FromFile((Join-Path $PSScriptRoot ('images/' + $asset.id + '.jpg')))
      try {
        $scale = [Math]::Min([double]$tileWidth / $image.Width, [double]$imageHeight / $image.Height)
        $drawWidth = [int]($image.Width * $scale)
        $drawHeight = [int]($image.Height * $scale)
        $drawX = $x + [int](($tileWidth - $drawWidth) / 2)
        $drawY = $y + [int](($imageHeight - $drawHeight) / 2)
        $graphics.DrawImage($image, $drawX, $drawY, $drawWidth, $drawHeight)
      } finally { $image.Dispose() }
      $graphics.DrawString(($asset.id + ' / ' + $asset.category), $font, [System.Drawing.Brushes]::Black, ($x + 5), ($y + $imageHeight + 5))
    }
    $path = Join-Path $PSScriptRoot ($kind + '-preview.jpg')
    $sheet.Save($path, [System.Drawing.Imaging.ImageFormat]::Jpeg)
    Write-Output "$kind preview: $($assets.Count) actual generated images"
  } finally { $font.Dispose(); $graphics.Dispose(); $sheet.Dispose() }
}
