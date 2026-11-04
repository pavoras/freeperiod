<#
  Creates the FreePeriod. release key for the F-Droid/GitHub APK OUTSIDE the repository. Run it yourself; keytool
  asks for the password interactively (it is never stored by this script). Unlike the Play upload key, nobody can
  reset this key: if it is lost, F-Droid users have to reinstall. Back it up privately.
  Result: %USERPROFILE%\dev-tools\keys\freeperiod-release.jks + freeperiod-release.properties template.
#>
$keys = Join-Path $env:USERPROFILE 'dev-tools\keys'
New-Item -ItemType Directory -Force $keys | Out-Null
$store = Join-Path $keys 'freeperiod-release.jks'
if (Test-Path $store) { throw "Keystore already exists: $store" }
$keytool = Join-Path $env:USERPROFILE 'dev-tools\jdk17\bin\keytool.exe'
# PKCS12: one password for store and key.
& $keytool -genkeypair -v -storetype PKCS12 -keystore $store -alias freeperiod-release -keyalg RSA -keysize 4096 `
    -validity 10000 -dname 'CN=FreePeriod, O=Pavoras'
$props = Join-Path $keys 'freeperiod-release.properties'
@"
storeFile=freeperiod-release.jks
storePassword=ENTER_HERE
keyAlias=freeperiod-release
"@ | Set-Content -Encoding ascii $props
Write-Output "Done. Fill in the password in $props (keep this file private, never commit it)."
