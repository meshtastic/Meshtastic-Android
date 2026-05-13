load "test_helper"

setup() {
    setup_temp_dir
}

teardown() {
    teardown_temp_dir
}

# ──────────────────────────────────────────────
# 1a. Git Availability Errors
# ──────────────────────────────────────────────

@test "fails when not in a git repository" {
    cd "$TEST_TEMP_DIR"
    run bash "$SCRIPTS_DIR/detect-changed-files.sh"
    assert_failure
    [ "$status" -eq 1 ]
    assert_output --partial "Not a git repository"
}

@test "fails with JSON error when not in a git repository" {
    cd "$TEST_TEMP_DIR"
    run bash "$SCRIPTS_DIR/detect-changed-files.sh" --json
    assert_failure
    [ "$status" -eq 1 ]
    assert_output --partial '"error"'
    assert_output --partial "Not a git repository"
}

# ──────────────────────────────────────────────
# Help
# ──────────────────────────────────────────────

@test "--help shows usage information" {
    run bash "$SCRIPTS_DIR/detect-changed-files.sh" --help
    assert_success
    assert_output --partial "Usage"
    assert_output --partial "detect-changed-files"
}

@test "-h shows usage information" {
    run bash "$SCRIPTS_DIR/detect-changed-files.sh" -h
    assert_success
    assert_output --partial "Usage"
}

@test "unknown option fails with error" {
    run bash "$SCRIPTS_DIR/detect-changed-files.sh" --bogus
    assert_failure
    assert_output --partial "Unknown option"
}

# ──────────────────────────────────────────────
# No Changes Detected (exit code 2)
# ──────────────────────────────────────────────

@test "exit code 2 when no changes in clean repo" {
    init_git_repo "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"
    run bash "$SCRIPTS_DIR/detect-changed-files.sh"
    [ "$status" -eq 2 ]
    assert_output --partial "No changes detected"
}

@test "exit code 2 with JSON output when no changes" {
    init_git_repo "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"
    run bash "$SCRIPTS_DIR/detect-changed-files.sh" --json
    [ "$status" -eq 2 ]
    assert_valid_json "$output"
    local msg=$(json_field "$output" "message")
    [[ "$msg" == *"No changes detected"* ]]
}

# ──────────────────────────────────────────────
# Mode B — Working Directory: Unstaged Changes
# ──────────────────────────────────────────────

@test "detects unstaged changes (Mode B)" {
    init_git_repo "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"

    # Create and commit a file, then modify it
    echo "initial" > tracked.txt
    git add tracked.txt
    git commit --quiet -m "Add tracked file"
    echo "modified" > tracked.txt

    run bash "$SCRIPTS_DIR/detect-changed-files.sh"
    assert_success
    assert_output --partial "tracked.txt"
    assert_output --partial "Working directory changes"
}

@test "detects unstaged changes with --json (Mode B)" {
    init_git_repo "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"

    echo "initial" > tracked.txt
    git add tracked.txt
    git commit --quiet -m "Add tracked file"
    echo "modified" > tracked.txt

    run bash "$SCRIPTS_DIR/detect-changed-files.sh" --json
    assert_success
    assert_valid_json "$output"
    local mode=$(json_field "$output" "mode")
    [[ "$mode" == "Working directory changes (staged + unstaged)" ]]
    assert_output --partial '"tracked.txt"'
}

# ──────────────────────────────────────────────
# Mode B — Working Directory: Staged Changes
# ──────────────────────────────────────────────

@test "detects staged changes (Mode B)" {
    init_git_repo "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"

    echo "new file" > staged.txt
    git add staged.txt

    run bash "$SCRIPTS_DIR/detect-changed-files.sh"
    assert_success
    assert_output --partial "staged.txt"
    assert_output --partial "Working directory changes"
}

# ──────────────────────────────────────────────
# Mode B — Staged + Unstaged Deduplication
# ──────────────────────────────────────────────

@test "deduplicates staged and unstaged changes (Mode B)" {
    init_git_repo "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"

    # Create a file, commit, stage a change, then modify again (unstaged)
    echo "v1" > both.txt
    git add both.txt
    git commit --quiet -m "Add both.txt"
    echo "v2" > both.txt
    git add both.txt
    echo "v3" > both.txt

    # Also add a new staged-only file
    echo "new" > only-staged.txt
    git add only-staged.txt

    run bash "$SCRIPTS_DIR/detect-changed-files.sh" --json
    assert_success
    assert_valid_json "$output"

    # both.txt should appear only once
    local count=$(echo "$output" | python3 -c "import json,sys; print(json.load(sys.stdin)['changed_files'].count('both.txt'))")
    [ "$count" -eq 1 ]

    # only-staged.txt should also be present
    assert_output --partial '"only-staged.txt"'
}

