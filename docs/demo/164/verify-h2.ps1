param([switch]$RequireImages)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$h2 = Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/com.h2database/h2') -Recurse -Filter 'h2-*.jar' |
  Where-Object Name -NotLike '*-sources.jar' | Sort-Object FullName -Descending | Select-Object -First 1
if (-not $h2) { throw 'Existing Gradle H2 runtime jar not found. No new dependency was installed.' }
$files = @('docs/erd/01_schema_ddl.sql', 'docs/erd/02_init_data_dml.sql', 'docs/erd/03_demo_data_dml.sql',
  'docs/demo/164/verify-data.sql', 'docs/demo/164/demo164-additions.sql', 'docs/demo/164/verify-data.sql')
$sql = foreach ($file in $files) {
  $absolute = (Join-Path $root $file).Replace('\', '/').Replace("'", "''")
  "RUNSCRIPT FROM '$absolute' CHARSET 'UTF-8';"
}
if ($RequireImages) {
  $sql += @"
SELECT 1 / CASE WHEN COUNT(*) = 50 THEN 1 ELSE 0 END FROM SHOP
WHERE LOGIN_ID LIKE 'demo164_s%' AND IMAGE_URL LIKE '/api/v1/shops/%/images/%.jpg';
SELECT 1 / CASE WHEN COUNT(*) = 150 THEN 1 ELSE 0 END FROM TREATMENT T JOIN SHOP S ON S.SHOP_ID = T.SHOP_ID
WHERE S.LOGIN_ID LIKE 'demo164_s%' AND T.IMAGE_URL LIKE '/api/v1/shops/%/treatments/%/images/%.jpg';
"@
}
$sql += @"
-- 시드 이후 앱과 같은 시퀀스로 새 행을 생성해 실제 PK 충돌 유무를 확인한다. 임시 메모리 DB에만 실행한다.
INSERT INTO SHOP (SHOP_ID,CATEGORY_ID,LOGIN_ID,PASSWORD_HASH,NAME,REGION_CODE,ADDRESS,LATITUDE,LONGITUDE,PHONE,STATUS,CREATED_AT,UPDATED_AT)
SELECT SEQ_SHOP.NEXTVAL,1,'sequence_probe_164','CHANGE_ME','PK 시퀀스 검사','11680',ADDRESS,LATITUDE,LONGITUDE,'02-0000-0000','ACTIVE',
 TIMESTAMP '2026-11-02 09:00:00',TIMESTAMP '2026-11-02 09:00:00' FROM SHOP WHERE LOGIN_ID='demo164_s01';
INSERT INTO TREATMENT (TREATMENT_ID,SHOP_ID,NAME,REGULAR_PRICE,PRICE,LAST_MINUTE_PRICE,DURATION_MINUTES,TREATMENT_TYPE,STATUS,CREATED_AT,UPDATED_AT)
SELECT SEQ_TREATMENT.NEXTVAL,SHOP_ID,'PK 시퀀스 검사',10000,10000,10000,30,'NORMAL','ACTIVE',
 TIMESTAMP '2026-11-02 09:00:00',TIMESTAMP '2026-11-02 09:00:00' FROM SHOP WHERE LOGIN_ID='sequence_probe_164';
"@
$wrapper = Join-Path $root 'backend/build/demo164-validation.sql'
[System.IO.File]::WriteAllText($wrapper, ($sql -join "`n"), (New-Object System.Text.UTF8Encoding($false)))
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java).Source }
& $java -cp $h2.FullName org.h2.tools.RunScript -url 'jdbc:h2:mem:demo164_validation;MODE=Oracle;DB_CLOSE_DELAY=-1' -user sa -script $wrapper
if ($LASTEXITCODE -ne 0) { throw 'H2 SQL execution or data assertions failed' }
Write-Output "H2 PASS: full schema + initial + demo SQL, assertions, additions rerun, assertions, sequence inserts. Images required: $RequireImages"
Write-Output 'This does not verify Oracle 21c XE execution. No existing database was used.'
