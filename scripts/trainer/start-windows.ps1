# Bob-Trainer: startet den Trainer im Hintergrund (Windows). Aufgerufen von "Bob-Trainer starten.bat".
# Sucht Java 25; gibt es keins, bietet es an, Eclipse Temurin JRE 25 (api.adoptium.net) in den
# Ordner java\ zu laden. Danach laeuft der Trainer ohne Konsolenfenster; das Dashboard oeffnet sich im Browser.
$ErrorActionPreference = 'Stop'
$App = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Jar = Join-Path $App 'app\bob-trainer.jar'

function Get-JavaMajor([string]$exe) {
    # (java -version writes to stderr: with 'Stop' Windows PowerShell would take that for an error.)
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $out = & $exe -version 2>&1 | Out-String
        if ($out -match 'version "(\d+)') { return [int]$Matches[1] }
    } catch { } finally { $ErrorActionPreference = $old }
    return 0
}

function Find-Java {
    $c = New-Object System.Collections.Generic.List[string]
    $c.Add((Join-Path $App 'java\bin\java.exe'))
    if ($env:JAVA_HOME) { $c.Add((Join-Path $env:JAVA_HOME 'bin\java.exe')) }
    $onPath = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($onPath) { $c.Add($onPath.Source) }
    $roots = @(@($env:ProgramFiles, ${env:ProgramFiles(x86)}) | Where-Object { $_ })
    if ($env:LOCALAPPDATA) { $roots += (Join-Path $env:LOCALAPPDATA 'Programs') }
    foreach ($r in $roots) {
        foreach ($vendor in 'Eclipse Adoptium', 'Java', 'Microsoft', 'Zulu', 'Amazon Corretto', 'BellSoft') {
            Get-ChildItem -Path (Join-Path $r $vendor) -Directory -ErrorAction SilentlyContinue | ForEach-Object { $c.Add((Join-Path $_.FullName 'bin\java.exe')) }
        }
    }
    # The Minecraft launcher's own Java (the old launcher, the Store launcher).
    $mc = New-Object System.Collections.Generic.List[string]
    if ($env:APPDATA) { $mc.Add((Join-Path $env:APPDATA '.minecraft\runtime')) }
    if ($env:LOCALAPPDATA) { $mc.Add((Join-Path $env:LOCALAPPDATA 'Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime')) }
    if (${env:ProgramFiles(x86)}) { $mc.Add((Join-Path ${env:ProgramFiles(x86)} 'Minecraft Launcher\runtime')) }
    foreach ($m in $mc) {
        Get-ChildItem -Path $m -Recurse -Filter java.exe -ErrorAction SilentlyContinue | ForEach-Object { $c.Add($_.FullName) }
    }
    foreach ($exe in $c) {
        if ($exe -and (Test-Path $exe) -and (Get-JavaMajor $exe) -ge 25) { return $exe }
    }
    return $null
}

$java = Find-Java
if (-not $java) {
    Write-Host 'Bob-Trainer braucht Java 25, auf diesem Rechner ist keins zu finden.'
    $a = Read-Host 'Soll Java 25 (Eclipse Temurin, etwa 50 MB) in den Trainer-Ordner geladen werden? (j/n)'
    if ($a -notmatch '^[jJyY]') { Write-Host 'Abgebrochen. Java 25 gibt es auf https://adoptium.net'; Read-Host 'Enter zum Schliessen'; exit 1 }
    $arch = if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64') { 'aarch64' } else { 'x64' }
    $url = "https://api.adoptium.net/v3/binary/latest/25/ga/windows/$arch/jre/hotspot/normal/eclipse?project=jdk"
    $zip = Join-Path $env:TEMP 'bob-trainer-java25.zip'
    Write-Host "Lade $url ..."
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
    $tmp = Join-Path $App 'java-download'
    if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
    Expand-Archive -Path $zip -DestinationPath $tmp
    $inner = Get-ChildItem -Path $tmp -Directory | Select-Object -First 1
    $dest = Join-Path $App 'java'
    if (Test-Path $dest) { Remove-Item -Recurse -Force $dest }
    Move-Item $inner.FullName $dest
    Remove-Item -Recurse -Force $tmp
    Remove-Item -Force $zip
    $java = Join-Path $dest 'bin\java.exe'
    if ((Get-JavaMajor $java) -lt 25) { Write-Host 'Java konnte nicht eingerichtet werden.'; Read-Host 'Enter zum Schliessen'; exit 1 }
    Write-Host 'Java 25 ist eingerichtet.'
}
# javaw: the same Java without a console window.
$javaw = Join-Path (Split-Path -Parent $java) 'javaw.exe'
if (-not (Test-Path $javaw)) { $javaw = $java }
Start-Process -FilePath $javaw -ArgumentList @('-Xmx512m', '-jar', "`"$Jar`"", '--app', "`"$App`"") -WorkingDirectory $App -WindowStyle Hidden
Write-Host 'Bob-Trainer laeuft im Hintergrund. Das Dashboard oeffnet sich im Browser.'
