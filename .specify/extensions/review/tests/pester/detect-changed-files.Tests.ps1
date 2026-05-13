BeforeAll {
    $ScriptsDir = Join-Path $PSScriptRoot "..\..\scripts\powershell"
    $Script = Join-Path $ScriptsDir "detect-changed-files.ps1"

    function New-TempDir {
        $tmp = Join-Path ([System.IO.Path]::GetTempPath()) ([System.IO.Path]::GetRandomFileName())
        New-Item -ItemType Directory -Path $tmp -Force | Out-Null
        return $tmp
    }

    function Initialize-GitRepo {
        param([string]$Dir)
        Push-Location $Dir
        git init --quiet -b main
        git config user.email "test@example.com"
        git config user.name "Test"
        New-Item -ItemType File -Path ".gitkeep" -Force | Out-Null
        git add .
        git commit --quiet -m "Initial commit"
        Pop-Location
    }

    function Initialize-GitRepoWithRemote {
        param([string]$Dir)
        $bareDir = Join-Path $Dir "_bare_remote"
        New-Item -ItemType Directory -Path $bareDir -Force | Out-Null
        Push-Location $bareDir
        git init --bare --quiet
        git symbolic-ref HEAD refs/heads/main
        Pop-Location

        Push-Location $Dir
        git init --quiet -b main
        git config user.email "test@example.com"
        git config user.name "Test"
        git remote add origin $bareDir
        New-Item -ItemType File -Path ".gitkeep" -Force | Out-Null
        git add .
        git commit --quiet -m "Initial commit"
        git push --quiet origin main
        git remote set-head origin --auto 2>$null
        Pop-Location
    }
}

