<#
.SYNOPSIS
    Detect changed files for code review via git diff.

.DESCRIPTION
    Identifies changed files by comparing the current branch against
    the default branch plus any uncommitted work (Mode A - feature branch
    diff + working directory) or by collecting staged + unstaged changes
    (Mode B - working directory changes only).

.PARAMETER Json
    Output in JSON format (for machine consumption).

.PARAMETER Help
    Show help message and exit.

.EXAMPLE
    .\detect-changed-files.ps1
    # Text output of changed files

.EXAMPLE
    .\detect-changed-files.ps1 -Json
    # JSON output of changed files

.NOTES
    EXIT CODES:
      0  Changed files detected successfully
      1  Error (git unavailable, not a git repository)
      2  No changes detected
#>

[CmdletBinding()]
param(
    [switch]$Json,
    [Alias("h")]
    [switch]$Help
)

$ErrorActionPreference = 'Stop'

# --- Help ---
if ($Help) {
    @"
Usage: detect-changed-files.ps1 [OPTIONS]

Detect changed files for code review via git diff.

OPTIONS:
  -Json         Output in JSON format
  -Help, -h     Show this help message

EXIT CODES:
  0  Changed files detected successfully
  1  Error (git unavailable, not a git repository)
  2  No changes detected
"@
    exit 0
}

# --- Helper: output error and exit ---
function Write-ErrorAndExit {
    param(
        [string]$Message,
        [int]$Code = 1
    )
    if ($Json) {
        [PSCustomObject]@{ error = $Message } | ConvertTo-Json -Compress
    } else {
        Write-Error "Error: $Message"
    }
    exit $Code
}

# --- 1a. Verify Git Availability ---
$gitCmd = Get-Command git -ErrorAction SilentlyContinue
if (-not $gitCmd) {
    Write-ErrorAndExit "git is not available. The review extension requires git to identify changed files." 1
}

$gitDir = git rev-parse --git-dir 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-ErrorAndExit "Not a git repository. The review extension requires git to identify changed files." 1
}

# --- 1b. Detect Branch Context ---

# Get current branch (empty string if detached HEAD)
$CurrentBranch = ""
try {
    $CurrentBranch = (git branch --show-current 2>$null) | Out-String
    $CurrentBranch = $CurrentBranch.Trim()
} catch {
    $CurrentBranch = ""
}

# Determine default branch
$DefaultBranch = ""

# Try symbolic-ref first
try {
    $symref = (git symbolic-ref refs/remotes/origin/HEAD 2>$null) | Out-String
    $symref = $symref.Trim()
    if ($symref -match "refs/remotes/origin/(.+)$") {
        $DefaultBranch = $Matches[1]
    }
} catch {}

# Fallback: check origin/main
if (-not $DefaultBranch) {
    $null = git rev-parse --verify origin/main 2>$null
    if ($LASTEXITCODE -eq 0) {
        $DefaultBranch = "main"
    }
}

# Fallback: check origin/master
if (-not $DefaultBranch) {
    $null = git rev-parse --verify origin/master 2>$null
    if ($LASTEXITCODE -eq 0) {
        $DefaultBranch = "master"
    }
}

# --- 1c. Get Changed Files ---

$ChangedFiles = @()
$Mode = ""

if ($CurrentBranch -and $DefaultBranch -and ($CurrentBranch -ne $DefaultBranch)) {
    # Mode A - Feature Branch
    $MergeBase = ""
    try {
        $MergeBase = (git merge-base "origin/$DefaultBranch" HEAD 2>$null) | Out-String
        $MergeBase = $MergeBase.Trim()
    } catch {}

    if ($MergeBase) {
        # Committed changes since merge-base
        $diffRaw = git diff --name-only -z --diff-filter=ACMR "$MergeBase...HEAD" 2>$null
        $committedFiles = @()
        if ($diffRaw) {
            $diffJoined = ($diffRaw -join "`n")
            $committedFiles = @($diffJoined -split "`0" | Where-Object { $_ -ne "" })
        }

        # Staged (index) changes
        $stagedRaw = git diff --cached --name-only -z --diff-filter=ACMR 2>$null
        $stagedFiles = @()
        if ($stagedRaw) {
            $sJoined = ($stagedRaw -join "`n")
            $stagedFiles = @($sJoined -split "`0" | Where-Object { $_ -ne "" })
        }

        # Unstaged (working tree) changes
        $unstagedRaw = git diff --name-only -z --diff-filter=ACMR 2>$null
        $unstagedFiles = @()
        if ($unstagedRaw) {
            $uJoined = ($unstagedRaw -join "`n")
            $unstagedFiles = @($uJoined -split "`0" | Where-Object { $_ -ne "" })
        }

        # Combine and deduplicate
        $ChangedFiles = @($committedFiles + $stagedFiles + $unstagedFiles | Sort-Object -Unique)

        $Mode = "Feature branch diff ($DefaultBranch...HEAD) + uncommitted changes"
    } else {
        # merge-base failed - fall through to Mode B
        $DefaultBranch = ""
    }
}

if (-not $Mode) {
    # Mode B - Working Directory Changes
    $stagedRaw = git diff --cached --name-only -z --diff-filter=ACMR 2>$null
    $unstagedRaw = git diff --name-only -z --diff-filter=ACMR 2>$null

    $allFiles = @()
    if ($stagedRaw) {
        $sJoined = ($stagedRaw -join "`n")
        $allFiles += @($sJoined -split "`0" | Where-Object { $_ -ne "" })
    }
    if ($unstagedRaw) {
        $uJoined = ($unstagedRaw -join "`n")
        $allFiles += @($uJoined -split "`0" | Where-Object { $_ -ne "" })
    }

    # Deduplicate
    $ChangedFiles = @($allFiles | Sort-Object -Unique)

    $Mode = "Working directory changes (staged + unstaged)"
    if (-not $DefaultBranch) { $DefaultBranch = "(unknown)" }
}

# --- 1d. Validate Changed Files ---
if ($ChangedFiles.Count -eq 0) {
    if ($Json) {
        [PSCustomObject]@{
            branch        = $CurrentBranch
            default_branch = $DefaultBranch
            mode          = $Mode
            changed_files = @()
            message       = "No changes detected. Nothing to review."
        } | ConvertTo-Json -Compress
    } else {
        Write-Output "No changes detected. Nothing to review."
    }
    exit 2
}

# --- Output ---
if ($Json) {
    [PSCustomObject]@{
        branch        = $CurrentBranch
        default_branch = $DefaultBranch
        mode          = $Mode
        changed_files = $ChangedFiles
    } | ConvertTo-Json -Compress
} else {
    Write-Output "BRANCH: $CurrentBranch"
    Write-Output "DEFAULT_BRANCH: $DefaultBranch"
    Write-Output "MODE: $Mode"
    Write-Output "CHANGED_FILES:"
    foreach ($f in $ChangedFiles) {
        Write-Output "  $f"
    }
}

exit 0