# ──────────────────────────────────────────────
# Mode A — Feature Branch Diff
# ──────────────────────────────────────────────

@test "detects feature branch changes via merge-base (Mode A)" {
    init_git_repo_with_remote "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"

    # Create a feature branch with a new file
    git checkout --quiet -b feature-branch
    echo "feature code" > feature.txt
    git add feature.txt
    git commit --quiet -m "Add feature file"

    run bash "$SCRIPTS_DIR/detect-changed-files.sh"
    assert_success
    assert_output --partial "feature.txt"
    assert_output --partial "Feature branch diff"
}

@test "Mode A --json has correct structure" {
    init_git_repo_with_remote "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"

    git checkout --quiet -b feature-branch
    echo "feature code" > feature.txt
    git add feature.txt
    git commit --quiet -m "Add feature file"

    run bash "$SCRIPTS_DIR/detect-changed-files.sh" --json
    assert_success
    assert_valid_json "$output"

    local branch=$(json_field "$output" "branch")
    [ "$branch" = "feature-branch" ]

    local mode=$(json_field "$output" "mode")
    [[ "$mode" == "Feature branch diff"* ]]

    assert_output --partial '"feature.txt"'
}

@test "Mode A excludes deleted files (diff-filter=ACMR)" {
    init_git_repo_with_remote "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"

    # Add a file on main and push
    echo "to delete" > delete-me.txt
    git add delete-me.txt
    git commit --quiet -m "Add file to delete"
    git push --quiet origin main

    # Create feature branch, delete the file, add another
    git checkout --quiet -b feature-delete
    git rm --quiet delete-me.txt
    echo "keep me" > keep.txt
    git add keep.txt
    git commit --quiet -m "Delete and add"

    run bash "$SCRIPTS_DIR/detect-changed-files.sh" --json
    assert_success

    # delete-me.txt should NOT appear (it was deleted)
    local files=$(json_array_field "$output" "changed_files")
    ! echo "$files" | grep -q "delete-me.txt"

    # keep.txt should appear
    assert_output --partial '"keep.txt"'
}

@test "Mode A detects multiple changed files" {
    init_git_repo_with_remote "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"

    git checkout --quiet -b multi-files
    echo "a" > file-a.txt
    echo "b" > file-b.txt
    mkdir -p sub
    echo "c" > sub/file-c.txt
    git add .
    git commit --quiet -m "Add multiple files"

    run bash "$SCRIPTS_DIR/detect-changed-files.sh" --json
    assert_success
    assert_output --partial '"file-a.txt"'
    assert_output --partial '"file-b.txt"'
    assert_output --partial '"sub/file-c.txt"'
}

# ──────────────────────────────────────────────
# Mode A — Feature Branch + Uncommitted Changes
# ──────────────────────────────────────────────

@test "Mode A includes staged uncommitted files" {
    init_git_repo_with_remote "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"

    git checkout --quiet -b feature-staged
    echo "committed" > committed.txt
    git add committed.txt
    git commit --quiet -m "Add committed file"

    # Stage a new file without committing
    echo "staged" > staged-only.txt
    git add staged-only.txt

    run bash "$SCRIPTS_DIR/detect-changed-files.sh" --json
    assert_success
    assert_valid_json "$output"

    # Both committed and staged files should appear
    assert_output --partial '"committed.txt"'
    assert_output --partial '"staged-only.txt"'

    local mode=$(json_field "$output" "mode")
    [[ "$mode" == *"uncommitted"* ]]
}

@test "Mode A includes unstaged uncommitted files" {
    init_git_repo_with_remote "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"

    # Create a file on main, push it, then modify on feature branch
    echo "original" > existing.txt
    git add existing.txt
    git commit --quiet -m "Add existing file"
    git push --quiet origin main

    git checkout --quiet -b feature-unstaged
    echo "committed on branch" > committed.txt
    git add committed.txt
    git commit --quiet -m "Add committed file"

    # Modify existing file without staging
    echo "modified" > existing.txt

    run bash "$SCRIPTS_DIR/detect-changed-files.sh" --json
    assert_success
    assert_valid_json "$output"

    # Both committed diff and unstaged modification should appear
    assert_output --partial '"committed.txt"'
    assert_output --partial '"existing.txt"'
}