Describe "detect-changed-files.ps1" {

    # ──────────────────────────────────────────────
    # Help
    # ──────────────────────────────────────────────

    Describe "Help" {
        It "shows usage with -Help" {
            $result = & pwsh -NoProfile -File $Script -Help
            $LASTEXITCODE | Should -Be 0
            ($result -join "`n") | Should -Match "Usage"
        }

        It "shows usage with -h" {
            $result = & pwsh -NoProfile -File $Script -h
            $LASTEXITCODE | Should -Be 0
            ($result -join "`n") | Should -Match "Usage"
        }
    }

    # ──────────────────────────────────────────────
    # Git Availability Errors
    # ──────────────────────────────────────────────

    Describe "Git availability" {
        It "fails when not in a git repository" {
            $tmp = New-TempDir
            try {
                Push-Location $tmp
                $result = & pwsh -NoProfile -File $Script 2>&1
                $LASTEXITCODE | Should -Be 1
            } finally {
                Pop-Location
                Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            }
        }

        It "fails with JSON error when not in a git repository" {
            $tmp = New-TempDir
            try {
                Push-Location $tmp
                $result = & pwsh -NoProfile -File $Script -Json 2>&1
                $LASTEXITCODE | Should -Be 1
                ($result -join "`n") | Should -Match '"error"'
            } finally {
                Pop-Location
                Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            }
        }
    }

    # ──────────────────────────────────────────────
    # No Changes Detected
    # ──────────────────────────────────────────────

    Describe "No changes" {
        It "exit code 2 when no changes in clean repo" {
            $tmp = New-TempDir
            try {
                Initialize-GitRepo -Dir $tmp
                Push-Location $tmp
                & pwsh -NoProfile -File $Script 2>&1
                $LASTEXITCODE | Should -Be 2
            } finally {
                Pop-Location
                Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            }
        }

        It "exit code 2 with JSON output when no changes" {
            $tmp = New-TempDir
            try {
                Initialize-GitRepo -Dir $tmp
                Push-Location $tmp
                $result = & pwsh -NoProfile -File $Script -Json 2>&1
                $LASTEXITCODE | Should -Be 2
                $json = $result | ConvertFrom-Json
                $json.message | Should -Match "No changes detected"
            } finally {
                Pop-Location
                Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            }
        }
    }

    # ──────────────────────────────────────────────
    # Mode B — Unstaged Changes
    # ──────────────────────────────────────────────

    Describe "Mode B - Unstaged" {
        It "detects unstaged changes" {
            $tmp = New-TempDir
            try {
                Initialize-GitRepo -Dir $tmp
                Push-Location $tmp
                "initial" | Set-Content "tracked.txt"
                git add tracked.txt
                git commit --quiet -m "Add tracked file"
                "modified" | Set-Content "tracked.txt"

                $result = & pwsh -NoProfile -File $Script -Json 2>&1
                $LASTEXITCODE | Should -Be 0
                $json = $result | ConvertFrom-Json
                $json.mode | Should -Match "Working directory changes"
                $json.changed_files | Should -Contain "tracked.txt"
            } finally {
                Pop-Location
                Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            }
        }
    }

    # ──────────────────────────────────────────────
    # Mode B — Staged Changes
    # ──────────────────────────────────────────────

    Describe "Mode B - Staged" {
        It "detects staged changes" {
            $tmp = New-TempDir
            try {
                Initialize-GitRepo -Dir $tmp
                Push-Location $tmp
                "new file" | Set-Content "staged.txt"
                git add staged.txt

                $result = & pwsh -NoProfile -File $Script -Json 2>&1
                $LASTEXITCODE | Should -Be 0
                $json = $result | ConvertFrom-Json
                $json.mode | Should -Match "Working directory changes"
                $json.changed_files | Should -Contain "staged.txt"
            } finally {
                Pop-Location
                Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            }
        }
    }

    # ──────────────────────────────────────────────
    # Mode B — Deduplication
    # ──────────────────────────────────────────────

    Describe "Mode B - Deduplication" {
        It "deduplicates staged and unstaged changes" {
            $tmp = New-TempDir
            try {
                Initialize-GitRepo -Dir $tmp
                Push-Location $tmp
                "v1" | Set-Content "both.txt"
                git add both.txt
                git commit --quiet -m "Add both.txt"
                "v2" | Set-Content "both.txt"
                git add both.txt
                "v3" | Set-Content "both.txt"

                $result = & pwsh -NoProfile -File $Script -Json 2>&1
                $LASTEXITCODE | Should -Be 0
                $json = $result | ConvertFrom-Json
                ($json.changed_files | Where-Object { $_ -eq "both.txt" }).Count | Should -Be 1
            } finally {
                Pop-Location
                Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            }
        }
    }

    # ──────────────────────────────────────────────
    # Mode A — Feature Branch Diff
    # ──────────────────────────────────────────────

    Describe "Mode A - Feature Branch" {
        It "detects feature branch changes via merge-base" {
            $tmp = New-TempDir
            try {
                Initialize-GitRepoWithRemote -Dir $tmp
                Push-Location $tmp
                git checkout --quiet -b feature-branch
                "feature code" | Set-Content "feature.txt"
                git add feature.txt
                git commit --quiet -m "Add feature file"

                $result = & pwsh -NoProfile -File $Script -Json 2>&1
                $LASTEXITCODE | Should -Be 0
                $json = $result | ConvertFrom-Json
                $json.branch | Should -Be "feature-branch"
                $json.mode | Should -Match "Feature branch diff"
                $json.changed_files | Should -Contain "feature.txt"
            } finally {
                Pop-Location
                Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            }
        }

        It "excludes deleted files (diff-filter=ACMR)" {
            $tmp = New-TempDir
            try {
                Initialize-GitRepoWithRemote -Dir $tmp
                Push-Location $tmp
                "to delete" | Set-Content "delete-me.txt"
                git add delete-me.txt
                git commit --quiet -m "Add file to delete"
                git push --quiet origin main

                git checkout --quiet -b feature-delete
                git rm --quiet delete-me.txt
                "keep me" | Set-Content "keep.txt"
                git add keep.txt
                git commit --quiet -m "Delete and add"

                $result = & pwsh -NoProfile -File $Script -Json 2>&1
                $LASTEXITCODE | Should -Be 0
                $json = $result | ConvertFrom-Json
                $json.changed_files | Should -Contain "keep.txt"
                $json.changed_files | Should -Not -Contain "delete-me.txt"
            } finally {
                Pop-Location
                Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            }
        }

        It "detects multiple changed files" {
            $tmp = New-TempDir
            try {
                Initialize-GitRepoWithRemote -Dir $tmp
                Push-Location $tmp
                git checkout --quiet -b multi-files
                "a" | Set-Content "file-a.txt"
                "b" | Set-Content "file-b.txt"
                $subDir = Join-Path $tmp "sub"
                New-Item -ItemType Directory -Path $subDir -Force | Out-Null
                "c" | Set-Content (Join-Path $subDir "file-c.txt")
                git add .
                git commit --quiet -m "Add multiple files"

                $result = & pwsh -NoProfile -File $Script -Json 2>&1
                $LASTEXITCODE | Should -Be 0
                $json = $result | ConvertFrom-Json
                $json.changed_files | Should -Contain "file-a.txt"
                $json.changed_files | Should -Contain "file-b.txt"
                $json.changed_files | Should -Contain "sub/file-c.txt"
            } finally {
                Pop-Location
                Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            }
        }

        It "detects renamed files (diff-filter includes R)" {
            $tmp = New-TempDir
            try {
                Initialize-GitRepoWithRemote -Dir $tmp
                Push-Location $tmp
                "content" | Set-Content "original.txt"
                git add original.txt
                git commit --quiet -m "Add original"
                git push --quiet origin main

                git checkout --quiet -b feature-rename
                git mv original.txt renamed.txt
                git commit --quiet -m "Rename file"

                $result = & pwsh -NoProfile -File $Script -Json 2>&1
                $LASTEXITCODE | Should -Be 0
                $json = $result | ConvertFrom-Json
                $json.changed_files | Should -Contain "renamed.txt"
            } finally {
                Pop-Location
                Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            }
        }
    }

    # ──────────────────────────────────────────────
    # Mode A — Feature Branch + Uncommitted Changes
    # ──────────────────────────────────────────────

    Describe "Mode A - Uncommitted changes" {
        It "includes staged uncommitted files" {
            $tmp = New-TempDir
            try {
                Initialize-GitRepoWithRemote -Dir $tmp
                Push-Location $tmp
                git checkout --quiet -b feature-staged
                "committed" | Set-Content "committed.txt"
                git add committed.txt
                git commit --quiet -m "Add committed file"

                # Stage a new file without committing
                "staged" | Set-Content "staged-only.txt"
                git add staged-only.txt

                $result = & pwsh -NoProfile -File $Script -Json 2>&1
                $LASTEXITCODE | Should -Be 0
                $json = $result | ConvertFrom-Json
                $json.changed_files | Should -Contain "committed.txt"
                $json.changed_files | Should -Contain "staged-only.txt"
                $json.mode | Should -Match "uncommitted"
            } finally {
                Pop-Location
                Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            }
        }

        It "includes unstaged uncommitted files" {
            $tmp = New-TempDir
            try {
                Initialize-GitRepoWithRemote -Dir $tmp
                Push-Location $tmp
                "original" | Set-Content "existing.txt"
                git add existing.txt
                git commit --quiet -m "Add existing file"
                git push --quiet origin main

                git checkout --quiet -b feature-unstaged
                "committed on branch" | Set-Content "committed.txt"
                git add committed.txt
                git commit --quiet -m "Add committed file"

                # Modify existing file without staging
                "modified" | Set-Content "existing.txt"

                $result = & pwsh -NoProfile -File $Script -Json 2>&1
                $LASTEXITCODE | Should -Be 0
                $json = $result | ConvertFrom-Json
                $json.changed_files | Should -Contain "committed.txt"
                $json.changed_files | Should -Contain "existing.txt"
            } finally {
                Pop-Location
                Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            }
        }

        It "deduplicates committed and uncommitted files" {
            $tmp = New-TempDir
            try {
                Initialize-GitRepoWithRemote -Dir $tmp
                Push-Location $tmp
                git checkout --quiet -b feature-dedup
                "v1" | Set-Content "shared.txt"
                git add shared.txt
                git commit --quiet -m "Add shared file"

                # Modify the same file (unstaged)
                "v2" | Set-Content "shared.txt"

                $result = & pwsh -NoProfile -File $Script -Json 2>&1
                $LASTEXITCODE | Should -Be 0
                $json = $result | ConvertFrom-Json
                ($json.changed_files | Where-Object { $_ -eq "shared.txt" }).Count | Should -Be 1
            } finally {
                Pop-Location
                Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            }
        }
    }

    # ──────────────────────────────────────────────
    # Default Branch Detection Fallbacks
    # ──────────────────────────────────────────────

    Describe "Default branch detection" {
        It "detects origin/main as default branch" {
            $tmp = New-TempDir
            try {
                Initialize-GitRepoWithRemote -Dir $tmp
                Push-Location $tmp

                # Unset symbolic-ref to force fallback
                git remote set-head origin --delete 2>$null

                git checkout --quiet -b test-branch
                "test" | Set-Content "test-file.txt"
                git add test-file.txt
                git commit --quiet -m "Add test file"

                $result = & pwsh -NoProfile -File $Script -Json 2>&1
                $LASTEXITCODE | Should -Be 0
                $json = $result | ConvertFrom-Json
                $json.default_branch | Should -Be "main"
            } finally {
                Pop-Location
                Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            }
        }

        It "falls back to Mode B when no remote default branch found" {
            $tmp = New-TempDir
            try {
                Initialize-GitRepo -Dir $tmp
                Push-Location $tmp
                "change" | Set-Content "new-file.txt"
                git add new-file.txt

                $result = & pwsh -NoProfile -File $Script -Json 2>&1
                $LASTEXITCODE | Should -Be 0
                $json = $result | ConvertFrom-Json
                $json.mode | Should -Match "Working directory changes"
            } finally {
                Pop-Location
                Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            }
        }

        It "detects origin/master as default branch when no origin/main" {
            $tmp = New-TempDir
            try {
                # Create a bare repo with master as default
                $bareDir = Join-Path $tmp "_bare_master"
                New-Item -ItemType Directory -Path $bareDir -Force | Out-Null
                Push-Location $bareDir
                git init --bare --quiet
                git symbolic-ref HEAD refs/heads/master
                Pop-Location

                Push-Location $tmp
                git init --quiet
                git config user.email "test@example.com"
                git config user.name "Test"
                git remote add origin $bareDir

                New-Item -ItemType File -Path ".gitkeep" -Force | Out-Null
                git add .
                git checkout -b master --quiet 2>$null
                git commit --quiet -m "Initial commit"
                git push --quiet origin master 2>$null

                # Remove symbolic-ref to force fallback
                git remote set-head origin --delete 2>$null

                # Create feature branch
                git checkout --quiet -b test-branch
                "test" | Set-Content "test-file.txt"
                git add test-file.txt
                git commit --quiet -m "Add test file"

                $result = & pwsh -NoProfile -File $Script -Json 2>&1
                $LASTEXITCODE | Should -Be 0
                $json = $result | ConvertFrom-Json
                $json.default_branch | Should -Be "master"
            } finally {
                Pop-Location
                Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            }
        }
    }

    # ──────────────────────────────────────────────
    # JSON Output Validation
    # ──────────────────────────────────────────────

    Describe "JSON output" {
        It "has all required keys" {
            $tmp = New-TempDir
            try {
                Initialize-GitRepo -Dir $tmp
                Push-Location $tmp
                "content" | Set-Content "new.txt"
                git add new.txt

                $result = & pwsh -NoProfile -File $Script -Json 2>&1
                $LASTEXITCODE | Should -Be 0
                $json = $result | ConvertFrom-Json
                $json.PSObject.Properties.Name | Should -Contain "branch"
                $json.PSObject.Properties.Name | Should -Contain "default_branch"
                $json.PSObject.Properties.Name | Should -Contain "mode"
                $json.PSObject.Properties.Name | Should -Contain "changed_files"
            } finally {
                Pop-Location
                Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            }
        }
    }

    # ──────────────────────────────────────────────
    # Edge Cases
    # ──────────────────────────────────────────────

    Describe "Edge cases" {
        It "handles files with spaces in names" {
            $tmp = New-TempDir
            try {
                Initialize-GitRepo -Dir $tmp
                Push-Location $tmp
                "content" | Set-Content "file with spaces.txt"
                git add "file with spaces.txt"

                $result = & pwsh -NoProfile -File $Script 2>&1
                $LASTEXITCODE | Should -Be 0
                ($result -join "`n") | Should -Match "file with spaces.txt"
            } finally {
                Pop-Location
                Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            }
        }

        It "handles nested directory changes" {
            $tmp = New-TempDir
            try {
                Initialize-GitRepo -Dir $tmp
                Push-Location $tmp
                $nested = Join-Path $tmp "deep" "nested" "path"
                New-Item -ItemType Directory -Path $nested -Force | Out-Null
                "deep" | Set-Content (Join-Path $nested "file.txt")
                git add .

                $result = & pwsh -NoProfile -File $Script -Json 2>&1
                $LASTEXITCODE | Should -Be 0
                $json = $result | ConvertFrom-Json
                $json.changed_files | Should -Contain "deep/nested/path/file.txt"
            } finally {
                Pop-Location
                Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            }
        }

        It "only reports ACMR files" {
            $tmp = New-TempDir
            try {
                Initialize-GitRepo -Dir $tmp
                Push-Location $tmp
                "keep" | Set-Content "keep.txt"
                "remove" | Set-Content "remove.txt"
                git add .
                git commit --quiet -m "Add files"

                git rm --quiet remove.txt
                "modified" | Set-Content "keep.txt"
                "added" | Set-Content "added.txt"
                git add .

                $result = & pwsh -NoProfile -File $Script -Json 2>&1
                $LASTEXITCODE | Should -Be 0
                $json = $result | ConvertFrom-Json
                $json.changed_files | Should -Contain "keep.txt"
                $json.changed_files | Should -Contain "added.txt"
                $json.changed_files | Should -Not -Contain "remove.txt"
            } finally {
                Pop-Location
                Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            }
        }
    }

    # ──────────────────────────────────────────────
    # Detached HEAD
    # ──────────────────────────────────────────────

    Describe "Detached HEAD" {
        It "falls back to Mode B on detached HEAD" {
            $tmp = New-TempDir
            try {
                Initialize-GitRepo -Dir $tmp
                Push-Location $tmp
                "file" | Set-Content "detached.txt"
                git add detached.txt
                git commit --quiet -m "Add file"
                $commitHash = git rev-parse HEAD
                git checkout --quiet $commitHash

                "modified" | Set-Content "detached.txt"

                $result = & pwsh -NoProfile -File $Script 2>&1
                $LASTEXITCODE | Should -Be 0
                ($result -join "`n") | Should -Match "Working directory changes"
                ($result -join "`n") | Should -Match "detached.txt"
            } finally {
                Pop-Location
                Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            }
        }
    }

    # ──────────────────────────────────────────────
    # Text Output Format Validation
    # ──────────────────────────────────────────────

    Describe "Text output format" {
        It "text mode output has correct format" {
            $tmp = New-TempDir
            try {
                Initialize-GitRepo -Dir $tmp
                Push-Location $tmp
                "content" | Set-Content "formatted.txt"
                git add formatted.txt

                $result = & pwsh -NoProfile -File $Script 2>&1
                $LASTEXITCODE | Should -Be 0
                $text = $result -join "`n"
                $text | Should -Match "BRANCH:"
                $text | Should -Match "DEFAULT_BRANCH:"
                $text | Should -Match "MODE:"
                $text | Should -Match "CHANGED_FILES:"
                $text | Should -Match "formatted.txt"
            } finally {
                Pop-Location
                Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            }
        }
    }

    # ──────────────────────────────────────────────
    # Branch Field Validation
    # ──────────────────────────────────────────────

    Describe "Branch field" {
        It "branch field matches current branch name in Mode A" {
            $tmp = New-TempDir
            try {
                Initialize-GitRepoWithRemote -Dir $tmp
                Push-Location $tmp
                git checkout --quiet -b my-feature-123
                "x" | Set-Content "x.txt"
                git add x.txt
                git commit --quiet -m "commit"

                $result = & pwsh -NoProfile -File $Script -Json 2>&1
                $LASTEXITCODE | Should -Be 0
                $json = $result | ConvertFrom-Json
                $json.branch | Should -Be "my-feature-123"
            } finally {
                Pop-Location
                Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            }
        }

        It "branch field shows current branch on default branch (Mode B)" {
            $tmp = New-TempDir
            try {
                Initialize-GitRepo -Dir $tmp
                Push-Location $tmp
                "change" | Set-Content "file.txt"
                git add file.txt

                $result = & pwsh -NoProfile -File $Script -Json 2>&1
                $LASTEXITCODE | Should -Be 0
                $json = $result | ConvertFrom-Json
                $json.branch | Should -Be "main"
            } finally {
                Pop-Location
                Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            }
        }
    }

    # ──────────────────────────────────────────────
    # Special Characters in Filenames
    # ──────────────────────────────────────────────

    Describe "Special character filenames" {
        It "handles filenames with special characters in JSON mode" {
            $tmp = New-TempDir
            try {
                Initialize-GitRepo -Dir $tmp
                Push-Location $tmp

                # Create files with special characters
                "a" | Set-Content "file (1).txt"
                "b" | Set-Content "file's.txt"
                "c" | Set-Content "file&more.txt"
                git add .

                $result = & pwsh -NoProfile -File $Script -Json 2>&1
                $LASTEXITCODE | Should -Be 0
                $json = $result | ConvertFrom-Json
                $json.changed_files | Should -Contain "file (1).txt"
                $json.changed_files | Should -Contain "file's.txt"
                $json.changed_files | Should -Contain "file&more.txt"
            } finally {
                Pop-Location
                Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            }
        }

        It "handles filenames with double quotes in JSON mode" -Skip:($IsWindows -or $env:OS -eq 'Windows_NT') {
            $tmp = New-TempDir
            try {
                Initialize-GitRepo -Dir $tmp
                Push-Location $tmp

                # Create a file with a double quote in its name
                # (skipped on Windows — NTFS does not allow " in filenames)
                $fname = 'file"quote.txt'
                [System.IO.File]::WriteAllText((Join-Path $tmp $fname), "content")
                git add .

                $result = & pwsh -NoProfile -File $Script -Json 2>&1
                $LASTEXITCODE | Should -Be 0
                $json = $result | ConvertFrom-Json
                $json.changed_files.Count | Should -Be 1
                $json.changed_files[0] | Should -Match 'quote'
            } finally {
                Pop-Location
                Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            }
        }
    }
}
