param(
    [switch]$WithLint
)
$ErrorActionPreference = 'Stop'
$gameRoot = Split-Path $PSScriptRoot -Parent
$workspaceRoot = (Resolve-Path (Join-Path $gameRoot '../../..')).Path
$env:JAVA_HOME = Join-Path $workspaceRoot '.tools/jdk21/jdk-21.0.12.1+1'
$env:GRADLE_USER_HOME = Join-Path $workspaceRoot '.tools/gradle'
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
$gradleTasks = @('testDebugUnitTest', 'assembleDebug', 'assembleDebugAndroidTest')
if ($WithLint) { $gradleTasks += 'lintDebug' }
& (Join-Path $gameRoot 'app/gradlew.bat') -p (Join-Path $gameRoot 'app') --no-daemon '-Dorg.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8' @gradleTasks
if ($LASTEXITCODE -ne 0) { throw "Gradle validation failed: $LASTEXITCODE" }
Write-Output 'Built and tested ARM64. This script does not open or control any window.'
