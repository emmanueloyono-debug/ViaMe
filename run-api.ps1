# Compile and Launch ViaMe REST API Server
param(
    [int]$Port = 8080
)

Set-Location $PSScriptRoot

function Resolve-JavaExecutable {
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-Path $candidate) { return $candidate }
    }
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    $jbr = Get-ChildItem -Path "$env:USERPROFILE\.jdks\jbr-*\bin\java.exe" -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending |
        Select-Object -First 1
    if ($jbr) { return $jbr.FullName }

    $anyJdk = Get-ChildItem -Path "$env:USERPROFILE\.jdks\*\bin\java.exe" -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending |
        Select-Object -First 1
    if ($anyJdk) { return $anyJdk.FullName }

    throw "Could not locate java.exe. Set JAVA_HOME or install JDK 17+."
}

function Resolve-MavenCommand {
    $cmd = Get-Command mvn -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $bundled = Get-ChildItem -Path "C:\Program Files\JetBrains\*\plugins\maven\lib\maven3\bin\mvn.cmd" -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($bundled) { return $bundled.FullName }
    return $null
}

function Get-RuntimeClasspath {
    $m2 = "$env:USERPROFILE\.m2\repository"
    $depDirs = @("org\openjfx", "com\mysql", "org\mindrot", "com\google\protobuf") |
        ForEach-Object { Join-Path $m2 $_ } |
        Where-Object { Test-Path $_ }
    $jars = @()
    if ($depDirs) {
        $jars = Get-ChildItem -Path $depDirs -Recurse -Filter "*.jar" -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notmatch "-(sources|javadoc)\.jar$" } |
            ForEach-Object { $_.FullName }
    }
    if (-not $jars -and (Test-Path $m2)) {
        $jars = Get-ChildItem -Path $m2 -Recurse -Filter "*.jar" -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notmatch "-(sources|javadoc)\.jar$" } |
            ForEach-Object { $_.FullName }
    }
    return (@("target\classes") + $jars) -join ";"
}

function Resolve-JavacExecutable([string]$javaExe) {
    $javaBin = Split-Path $javaExe -Parent
    $candidate = Join-Path $javaBin "javac.exe"
    if (Test-Path $candidate) { return $candidate }
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin\javac.exe"
        if (Test-Path $candidate) { return $candidate }
    }
    $cmd = Get-Command javac -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $jbr = Get-ChildItem -Path "$env:USERPROFILE\.jdks\jbr-*\bin\javac.exe" -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1
    if ($jbr) { return $jbr.FullName }
    $anyJdk = Get-ChildItem -Path "$env:USERPROFILE\.jdks\*\bin\javac.exe" -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1
    if ($anyJdk) { return $anyJdk.FullName }
    return $null
}

function Invoke-JavacCompile([string]$javaExe, [string]$classpath) {
    $javacExe = Resolve-JavacExecutable -javaExe $javaExe
    if (-not $javacExe -or -not (Test-Path $javacExe)) {
        throw "Could not locate javac.exe to compile sources."
    }
    New-Item -ItemType Directory -Force -Path "target\classes" | Out-Null
    if (Test-Path "src\main\resources") {
        Copy-Item -Path "src\main\resources\*" -Destination "target\classes" -Recurse -Force
    }
    $sourcesFile = Join-Path (Get-Location) "target\sources.txt"
    $sourceLines = Get-ChildItem -Path "src\main\java" -Recurse -Filter "*.java" |
        ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
    [System.IO.File]::WriteAllLines($sourcesFile, $sourceLines, (New-Object System.Text.UTF8Encoding $false))
    try {
        & $javacExe -encoding UTF-8 -cp $classpath -d "target\classes" "@$sourcesFile"
        if ($LASTEXITCODE -ne 0) {
            throw "javac compilation failed with exit code $LASTEXITCODE"
        }
    } finally {
        Remove-Item -Path $sourcesFile -ErrorAction SilentlyContinue
    }
}

$javaExe = Resolve-JavaExecutable
if (-not $env:JAVA_HOME) {
    $candidateHome = Split-Path (Split-Path $javaExe -Parent) -Parent
    if (Test-Path (Join-Path $candidateHome "bin\javac.exe")) {
        $env:JAVA_HOME = $candidateHome
    } else {
        $resolvedJavac = Resolve-JavacExecutable -javaExe $javaExe
        if ($resolvedJavac) {
            $env:JAVA_HOME = Split-Path (Split-Path $resolvedJavac -Parent) -Parent
        }
    }
}

$mvnCmd = Resolve-MavenCommand
$fullCp = Get-RuntimeClasspath

if ($mvnCmd) {
    Write-Host "Compiling ViaMe with Maven..." -ForegroundColor DarkCyan
    & $mvnCmd -q compile
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Maven compile failed; falling back to javac..."
        Invoke-JavacCompile -javaExe $javaExe -classpath $fullCp
    }
} else {
    Write-Host "Maven not found; compiling ViaMe with javac..." -ForegroundColor DarkCyan
    Invoke-JavacCompile -javaExe $javaExe -classpath $fullCp
}

Write-Host "Starting ViaMe REST API Server on port $Port ($javaExe)..." -ForegroundColor Cyan
& $javaExe -cp $fullCp com.viame.api.RestApiServer $Port
exit $LASTEXITCODE
