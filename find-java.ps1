#!/usr/bin/env powershell

# Verifica percorsi comuni di Java
$javaPaths = @(
    "C:\Program Files\Java",
    "C:\Program Files (x86)\Java",
    "C:\Android\jdk",
    "C:\jdk",
    "C:\Program Files\OpenJDK",
    "${env:USERPROFILE}\AppData\Local\Android\Sdk\jdk"
)

Write-Host "Searching for Java installation..."

foreach ($path in $javaPaths) {
    if (Test-Path $path) {
        Write-Host "Found: $path"
        Get-ChildItem $path -Recurse -Include "java.exe" -ErrorAction SilentlyContinue | ForEach-Object {
            Write-Host "  Java executable: $($_.FullName)"
        }
    }
}

# Verifica anche se java è nel PATH
try {
    $javaVersion = & java -version 2>&1
    Write-Host "Java found in PATH: $javaVersion"
} catch {
    Write-Host "Java NOT found in PATH"
}

