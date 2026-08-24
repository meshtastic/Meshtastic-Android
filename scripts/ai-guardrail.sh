#!/bin/bash
# Meshtastic AI Guardrail - Prevent binary/log leaks in commits
#
# INSTALLATION
# ------------
# Option 1 (recommended): set core.hooksPath so all devs share it automatically:
#   git config core.hooksPath scripts/hooks
#   mkdir -p scripts/hooks
#   ln -sf ../../ai-guardrail.sh scripts/hooks/pre-commit
#
# Option 2: copy/symlink directly into the local .git directory:
#   ln -sf ../../scripts/ai-guardrail.sh .git/hooks/pre-commit
#   chmod +x .git/hooks/pre-commit
#
# To run manually: bash scripts/ai-guardrail.sh

# List of patterns that should NEVER be committed by an AI Agent
FORBIDDEN_PATTERNS=(
    "\.log$"
    "\.png$"
    "\.jpg$"
    "\.jpeg$"
    "\.webp$"
    "\.mp3$"
    "tmp/"
    "\.agent_artifacts/"
    "build/"
    "google-services\.json$"
    "local\.properties$"
    "secrets\.properties$"
)

VIOLATIONS=()

while IFS= read -r -d '' file; do
    for pattern in "${FORBIDDEN_PATTERNS[@]}"; do
        if [[ $file =~ $pattern ]]; then
            VIOLATIONS+=("$file (matched $pattern)")
        fi
    done
done < <(git diff --cached --name-only -z)

if [ ${#VIOLATIONS[@]} -ne 0 ]; then
    echo "❌ AI GUARDRAIL VIOLATION: Staged files contain high-token or sensitive artifacts:"
    for violation in "${VIOLATIONS[@]}"; do
        echo "  - $violation"
    done
    echo ""
    echo "Please unstage these files before committing. Use .copilotignore to prevent this in the future."
    exit 1
fi

exit 0