@test "Mode A deduplicates committed and uncommitted files" {
    init_git_repo_with_remote "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"

    git checkout --quiet -b feature-dedup
    echo "v1" > shared.txt
    git add shared.txt
    git commit --quiet -m "Add shared file"

    # Modify the same file (unstaged) — it appears in both committed diff and unstaged
    echo "v2" > shared.txt

    run bash "$SCRIPTS_DIR/detect-changed-files.sh" --json
    assert_success
    assert_valid_json "$output"

    # shared.txt should appear exactly once
    local count=$(echo "$output" | python3 -c "import json,sys; print(json.load(sys.stdin)['changed_files'].count('shared.txt'))")
    [ "$count" -eq 1 ]
}

# ──────────────────────────────────────────────
# Default Branch Detection Fallbacks
# ──────────────────────────────────────────────

@test "detects origin/main as default branch" {
    init_git_repo_with_remote "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"

    # Unset symbolic-ref to force fallback
    git remote set-head origin --delete 2>/dev/null || true

    git checkout --quiet -b test-branch
    echo "test" > test-file.txt
    git add test-file.txt
    git commit --quiet -m "Add test file"

    run bash "$SCRIPTS_DIR/detect-changed-files.sh" --json
    assert_success

    local default=$(json_field "$output" "default_branch")
    [ "$default" = "main" ]
}

@test "detects origin/master as default branch when no origin/main" {
    # Create a bare repo with master as default
    local bare="${TEST_TEMP_DIR}/_bare_master"
    mkdir -p "$bare"
    git -C "$bare" init --bare --quiet
    git -C "$bare" symbolic-ref HEAD refs/heads/master

    # Clone-like setup
    cd "$TEST_TEMP_DIR"
    git init --quiet
    git config user.email "test@example.com"
    git config user.name "Test"
    git remote add origin "$bare"

    # Create initial commit on master and push
    touch .gitkeep
    git add .
    git checkout -b master --quiet 2>/dev/null || true
    git commit --quiet -m "Initial commit"
    git push --quiet origin master

    # Remove symbolic-ref to force fallback
    git remote set-head origin --delete 2>/dev/null || true

    # Create feature branch
    git checkout --quiet -b test-branch
    echo "test" > test-file.txt
    git add test-file.txt
    git commit --quiet -m "Add test file"

    run bash "$SCRIPTS_DIR/detect-changed-files.sh" --json
    assert_success

    local default=$(json_field "$output" "default_branch")
    [ "$default" = "master" ]
}

@test "falls back to Mode B when no remote default branch found" {
    init_git_repo "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"

    # No remote configured, so no origin/* branches
    echo "change" > new-file.txt
    git add new-file.txt

    run bash "$SCRIPTS_DIR/detect-changed-files.sh" --json
    assert_success

    local mode=$(json_field "$output" "mode")
    [[ "$mode" == "Working directory changes"* ]]
}

# ──────────────────────────────────────────────
# Detached HEAD
# ──────────────────────────────────────────────

@test "detached HEAD falls back to Mode B" {
    init_git_repo "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"

    # Create a commit and detach HEAD
    echo "file" > detached.txt
    git add detached.txt
    git commit --quiet -m "Add file"
    local commit_hash=$(git rev-parse HEAD)
    git checkout --quiet "$commit_hash"

    # Make a change
    echo "modified" > detached.txt

    run bash "$SCRIPTS_DIR/detect-changed-files.sh"
    assert_success
    assert_output --partial "Working directory changes"
    assert_output --partial "detached.txt"
}

# ──────────────────────────────────────────────
# JSON Output Validation
# ──────────────────────────────────────────────

@test "--json output is valid JSON with all required keys" {
    init_git_repo "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"

    echo "content" > new.txt
    git add new.txt

    run bash "$SCRIPTS_DIR/detect-changed-files.sh" --json
    assert_success
    assert_valid_json "$output"

    # Verify all expected keys exist
    echo "$output" | python3 -c "
import json, sys
data = json.load(sys.stdin)
assert 'branch' in data, 'missing branch key'
assert 'default_branch' in data, 'missing default_branch key'
assert 'mode' in data, 'missing mode key'
assert 'changed_files' in data, 'missing changed_files key'
assert isinstance(data['changed_files'], list), 'changed_files should be a list'
print('All keys present and correct types')
"
}

@test "text mode output has correct format" {
    init_git_repo "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"

    echo "content" > formatted.txt
    git add formatted.txt

    run bash "$SCRIPTS_DIR/detect-changed-files.sh"
    assert_success
    assert_output --partial "BRANCH:"
    assert_output --partial "DEFAULT_BRANCH:"
    assert_output --partial "MODE:"
    assert_output --partial "CHANGED_FILES:"
    assert_output --partial "formatted.txt"
}

# ──────────────────────────────────────────────
# Edge Cases
# ──────────────────────────────────────────────

