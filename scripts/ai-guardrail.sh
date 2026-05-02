#!/bin/bash
# Meshtastic AI Guardrail - Prevent binary/log leaks in commits

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

STAGED_FILES=$(git diff --cached --name-only)
VIOLATIONS=()

for file in $STAGED_FILES; do
    for pattern in "${FORBIDDEN_PATTERNS[@]}"; do
        if [[ $file =~ $pattern ]]; then
            VIOLATIONS+=("$file (matched $pattern)")
        fi
    done
done

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
