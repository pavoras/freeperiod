<#
  Builds the F-Droid APK of a release tag exactly as F-Droid's build server does (same bytes), then signs it with the
  release key, so F-Droid can publish our signed APK (reproducible build).
  What makes a Windows build identical to F-Droid's Linux build: a fresh clone without CRLF conversion, JDK 21, and
  Unix line separators for R8 (it writes META-INF/services files with the platform line separator).
  Usage: powershell -File tools\build-fdroid-apk.ps1 -Tag v1.4.0 [-Compare <unsigned APK from F-Droid's CI>]
  The signing properties are read here and never printed.
#>
param([Parameter(Mandatory)][string] $Tag, [string] $Compare)
$ErrorActionPreference = 'Stop'
$tools = Join-Path $env:USERPROFILE 'dev-tools'
$work = Join-Path $env:TEMP "fp-repro-$Tag"
if (Test-Path $work) { Remove-Item -Recurse -Force $work }
git clone -q -c core.autocrlf=false --branch $Tag https://github.com/pavoras/freeperiod.git $work
if ($LASTEXITCODE) { throw "clone failed" }

$env:JAVA_HOME = (Get-ChildItem (Join-Path $tools 'jdk21') -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = Join-Path $tools 'android-sdk'
$env:GRADLE_USER_HOME = Join-Path $tools 'gradle-home'
$env:JDK_JAVA_OPTIONS = "`"-Dline.separator=`n`""
try {
    & (Join-Path $tools 'gradle-8.11.1\bin\gradle.bat') -q --no-daemon -p (Join-Path $work 'app') assembleFdroidRelease
    if ($LASTEXITCODE) { throw "gradle failed" }
} finally { Remove-Item Env:JDK_JAVA_OPTIONS }

$unsigned = Join-Path $work 'app\build\outputs\apk\fdroid\release\app-fdroid-release-unsigned.apk'
if ($Compare) {
    if ((Get-FileHash $unsigned).Hash -ne (Get-FileHash $Compare).Hash) { throw "NOT identical to $Compare" }
    Write-Output "Identical to F-Droid's build."
}

$keys = Join-Path $tools 'keys'
$props = @{}
Get-Content (Join-Path $keys 'freeperiod-release.properties') | Where-Object { $_ -match '=' } |
    ForEach-Object { $k, $v = $_ -split '=', 2; $props[$k.Trim()] = $v.Trim() }
$buildTools = (Get-ChildItem (Join-Path $env:ANDROID_HOME 'build-tools') -Directory | Sort-Object { [version]$_.Name } | Select-Object -Last 1).FullName
$version = $Tag.TrimStart('v')
$signed = Join-Path $work "FreePeriod-$version-fdroid.apk"
$env:FP_KS_PASS = $props['storePassword']
try {
    & (Join-Path $buildTools 'apksigner.bat') sign --ks (Join-Path $keys $props['storeFile']) --ks-key-alias $props['keyAlias'] `
        --ks-pass env:FP_KS_PASS --out $signed $unsigned
    if ($LASTEXITCODE) { throw "apksigner failed" }
} finally { Remove-Item Env:FP_KS_PASS }
& (Join-Path $buildTools 'apksigner.bat') verify --print-certs $signed | Select-String 'SHA-256'
Write-Output "Signed APK: $signed"
