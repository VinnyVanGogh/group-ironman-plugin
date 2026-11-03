# The checksum step of Run-BuildGuard.cmd, extracted so it can be tested on
# its own (tools/windows/tests/Checksum.Tests.ps1). Run-BuildGuard.cmd carries
# an identical copy of this function between the BEGIN/END markers, because a
# friend downloads only the .cmd; the test fails if the two copies drift.

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
