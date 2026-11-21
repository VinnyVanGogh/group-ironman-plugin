<# : Run-GimUtilities.cmd - double-click to start a RuneLite test client with GIM Utilities loaded.
@echo off
setlocal
rem This file is both a batch file and a PowerShell script. cmd runs the lines
rem down to the closing marker below and hands the whole file to PowerShell,
rem which skips them as a comment. Nothing here needs admin rights, and
rem nothing changes system settings or PATH: everything is kept under
rem %LOCALAPPDATA%\GimUtilities and every variable set is for this window only.
set "GU_SELF=%~f0"
rem Started from a PowerShell 7 window, Windows PowerShell would inherit
rem PowerShell 7's module path and fail to load Get-FileHash. Left empty, it
rem uses its own defaults.
set "PSModulePath="
powershell -NoProfile -ExecutionPolicy Bypass -Command "iex ([IO.File]::ReadAllText($env:GU_SELF))"
set "GU_EXIT=%ERRORLEVEL%"
if not "%GU_EXIT%"=="0" (
  echo.
  echo GIM Utilities did not start. The reason is printed above.
  echo Take a screenshot of this window if you need help.
  pause
)
exit /b %GU_EXIT%
#>

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

# ---- Pins. Change these together with SETUP.md's Windows section. ----
# Eclipse Temurin JDK 11 for Windows x64, from the Adoptium API
# (api.adoptium.net/v3/assets/latest/11/hotspot, os=windows, arch=x64, jdk zip),
# read 2026-11-03.
$JdkUrl    = 'https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.32.1%2B1/OpenJDK11U-jdk_x64_windows_hotspot_11.0.32.1_1.zip'
$JdkSha256 = 'd5008f02174c1ad21c10407cbf104815cb390ec4c263d53946e8d0507d7a77f9'
# The tagged commit of this repo the launcher builds when it is run on its own.
# GitHub's tag archive needs no login only while the repo is public.
$SourceRef = 'gim-test-9'
$SourceUrl = "https://github.com/VinnyVanGogh/group-ironman-plugin/archive/refs/tags/$SourceRef.zip"
# Combat & Skilling Vitals, attached to the same release as combat-vitals.jar
# (the plain `./gradlew jar` of VinnyVanGogh/combat-vitals-plugin 084f6e5).
# The dev client this launcher starts runs with --developer-mode, which loads
# every *.jar in %USERPROFILE%\.runelite\sideloaded-plugins. A normal RuneLite
# never loads that folder.
$CvCommit    = '084f6e5'
$CvJarUrl    ="https://github.com/VinnyVanGogh/group-ironman-plugin/releases/download/$SourceRef/combat-vitals.jar"
$CvJarSha256 = '6156ad6ca1dd134cd78b2169a4c726997bf42feeb32f8d1ef0a6ec6856408081'
# Quest Route, attached to the same release as quest-route.jar (the plain
# `./gradlew jar` of VinnyVanGogh/questpath-plugin, build/libs/questpath.jar
# renamed). All zeros means "not pinned yet": the launcher skips it with a
# warning. Replace with the real SHA-256 of the jar attached to the release.
$QrCommit    = '2fe0674'
$QrJarUrl    ="https://github.com/VinnyVanGogh/group-ironman-plugin/releases/download/$SourceRef/quest-route.jar"
$QrJarSha256 = '0000000000000000000000000000000000000000000000000000000000000000'

$GuHome = Join-Path $env:LOCALAPPDATA 'GimUtilities'

# BEGIN Assert-Sha256
function Assert-Sha256 {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Expected
    )
    if ($Expected -notmatch '^[0-9a-fA-F]{64}$') {
        throw "Pinned SHA-256 is not 64 hex characters: '$Expected'"
    }
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "File to verify does not exist: $Path"
    }
    $actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
    if ($actual.ToLowerInvariant() -ne $Expected.ToLowerInvariant()) {
        throw "Checksum mismatch for $Path. Expected $($Expected.ToLowerInvariant()), got $($actual.ToLowerInvariant()). The download is refused."
    }
}
# END Assert-Sha256

