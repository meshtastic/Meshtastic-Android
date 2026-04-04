#!/bin/bash
#
# Copyright (c) 2026 Chris7X
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#
# Download Qwen3-0.6B LiteRT-LM model for on-device LLM inference
# Usage: ./download_model.sh [output_dir]
#
# Model source: https://huggingface.co/litert-community/Qwen3-0.6B

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="${1:-$SCRIPT_DIR/../src/androidMain/assets}"
MODEL_NAME="Qwen3-0.6B.litertlm"
MODEL_URL="https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm"

echo "=== LiteRT-LM Model Download ==="
echo "Model: Qwen3-0.6B (~586MB)"
echo "Output: $OUTPUT_DIR"
echo ""

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Check if model already exists
if [ -f "$OUTPUT_DIR/$MODEL_NAME" ]; then
    SIZE=$(stat -c%s "$OUTPUT_DIR/$MODEL_NAME" 2>/dev/null || stat -f%z "$OUTPUT_DIR/$MODEL_NAME" 2>/dev/null)
    if [ "$SIZE" -gt 500000000 ]; then
        echo "✓ Model already exists ($(($SIZE / 1024 / 1024))MB)"
        echo "  Delete $OUTPUT_DIR/$MODEL_NAME to re-download"
        exit 0
    else
        echo "! Incomplete download detected, re-downloading..."
        rm -f "$OUTPUT_DIR/$MODEL_NAME"
    fi
fi

# Download model
echo "Downloading model (this may take several minutes)..."
if command -v wget &> /dev/null; then
    wget -c -O "$OUTPUT_DIR/$MODEL_NAME" "$MODEL_URL"
elif command -v curl &> /dev/null; then
    curl -L -C - -o "$OUTPUT_DIR/$MODEL_NAME" "$MODEL_URL"
else
    echo "Error: wget or curl required"
    exit 1
fi

# Verify download
SIZE=$(stat -c%s "$OUTPUT_DIR/$MODEL_NAME" 2>/dev/null || stat -f%z "$OUTPUT_DIR/$MODEL_NAME" 2>/dev/null)
if [ "$SIZE" -gt 500000000 ]; then
    echo ""
    echo "✓ Download complete: $MODEL_NAME ($(($SIZE / 1024 / 1024))MB)"
    echo "  Location: $OUTPUT_DIR/$MODEL_NAME"
else
    echo ""
    echo "✗ Download failed or incomplete"
    rm -f "$OUTPUT_DIR/$MODEL_NAME"
    exit 1
fi

echo ""
echo "Next steps:"
echo "1. Build the app: ./gradlew :feature:ai:assembleDebug"
echo "2. The model will be bundled in assets/"
echo "3. On first run, copy model to filesDir for LiteRT-LM"
