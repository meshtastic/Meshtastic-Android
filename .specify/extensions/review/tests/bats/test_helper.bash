#!/usr/bin/env bash
# Shared test helper for BATS tests

# Load bats libraries
BATS_LIB_DIR="$(cd "$(dirname "$BATS_TEST_FILENAME")/lib" && pwd)"
load "${BATS_LIB_DIR}/bats-support/load"
load "${BATS_LIB_DIR}/bats-assert/load"

# Project root (repo root)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

# Scripts under test
SCRIPTS_DIR="${PROJECT_ROOT}/scripts/bash"

# Create a temporary working directory for each test
setup_temp_dir() {
    TEST_TEMP_DIR="$(mktemp -d)"
    export TEST_TEMP_DIR
}

# Clean up the temporary directory
teardown_temp_dir() {
    if [[ -n "${TEST_TEMP_DIR:-}" && -d "$TEST_TEMP_DIR" ]]; then
        rm -rf "$TEST_TEMP_DIR"
    fi
}

# Initialize a git repo in the temp directory
init_git_repo() {
    local dir="${1:-$TEST_TEMP_DIR}"
    git -C "$dir" init --quiet -b main
    git -C "$dir" config user.email "test@example.com"
    git -C "$dir" config user.name "Test"
    # Create initial commit so git diff works
    touch "$dir/.gitkeep"
    git -C "$dir" add .
    git -C "$dir" commit --quiet -m "Initial commit"
}

# Initialize a git repo with a bare remote (for origin/* refs)
init_git_repo_with_remote() {
    local dir="${1:-$TEST_TEMP_DIR}"
    local bare_dir="${dir}/_bare_remote"

    # Create a bare repo to act as origin (explicitly use main)
    mkdir -p "$bare_dir"
    git -C "$bare_dir" init --bare --quiet
    git -C "$bare_dir" symbolic-ref HEAD refs/heads/main

    # Create the working repo
    git -C "$dir" init --quiet -b main
    git -C "$dir" config user.email "test@example.com"
    git -C "$dir" config user.name "Test"
    git -C "$dir" remote add origin "$bare_dir"

    # Create initial commit and push to establish origin/main
    touch "$dir/.gitkeep"
    git -C "$dir" add .
    git -C "$dir" commit --quiet -m "Initial commit"
    git -C "$dir" push --quiet origin main

    # Set origin/HEAD so symbolic-ref works
    git -C "$dir" remote set-head origin --auto 2>/dev/null || true
}

# Validate that output is valid JSON
assert_valid_json() {
    local output="$1"
    echo "$output" | python3 -m json.tool > /dev/null 2>&1 \
        || fail "Invalid JSON: $output"
}

# Extract a JSON field value (simple top-level string/bool/number)
json_field() {
    local json="$1"
    local field="$2"
    echo "$json" | python3 -c "import json,sys; print(json.load(sys.stdin).get('$field',''))"
}

# Extract a JSON array field as newline-separated values
json_array_field() {
    local json="$1"
    local field="$2"
    echo "$json" | python3 -c "
import json, sys
data = json.load(sys.stdin)
for item in data.get('$field', []):
    print(item)
"
}