function Say([string]$text) { Write-Host $text }

# BEGIN Get-GradleTask
# GU_BUILD_ONLY=1 builds without opening RuneLite; CI uses it. Only the exact
# value 1 counts, so nothing a user is likely to have set changes behaviour.
function Get-GradleTask([string]$buildOnly) {
    if ($buildOnly -eq '1') { return 'build' }
    return 'run'
}
# END Get-GradleTask

# BEGIN Install-SideloadedJar
# Moves a downloaded jar into the sideloaded-plugins folder once its SHA-256
# matches the pin; a mismatch throws and leaves the folder alone. A different
# jar already there under the same name is kept as <name>.bak (RuneLite loads
# only *.jar files, so the backup is never loaded). Returns 'installed', or
# 'replaced' when an earlier jar was backed up.
function Install-SideloadedJar {
    param(
        [Parameter(Mandatory = $true)][string]$Downloaded,
        [Parameter(Mandatory = $true)][string]$Folder,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Expected
    )
    Assert-Sha256 -Path $Downloaded -Expected $Expected
    New-Item -ItemType Directory -Force -Path $Folder | Out-Null
    $target = Join-Path $Folder $Name
    $result = 'installed'
    if (Test-Path -LiteralPath $target -PathType Leaf) {
        Copy-Item -LiteralPath $target -Destination "$target.bak" -Force
        $result = 'replaced'
    }
    Move-Item -LiteralPath $Downloaded -Destination $target -Force
    return $result
}
# END Install-SideloadedJar

# Installs one sideloaded plugin jar for the test client. Never fatal: if it
# cannot be fetched or does not match its pin, GIM Utilities starts without it.
function Install-Sideload {
    param(
        [Parameter(Mandatory = $true)][string]$Label,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Url,
        [Parameter(Mandatory = $true)][string]$Sha256
    )
    $folder = Join-Path $env:USERPROFILE '.runelite\sideloaded-plugins'
    $target = Join-Path $folder $Name
    if ($Sha256 -match '^0{64}$') {
        Say "      WARNING: $Label is not pinned in this launcher yet, so it was skipped."
        Say '      GIM Utilities still starts without it.'
        return
    }
    if (Test-Path -LiteralPath $target -PathType Leaf) {
        $have = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($have -eq $Sha256.ToLowerInvariant()) {
            Say "      $Label ($SourceRef) is already in $folder"
            return
        }
    }
    Say "      Downloading $Label ($SourceRef) into $folder..."
    $downloads = Join-Path $GuHome 'downloads'
    New-Item -ItemType Directory -Force -Path $downloads | Out-Null
    $part = Join-Path $downloads "$Name.part"
    if (Test-Path -LiteralPath $part) { Remove-Item -LiteralPath $part -Force }
    try {
        Invoke-WebRequest -UseBasicParsing -Uri $Url -OutFile $part
        $result = Install-SideloadedJar -Downloaded $part -Folder $folder -Name $Name -Expected $Sha256
    } catch {
        if (Test-Path -LiteralPath $part) { Remove-Item -LiteralPath $part -Force }
        Say "      WARNING: $Label was not installed ($($_.Exception.Message))."
        Say '      GIM Utilities still starts without it.'
        return
    }
    if ($result -eq 'replaced') {
        Say "      Your earlier $Name is kept next to it as $Name.bak."
    }
    Say "      $Label installed. Only this test client loads it, not your normal RuneLite."
}

function Install-CombatVitals {
    Install-Sideload -Label 'Combat & Skilling Vitals' -Name 'combat-vitals.jar' -Url $CvJarUrl -Sha256 $CvJarSha256
}

function Install-QuestRoute {
    Install-Sideload -Label 'Quest Route' -Name 'quest-route.jar' -Url $QrJarUrl -Sha256 $QrJarSha256
}

