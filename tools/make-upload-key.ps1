<#
  Creates the FreePeriod. upload key OUTSIDE the repository. Run it yourself; keytool asks for the
  passwords interactively (they are never stored by this script).
  Result: %USERPROFILE%\dev-tools\keys\freeperiod-upload.jks + freeperiod-signing.properties template.
#>
$keys = Join-Path $env:USERPROFILE 'dev-tools\keys'
New-Item -ItemType Directory -Force $keys | Out-Null
$store = Join-Path $keys 'freeperiod-upload.jks'
if (Test-Path $store) { throw "Keystore already exists: $store" }
$keytool = Join-Path $env:USERPROFILE 'dev-tools\jdk17\bin\keytool.exe'
& $keytool -genkeypair -v -keystore $store -alias freeperiod-upload -keyalg RSA -keysize 4096 -validity 10000
$props = Join-Path $keys 'freeperiod-signing.properties'
@"
storeFile=$($store.Replace('\','/'))
storePassword=ENTER_HERE
keyAlias=freeperiod-upload
keyPassword=ENTER_HERE
"@ | Set-Content -Encoding ascii $props
Write-Output "Done. Fill in the two passwords in $props (keep this file private, never commit it)."
