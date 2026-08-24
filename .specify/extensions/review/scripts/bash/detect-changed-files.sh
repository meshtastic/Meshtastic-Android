#!/usr/bin/env bash
# Detect changed files for code review via git diff
#
# Identifies changed files by comparing the current branch against
# the default branch plus any uncommitted work (Mode A — feature branch
# diff + working directory) or by collecting staged + unstaged changes
# (Mode B — working directory changes only).
#
# Usage: ./detect-changed-files.sh [OPTIONS]
#
# OPTIONS:
#   --json        Output in JSON format (for machine consumption)
#   --help, -h    Show this help message
#
# EXIT CODES:
#   0  Changed files detected successfully
#   1  Error (git unavailable, not a git repository)
#   2  No changes detected
#
# OUTPUTS:
#   Text mode:
#     BRANCH: <current-branch>
#     DEFAULT_BRANCH: <default-branch>
#     MODE: <detection mode description>
#     CHANGED_FILES:
#       file1
#       file2
#
#   JSON mode:
#     {"branch":"...","default_branch":"...","mode":"...","changed_files":["..."]}

set -e

# --- Argument parsing ---
JSON_MODE=false

for arg in "$@"; do
    case "$arg" in
        --json) JSON_MODE=true ;;
        --help|-h)
            cat << 'EOF'
Usage: detect-changed-files.sh [OPTIONS]

Detect changed files for code review via git diff.

OPTIONS:
  --json        Output in JSON format
  --help, -h    Show this help message

EXIT CODES:
  0  Changed files detected successfully
  1  Error (git unavailable, not a git repository)
  2  No changes detected
EOF
            exit 0
            ;;
        *) echo "ERROR: Unknown option '$arg'" >&2; exit 1 ;;
    esac
done

# --- Helper: escape a string for safe JSON embedding ---
json_escape() {
    local s="$1"
    s="${s//\\/\\\\}"   # \ → \\
    s="${s//\"/\\\"}"   # " → \\"
    s="${s//$'\t'/\\t}"    # tab → \t
    s="${s//$'\n'/\\n}"    # newline → \n
    s="${s//$'\r'/\\r}"    # carriage return → \r
    printf '%s' "$s"
}