# The Java major version of a JDK home, or 0 when it is not a usable JDK.
# Both java.exe and javac.exe must exist: Gradle compiles the plugin.
function Get-JdkMajor([string]$javaHome) {
    if (-not $javaHome) { return 0 }
    $java = Join-Path $javaHome 'bin\java.exe'
    $javac = Join-Path $javaHome 'bin\javac.exe'
    if (-not (Test-Path -LiteralPath $java) -or -not (Test-Path -LiteralPath $javac)) { return 0 }
    # java -version writes to stderr; under 'Stop' Windows PowerShell would
    # turn that into an error, so relax it just for this call.
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $out = (& $java -version 2>&1 | Out-String)
    $ErrorActionPreference = $previous
    if ($out -match 'version "(\d+)(?:\.(\d+))?') {
        $major = [int]$Matches[1]
        if ($major -eq 1 -and $Matches[2]) { $major = [int]$Matches[2] }
        return $major
    }
    return 0
}

# 11 is what the Plugin Hub builds with; 21 is the newest this repo's Gradle
# 8.10 wrapper and RuneLite have been run on.
function Test-UsableJdk([string]$javaHome) {
    $major = Get-JdkMajor $javaHome
    return ($major -ge 11 -and $major -le 21)
}

function Find-Jdk {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $managed = Join-Path $GuHome 'jdk'
    if (Test-Path -LiteralPath $managed) {
        $candidates += (Get-ChildItem -LiteralPath $managed -Directory | ForEach-Object { $_.FullName })
    }
    $onPath = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($onPath) { $candidates += (Split-Path -Parent (Split-Path -Parent $onPath.Source)) }
    foreach ($c in $candidates) {
        if (Test-UsableJdk $c) { return $c }
    }
    return $null
}

function Install-Jdk {
    $downloads = Join-Path $GuHome 'downloads'
    New-Item -ItemType Directory -Force -Path $downloads | Out-Null
    $zip = Join-Path $downloads 'OpenJDK11U-jdk_x64_windows_hotspot.zip'

    $haveGoodZip = $false
    if (Test-Path -LiteralPath $zip) {
        try { Assert-Sha256 -Path $zip -Expected $JdkSha256; $haveGoodZip = $true } catch { Remove-Item -LiteralPath $zip -Force }
    }
    if (-not $haveGoodZip) {
        Say '      Downloading Java 11 (Eclipse Temurin, about 200 MB). This happens once.'
        $part = "$zip.part"
        if (Test-Path -LiteralPath $part) { Remove-Item -LiteralPath $part -Force }
        Invoke-WebRequest -UseBasicParsing -Uri $JdkUrl -OutFile $part
        try {
            Assert-Sha256 -Path $part -Expected $JdkSha256
        } catch {
            Remove-Item -LiteralPath $part -Force
            throw
        }
        Move-Item -LiteralPath $part -Destination $zip
    }
    Say '      Checksum verified. Unpacking Java...'

    $unpack = Join-Path $GuHome 'jdk.unpacking'
    if (Test-Path -LiteralPath $unpack) { Remove-Item -LiteralPath $unpack -Recurse -Force }
    Expand-Archive -LiteralPath $zip -DestinationPath $unpack
    $managed = Join-Path $GuHome 'jdk'
    New-Item -ItemType Directory -Force -Path $managed | Out-Null
    foreach ($dir in (Get-ChildItem -LiteralPath $unpack -Directory)) {
        $target = Join-Path $managed $dir.Name
        if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Recurse -Force }
        Move-Item -LiteralPath $dir.FullName -Destination $target
    }
    Remove-Item -LiteralPath $unpack -Recurse -Force
    $jdk = Find-Jdk
    if (-not $jdk) { throw 'Java was unpacked but java.exe/javac.exe were not found in it.' }
    return $jdk
}

function Test-Checkout([string]$dir) {
    return ($dir -and (Test-Path -LiteralPath (Join-Path $dir 'gradlew.bat')) -and
        (Test-Path -LiteralPath (Join-Path $dir 'src\main\java\com\vinnyvangogh\groupironman')))
}

