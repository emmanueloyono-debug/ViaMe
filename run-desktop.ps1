# Launch ViaMe JavaFX Multi-Role Desktop Application
$javaExe = "$env:USERPROFILE\.jdks\jbr-21.0.11\bin\java.exe"
$m2 = "$env:USERPROFILE\.m2\repository"
$cp = (Get-ChildItem -Path $m2 -Recurse -Filter "*.jar" | ForEach-Object { $_.FullName }) -join ";"
$fullCp = "$cp;target\classes"

Write-Host "Starting ViaMe Desktop Simulation..." -ForegroundColor Cyan
& $javaExe -cp $fullCp com.viame.launcher.MainApp

