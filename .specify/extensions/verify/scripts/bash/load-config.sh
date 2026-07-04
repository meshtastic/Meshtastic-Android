#!/usr/bin/env bash
# load-config.sh — Load and validate the verify extension configuration.
#
# Reads report.max_findings from the YAML config file,
# normalises YAML null sentinels, applies an optional environment
# variable override (SPECKIT_VERIFY_MAX_FINDINGS), and validates
# that a value is present before exporting it.
#
# Usage:  load-config.sh
#
# Exit codes:
#   0 — configuration loaded successfully
#   1 — config file missing, required value not set, or invalid value

config_file=".specify/extensions/verify/verify-config.yml"
extension_file=".specify/extensions/verify/extension.yml"
using_defaults=false

if [ ! -f "$config_file" ]; then
  if [ -f "$extension_file" ]; then
    using_defaults=true
  else
    echo "❌ Error: Configuration not found at $config_file"
    echo "Run 'specify extension add verify' to install and configure"
    exit 1
  fi
fi

# Read configuration values

# Extract a YAML value for a key from a file using only built-in tools.
# Finds the last occurrence of the key (handles nested sections) and
# strips surrounding whitespace and double quotes.
yaml_value() {
  local key="$1" file="$2"
  local raw
  raw=$(grep -E "^[[:space:]]*${key}:" "$file" | tail -n 1 | sed "s/^[^:]*://")
  # Trim leading/trailing whitespace
  raw=$(echo "$raw" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
  # Strip surrounding double quotes
  raw=$(echo "$raw" | sed 's/^"\(.*\)"$/\1/')
  echo "$raw"
}

if [ "$using_defaults" = true ]; then
  max_findings=$(yaml_value max_findings "$extension_file")
else
  max_findings=$(yaml_value max_findings "$config_file")
fi

# Treat YAML null sentinels as empty
if [ "$max_findings" = "null" ] || [ "$max_findings" = "~" ]; then
  max_findings=""
fi

# Apply environment variable overrides

max_findings="${SPECKIT_VERIFY_MAX_FINDINGS:-$max_findings}"

# Validate configuration

if [ -z "$max_findings" ]; then
  echo "❌ Error: Configuration value not set"
  echo "Edit $config_file and set 'report.max_findings'"
  exit 1
fi

if ! [[ "$max_findings" =~ ^[0-9]+$ ]]; then
  echo "❌ Error: 'report.max_findings' must be a positive integer, got '$max_findings'"
  echo "Edit $config_file and set 'report.max_findings' to a number (e.g. 50)"
  exit 1
fi

if [ "$using_defaults" = true ]; then
  echo "⚠️  No config file found; using defaults from extension.yml"
fi

echo "📋 Configuration loaded: max_findings=$max_findings"