@test "handles files with spaces in names" {
    init_git_repo "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"

    echo "content" > "file with spaces.txt"
    git add "file with spaces.txt"

    run bash "$SCRIPTS_DIR/detect-changed-files.sh"
    assert_success
    assert_output --partial "file with spaces.txt"
}

@test "handles nested directory changes" {
    init_git_repo "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"

    mkdir -p deep/nested/path
    echo "deep" > deep/nested/path/file.txt
    git add .

    run bash "$SCRIPTS_DIR/detect-changed-files.sh" --json
    assert_success
    assert_output --partial '"deep/nested/path/file.txt"'
}

@test "only reports ACMR files (Added, Copied, Modified, Renamed)" {
    init_git_repo "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"

    # Add and commit files
    echo "keep" > keep.txt
    echo "remove" > remove.txt
    git add .
    git commit --quiet -m "Add files"

    # Delete one, modify another — only staged
    git rm --quiet remove.txt
    echo "modified" > keep.txt
    echo "added" > added.txt
    git add .

    run bash "$SCRIPTS_DIR/detect-changed-files.sh" --json
    assert_success

    # keep.txt (Modified) and added.txt (Added) should be present
    assert_output --partial '"keep.txt"'
    assert_output --partial '"added.txt"'

    # remove.txt (Deleted) should NOT be present
    local files=$(json_array_field "$output" "changed_files")
    [[ ! "$files" == *"remove.txt"* ]]
}

@test "Mode A: branch field matches current branch name" {
    init_git_repo_with_remote "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"

    git checkout --quiet -b my-feature-123
    echo "x" > x.txt
    git add x.txt
    git commit --quiet -m "commit"

    run bash "$SCRIPTS_DIR/detect-changed-files.sh" --json
    assert_success

    local branch=$(json_field "$output" "branch")
    [ "$branch" = "my-feature-123" ]
}

@test "Mode B: branch field shows current branch on default" {
    init_git_repo "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"

    echo "change" > file.txt
    git add file.txt

    run bash "$SCRIPTS_DIR/detect-changed-files.sh" --json
    assert_success

    # Branch should be main (or master depending on git default)
    local branch=$(json_field "$output" "branch")
    [[ "$branch" == "main" || "$branch" == "master" ]]
}

# ──────────────────────────────────────────────
# Special Characters in Filenames
# ──────────────────────────────────────────────

@test "handles filenames with double quotes in JSON mode" {
    init_git_repo "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"

    # Create a file with a double quote in its name
    local fname='file"quote.txt'
    echo "content" > "$fname"
    git add "$fname"

    run bash "$SCRIPTS_DIR/detect-changed-files.sh" --json
    assert_success
    assert_valid_json "$output"

    # The filename should be properly escaped in JSON
    local files=$(json_array_field "$output" "changed_files")
    [[ "$files" == *'file"quote.txt'* ]]
}

@test "handles filenames with backslashes in JSON mode" {
    init_git_repo "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"

    # Create a file with a backslash in its name
    local fname='file\slash.txt'
    echo "content" > "$fname"
    git add "$fname"

    run bash "$SCRIPTS_DIR/detect-changed-files.sh" --json
    assert_success
    assert_valid_json "$output"
}

@test "handles filenames with special characters in JSON mode" {
    init_git_repo "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"

    # Create files with various special characters
    echo "a" > "file (1).txt"
    echo "b" > "file's.txt"
    echo "c" > "file&more.txt"
    git add .

    run bash "$SCRIPTS_DIR/detect-changed-files.sh" --json
    assert_success
    assert_valid_json "$output"

    # All three files should be present
    local count=$(echo "$output" | python3 -c "import json,sys; print(len(json.load(sys.stdin)['changed_files']))")
    [ "$count" -eq 3 ]
}

# ──────────────────────────────────────────────
# Renamed Files
# ──────────────────────────────────────────────

@test "Mode A detects renamed files (diff-filter includes R)" {
    init_git_repo_with_remote "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"

    echo "content" > original.txt
    git add original.txt
    git commit --quiet -m "Add original"
    git push --quiet origin main

    git checkout --quiet -b feature-rename
    git mv original.txt renamed.txt
    git commit --quiet -m "Rename file"

    run bash "$SCRIPTS_DIR/detect-changed-files.sh" --json
    assert_success
    assert_output --partial '"renamed.txt"'
}

@test "Mode B detects renamed files (staged)" {
    init_git_repo "$TEST_TEMP_DIR"
    cd "$TEST_TEMP_DIR"

    echo "content" > original.txt
    git add original.txt
    git commit --quiet -m "Add original"

    git mv original.txt renamed.txt

    run bash "$SCRIPTS_DIR/detect-changed-files.sh" --json
    assert_success
    assert_output --partial '"renamed.txt"'
}