function Find-Source {
    # Run from inside a checkout (tools\windows\Run-GimUtilities.cmd): use it as is.
    $toolsWindows = Split-Path -Parent $env:GU_SELF
    $checkout = Split-Path -Parent (Split-Path -Parent $toolsWindows)
    if (Test-Checkout $checkout) {
        Say "      Using the source this launcher sits in: $checkout"
        return $checkout
    }

    # Run on its own: fetch the pinned tag once, reuse it afterwards.
    $srcRoot = Join-Path (Join-Path $GuHome 'src') $SourceRef
    if (Test-Checkout $srcRoot) {
        Say "      Using the downloaded source: $srcRoot"
        return $srcRoot
    }
    Say "      Downloading GIM Utilities source ($SourceRef) from GitHub..."
    $downloads = Join-Path $GuHome 'downloads'
    New-Item -ItemType Directory -Force -Path $downloads | Out-Null
    $zip = Join-Path $downloads "gim-utilities-$SourceRef.zip"
    try {
        Invoke-WebRequest -UseBasicParsing -Uri $SourceUrl -OutFile $zip
    } catch {
        throw "Could not download $SourceUrl ($($_.Exception.Message)). If it says 404, the repo is not public yet or the tag $SourceRef does not exist."
    }
    $unpack = "$srcRoot.unpacking"
    if (Test-Path -LiteralPath $unpack) { Remove-Item -LiteralPath $unpack -Recurse -Force }
    Expand-Archive -LiteralPath $zip -DestinationPath $unpack
    $inner = Get-ChildItem -LiteralPath $unpack -Directory | Select-Object -First 1
    if (-not $inner -or -not (Test-Checkout $inner.FullName)) {
        throw "The downloaded source at $SourceUrl does not look like GIM Utilities."
    }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $srcRoot) | Out-Null
    if (Test-Path -LiteralPath $srcRoot) { Remove-Item -LiteralPath $srcRoot -Recurse -Force }
    Move-Item -LiteralPath $inner.FullName -Destination $srcRoot
    Remove-Item -LiteralPath $unpack -Recurse -Force
    return $srcRoot
}

try {
    Say 'GIM Utilities test client'
    Say "Everything is kept in $GuHome"
    Say ''

    # GU_DRY_RUN=1 lists what this launcher would install, then stops.
    if ($env:GU_DRY_RUN -eq '1') {
        Say 'Dry run (GU_DRY_RUN=1): nothing is downloaded or started. Components:'
        Say "GIM Utilities             $SourceRef (this repo)"
        Say "Combat & Skilling Vitals  $CvCommit  combat-vitals.jar  sha256 $CvJarSha256"
        Say "Quest Route               $QrCommit  quest-route.jar  sha256 $QrJarSha256"
        exit 0
    }

    Say '[1/3] Looking for Java 11 or newer...'
    $jdk = Find-Jdk
    if ($jdk) { Say "      Found $jdk" } else { $jdk = Install-Jdk; Say "      Installed $jdk" }

    Say '[2/3] Getting the plugin source...'
    $src = Find-Source
    Install-CombatVitals
    Install-QuestRoute

    $gradleTask = Get-GradleTask $env:GU_BUILD_ONLY
    if ($gradleTask -eq 'build') {
        Say '[3/3] Building GIM Utilities only (GU_BUILD_ONLY=1); RuneLite will not open.'
    } else {
        Say '[3/3] Building and starting RuneLite with GIM Utilities. The first run downloads Gradle and RuneLite (a few minutes); later runs are quick.'
        Say '      Close the RuneLite window when you are done; this window closes with it.'
    }
    $env:JAVA_HOME = $jdk
    $env:GRADLE_USER_HOME = Join-Path $GuHome 'gradle'
    $env:Path = (Join-Path $jdk 'bin') + ';' + $env:Path
    Push-Location -LiteralPath $src
    try {
        & (Join-Path $src 'gradlew.bat') --console=plain $gradleTask
        $code = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    if ($code -ne 0) { throw "The build or the client stopped with exit code $code. The Gradle output above says why." }
    exit 0
} catch {
    Write-Host ''
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