# --- Helper: format bash array as JSON array ---
fmt_array() {
    local arr=("$@")
    if [[ ${#arr[@]} -eq 0 ]]; then echo "[]"; return; fi
    local first=true
    local result="["
    for item in "${arr[@]}"; do
        if $first; then first=false; else result+=","; fi
        result+="\"$(json_escape "$item")\""
    done
    result+="]"
    echo "$result"
}

# --- Helper: output error and exit ---
error_exit() {
    local message="$1"
    local code="${2:-1}"
    if $JSON_MODE; then
        printf '{"error":"%s"}\n' "$(json_escape "$message")"
    else
        echo "Error: $message" >&2
    fi
    exit "$code"
}

# --- 1a. Verify Git Availability ---
if ! command -v git >/dev/null 2>&1; then
    error_exit "git is not available. The review extension requires git to identify changed files." 1
fi

if ! git rev-parse --git-dir >/dev/null 2>&1; then
    error_exit "Not a git repository. The review extension requires git to identify changed files." 1
fi

# --- 1b. Detect Branch Context ---

# Get current branch (empty string if detached HEAD)
CURRENT_BRANCH=$(git branch --show-current 2>/dev/null || echo "")

# Determine default branch
DEFAULT_BRANCH=""

# Try symbolic-ref first
symref=$(git symbolic-ref refs/remotes/origin/HEAD 2>/dev/null || echo "")
if [[ -n "$symref" ]]; then
    DEFAULT_BRANCH="${symref##refs/remotes/origin/}"
fi

# Fallback: check origin/main
if [[ -z "$DEFAULT_BRANCH" ]]; then
    if git rev-parse --verify origin/main >/dev/null 2>&1; then
        DEFAULT_BRANCH="main"
    fi
fi

# Fallback: check origin/master
if [[ -z "$DEFAULT_BRANCH" ]]; then
    if git rev-parse --verify origin/master >/dev/null 2>&1; then
        DEFAULT_BRANCH="master"
    fi
fi

# --- 1c. Get Changed Files ---

CHANGED_FILES=()
MODE=""

if [[ -n "$CURRENT_BRANCH" && -n "$DEFAULT_BRANCH" && "$CURRENT_BRANCH" != "$DEFAULT_BRANCH" ]]; then
    # Mode A — Feature Branch
    MERGE_BASE=$(git merge-base "origin/$DEFAULT_BRANCH" HEAD 2>/dev/null || echo "")

    if [[ -n "$MERGE_BASE" ]]; then
        # Committed changes since merge-base
        COMMITTED=()
        while IFS= read -r -d '' line; do
            [[ -n "$line" ]] && COMMITTED+=("$line")
        done < <(git diff --name-only -z --diff-filter=ACMR "${MERGE_BASE}...HEAD" 2>/dev/null)

        # Staged (index) changes
        STAGED=()
        while IFS= read -r -d '' line; do
            [[ -n "$line" ]] && STAGED+=("$line")
        done < <(git diff --cached --name-only -z --diff-filter=ACMR 2>/dev/null)

        # Unstaged (working tree) changes
        UNSTAGED=()
        while IFS= read -r -d '' line; do
            [[ -n "$line" ]] && UNSTAGED+=("$line")
        done < <(git diff --name-only -z --diff-filter=ACMR 2>/dev/null)

        # Combine and deduplicate (bash 3 compatible — no associative arrays)
        CHANGED_FILES=()
        for f in "${COMMITTED[@]}" "${STAGED[@]}" "${UNSTAGED[@]}"; do
            [[ -z "$f" ]] && continue
            _dup=false
            for existing in "${CHANGED_FILES[@]}"; do
                if [[ "$existing" == "$f" ]]; then
                    _dup=true
                    break
                fi
            done
            if ! $_dup; then
                CHANGED_FILES+=("$f")
            fi
        done

        MODE="Feature branch diff (${DEFAULT_BRANCH}...HEAD) + uncommitted changes"
    else
        # merge-base failed — fall through to Mode B
        DEFAULT_BRANCH=""
    fi
fi

if [[ -z "$MODE" ]]; then
    # Mode B — Working Directory Changes
    STAGED=()
    while IFS= read -r -d '' line; do
        [[ -n "$line" ]] && STAGED+=("$line")
    done < <(git diff --cached --name-only -z --diff-filter=ACMR 2>/dev/null)

    UNSTAGED=()
    while IFS= read -r -d '' line; do
        [[ -n "$line" ]] && UNSTAGED+=("$line")
    done < <(git diff --name-only -z --diff-filter=ACMR 2>/dev/null)

    # Combine and deduplicate (bash 3 compatible — no associative arrays)
    CHANGED_FILES=()
    for f in "${STAGED[@]}" "${UNSTAGED[@]}"; do
        [[ -z "$f" ]] && continue
        _dup=false
        for existing in "${CHANGED_FILES[@]}"; do
            if [[ "$existing" == "$f" ]]; then
                _dup=true
                break
            fi
        done
        if ! $_dup; then
            CHANGED_FILES+=("$f")
        fi
    done

    MODE="Working directory changes (staged + unstaged)"
    [[ -z "$DEFAULT_BRANCH" ]] && DEFAULT_BRANCH="(unknown)"
fi

# --- 1d. Validate Changed Files ---
if [[ ${#CHANGED_FILES[@]} -eq 0 ]]; then
    if $JSON_MODE; then
        printf '{"branch":"%s","default_branch":"%s","mode":"%s","changed_files":[],"message":"No changes detected. Nothing to review."}\n' \
            "$(json_escape "$CURRENT_BRANCH")" "$(json_escape "$DEFAULT_BRANCH")" "$(json_escape "$MODE")"
    else
        echo "No changes detected. Nothing to review."
    fi
    exit 2
fi

# --- Output ---
if $JSON_MODE; then
    printf '{"branch":"%s","default_branch":"%s","mode":"%s","changed_files":%s}\n' \
        "$(json_escape "$CURRENT_BRANCH")" "$(json_escape "$DEFAULT_BRANCH")" "$(json_escape "$MODE")" "$(fmt_array "${CHANGED_FILES[@]}")"
else
    echo "BRANCH: $CURRENT_BRANCH"
    echo "DEFAULT_BRANCH: $DEFAULT_BRANCH"
    echo "MODE: $MODE"
    echo "CHANGED_FILES:"
    for f in "${CHANGED_FILES[@]}"; do
        echo "  $f"
    done
fi

exit 0
