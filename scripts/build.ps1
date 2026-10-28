param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [string]$GradleHome = $env:GRADLE_HOME,
    [switch]$DeviceTests
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))) { throw 'Provide -JavaHome with a JDK 17 directory.' }
if (-not $AndroidSdk -or -not (Test-Path -LiteralPath (Join-Path $AndroidSdk 'platforms\android-35\android.jar'))) { throw 'Provide -AndroidSdk with Android SDK platform 35.' }
$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $AndroidSdk
$env:LIVING_APPS_BUILD_ROOT = Join-Path $env:LOCALAPPDATA 'LivingAppsDevelopment\build'
# Java properties use Latin-1 and Unicode escapes, including in Chinese paths.
$taskSdkValue = $AndroidSdk.Replace('\','/')
$taskEscaped = -join ($taskSdkValue.ToCharArray() | ForEach-Object { if ([int]$_ -gt 127) { '\u{0:x4}' -f [int]$_ } else { [string]$_ } })
Set-Content -LiteralPath (Join-Path $taskRoot 'local.properties') -Value ('sdk.dir=' + $taskEscaped) -Encoding ASCII
$taskGradle = if ($GradleHome) { Join-Path $GradleHome 'bin\gradle.bat' } else { Join-Path $taskRoot 'gradlew.bat' }
Push-Location -LiteralPath $taskRoot
try {
    $taskTargets = @('testDebugUnitTest','lintDebug','assembleDebug','assembleDebugAndroidTest')
    if ($DeviceTests) { $taskTargets += 'connectedDebugAndroidTest' }
    & $taskGradle @taskTargets --console plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
    $taskDist = Join-Path $taskRoot 'dist'
    New-Item -ItemType Directory -Path $taskDist -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $env:LIVING_APPS_BUILD_ROOT 'app\outputs\apk\debug\app-debug.apk') -Destination (Join-Path $taskDist 'living-apps-0.2.0-debug.apk') -Force
    Get-FileHash -LiteralPath (Join-Path $taskDist 'living-apps-0.2.0-debug.apk') -Algorithm SHA256 | Select-Object Hash,Path
} finally { Pop-Location }
