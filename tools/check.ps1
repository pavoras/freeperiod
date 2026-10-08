<#
  FreePeriod. gate.
  -Schnell   engine tests + app compile (offline; safe inside the Codex sandbox)
  -Voll      + app unit/Robolectric tests (Roborazzi compare), lint, release manifest INTERNET check
  -Aufnehmen record Roborazzi references instead of comparing (Claude only)
  -Online    allow Gradle network access (Claude only, to fill the shared cache)
  -Tests     optional Gradle --tests filter for :app:testDebugUnitTest
  Exit code 0 = green. Full log in .tools/check-<mode>.log
#>
param(
    [switch] $Schnell,
    [switch] $Voll,
    [switch] $Aufnehmen,
    [switch] $Online,
    [string] $Tests
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$tools = Join-Path $env:USERPROFILE 'dev-tools'
$env:JAVA_HOME = Join-Path $tools 'jdk17'
$env:GRADLE_USER_HOME = Join-Path $tools 'gradle-home'
$env:ANDROID_HOME = Join-Path $tools 'android-sdk'
$gradle = Join-Path $tools 'gradle-8.11.1\bin\gradle.bat'

if (-not ($Schnell -or $Voll -or $Aufnehmen)) { $Schnell = $true }
$mode = if ($Aufnehmen) { 'aufnehmen' } elseif ($Voll) { 'voll' } else { 'schnell' }

$tasks = @(':engine:test', ':app:assembleDebug', ':app:compileDebugUnitTestKotlin')
$extra = @()
if ($Voll -or $Aufnehmen) {
    $tasks += ':app:testDebugUnitTest'
    if ($Voll) { $tasks += @(':app:lintDebug', ':app:assembleRelease') }
    $extra += if ($Aufnehmen) { '-Proborazzi.test.record=true' } else { '-Proborazzi.test.compare=true' }
    # A filter applies to the task right before it, so run only the unit tests then.
    if ($Tests) { $tasks = @(':app:testDebugUnitTest', '--tests', $Tests) }
}
$gargs = @('--console=plain', '-Porg.gradle.java.installations.auto-download=false') + $extra + $tasks
if (-not $Online) { $gargs = @('--offline') + $gargs }

New-Item -ItemType Directory -Force (Join-Path $repo '.tools') | Out-Null
$log = Join-Path $repo ".tools\check-$mode.log"
$proc = Start-Process -FilePath $gradle -ArgumentList $gargs -WorkingDirectory $repo -NoNewWindow -Wait -PassThru `
    -RedirectStandardOutput $log -RedirectStandardError "$log.err"
$code = $proc.ExitCode
Add-Content -Path $log -Value (Get-Content "$log.err" -Raw -ErrorAction SilentlyContinue)
Remove-Item "$log.err" -ErrorAction SilentlyContinue

if ($code -eq 0 -and $Voll) {
    $manifests = Get-ChildItem (Join-Path $repo 'app\build\intermediates') -Recurse -Filter AndroidManifest.xml |
        Where-Object { $_.FullName -match 'merged_manifest\\release' }
    if (-not $manifests) { Write-Output 'FAIL: merged release manifest not found'; exit 2 }
    if ($manifests | Select-String -SimpleMatch 'android.permission.INTERNET') {
        Write-Output 'FAIL: release manifest contains android.permission.INTERNET'; exit 3
    }
    Write-Output 'OK: no INTERNET permission in release manifest'
}

$results = Get-ChildItem $repo -Recurse -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match '\\build\\test-results\\' }
if ($results) {
    $sum = @{ tests = 0; failures = 0; errors = 0; skipped = 0 }
    foreach ($r in $results) { $x = [xml](Get-Content $r.FullName -Raw); foreach ($k in @($sum.Keys)) { $sum[$k] += [int]$x.testsuite.$k } }
    Write-Output ("Tests: {0} run, {1} failed, {2} errors, {3} skipped" -f $sum.tests, $sum.failures, $sum.errors, $sum.skipped)
}
if ($code -ne 0) {
    Write-Output "FAIL ($mode), exit $code. Last lines of $log :"
    Get-Content $log -Tail 40
} else { Write-Output "GREEN ($mode)" }
exit $code


