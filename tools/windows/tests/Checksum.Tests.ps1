# Plain PowerShell asserts, no Pester needed. Runs on Windows PowerShell 5.1
# and on PowerShell 7 (pwsh) on macOS/Linux:
#   pwsh -NoProfile -File tools/windows/tests/Checksum.Tests.ps1
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$windows = Split-Path -Parent $here
. (Join-Path $windows 'Checksum.ps1')

$failures = 0
function Check([string]$name, [scriptblock]$body) {
    try { & $body; Write-Host "PASS $name" }
    catch { $script:failures++; Write-Host "FAIL $name : $_" }
}
function Expect-Throw([scriptblock]$body, [string]$like) {
    $threw = $false
    try { & $body } catch { $threw = $true; if ($_.ToString() -notlike $like) { throw "threw '$_', expected like '$like'" } }
    if (-not $threw) { throw 'expected a throw, got none' }
}

$dir = Join-Path ([IO.Path]::GetTempPath()) ("bg-checksum-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $dir | Out-Null
$file = Join-Path $dir 'payload.bin'
[IO.File]::WriteAllText($file, 'abc')
# SHA-256("abc"), the FIPS 180-2 test vector.
$abc = 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad'

try {
    Check 'matching hash passes' { Assert-Sha256 -Path $file -Expected $abc }
    Check 'hash comparison ignores case' { Assert-Sha256 -Path $file -Expected $abc.ToUpperInvariant() }
    Check 'one changed byte is refused' {
        [IO.File]::WriteAllText($file, 'abd')
        Expect-Throw { Assert-Sha256 -Path $file -Expected $abc } '*Checksum mismatch*refused*'
        [IO.File]::WriteAllText($file, 'abc')
    }
    Check 'missing file is refused' { Expect-Throw { Assert-Sha256 -Path (Join-Path $dir 'nope.zip') -Expected $abc } '*does not exist*' }
    Check 'malformed pin is refused' { Expect-Throw { Assert-Sha256 -Path $file -Expected 'abc123' } '*not 64 hex*' }
    Check 'empty pin is refused' { Expect-Throw { Assert-Sha256 -Path $file -Expected ' ' } '*not 64 hex*' }

    Check 'the .cmd carries the same Assert-Sha256 as Checksum.ps1' {
        $pattern = '(?s)# BEGIN Assert-Sha256.*?# END Assert-Sha256'
        $a = [regex]::Match((Get-Content -Raw (Join-Path $windows 'Checksum.ps1')), $pattern).Value -replace "`r", ''
        $b = [regex]::Match((Get-Content -Raw (Join-Path $windows 'Run-BuildGuard.cmd')), $pattern).Value -replace "`r", ''
        if (-not $a) { throw 'markers missing from Checksum.ps1' }
        if ($a -ne $b) { throw 'Run-BuildGuard.cmd and Checksum.ps1 have drifted' }
    }

    Check 'the pinned JDK hash in the .cmd is well-formed' {
        $cmd = Get-Content -Raw (Join-Path $windows 'Run-BuildGuard.cmd')
        $m = [regex]::Match($cmd, "\`$JdkSha256\s*=\s*'([^']*)'")
        if (-not $m.Success) { throw 'no $JdkSha256 pin found' }
        if ($m.Groups[1].Value -notmatch '^[0-9a-f]{64}$') { throw "bad pin $($m.Groups[1].Value)" }
    }

    Check 'the .cmd parses as PowerShell' {
        $errors = $null
        $text = Get-Content -Raw (Join-Path $windows 'Run-BuildGuard.cmd')
        [System.Management.Automation.Language.Parser]::ParseInput($text, [ref]$null, [ref]$errors) | Out-Null
        if ($errors.Count -gt 0) { throw ($errors | ForEach-Object { "line $($_.Extent.StartLineNumber): $($_.Message)" }) -join '; ' }
    }
}
finally {
    Remove-Item -Recurse -Force $dir
}

if ($failures -gt 0) { Write-Host "$failures failed"; exit 1 }
Write-Host 'all passed'
