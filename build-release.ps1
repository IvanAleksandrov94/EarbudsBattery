[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$previousJavaHome = $env:JAVA_HOME

Push-Location -LiteralPath $PSScriptRoot
try {
    if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath "$env:JAVA_HOME/bin/java.exe")) {
        $studioJavaHome = Join-Path $env:ProgramFiles 'Android/Android Studio/jbr'
        if (Test-Path -LiteralPath "$studioJavaHome/bin/java.exe") {
            $env:JAVA_HOME = $studioJavaHome
        }
    }

    # Release optimizations and the debug signing certificate are configured in app/build.gradle.kts.
    # assembleRelease does not run unit or device tests.
    & (Join-Path $PSScriptRoot 'gradlew.bat') ':app:assembleRelease' '--console=plain'
    if ($LASTEXITCODE -ne 0) {
        throw "Release build failed (exit code $LASTEXITCODE)."
    }

    $apkPath = Join-Path $PSScriptRoot 'app/build/outputs/apk/release/app-release.apk'
    if (-not (Test-Path -LiteralPath $apkPath)) {
        throw "Build completed, but the APK was not found: $apkPath"
    }
    Write-Host "`nRelease APK signed with the debug certificate:"
    Write-Host $apkPath
}
finally {
    $env:JAVA_HOME = $previousJavaHome
    Pop-Location
}
