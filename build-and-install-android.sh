#!/bin/bash

# Wireless APK Build, Install & Launch Script for Meshtastic Android
# This script automates building, installing, and launching the fdroidDebug APK wirelessly

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Project root directory
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK_PATH="${PROJECT_ROOT}/app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk"
PACKAGE_NAME="com.geeksville.mesh.fdroid.debug"
MAIN_ACTIVITY="${PACKAGE_NAME}/.MainActivity"

echo -e "${BLUE}=== Meshtastic Wireless Build, Install & Launch ===${NC}\n"

# Parse command line arguments
SKIP_BUILD=false
SKIP_LAUNCH=false

while [[ "$#" -gt 0 ]]; do
    case $1 in
        --skip-build) SKIP_BUILD=true ;;
        --skip-launch) SKIP_LAUNCH=true ;;
        --help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --skip-build   Skip building the APK (use existing build)"
            echo "  --skip-launch  Install but don't launch the app"
            echo "  --help         Show this help message"
            echo ""
            exit 0
            ;;
        *) echo "Unknown parameter: $1"; exit 1 ;;
    esac
    shift
done

# Build APK
if [ "$SKIP_BUILD" = false ]; then
    echo -e "${BLUE}Building fdroidDebug APK...${NC}"
    echo -e "${YELLOW}(This may take a minute)${NC}\n"

    if ./gradlew assembleFdroidDebug --console=plain | tail -20; then
        echo -e "\n${GREEN}✓ Build successful${NC}\n"
    else
        echo -e "\n${RED}✗ Build failed${NC}"
        exit 1
    fi
else
    echo -e "${YELLOW}Skipping build (--skip-build flag)${NC}\n"
fi

# Check if APK exists
if [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}Error: APK not found at ${APK_PATH}${NC}"
    echo -e "${YELLOW}Try running without --skip-build${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Found APK: $(basename "$APK_PATH")${NC}"

# Check for connected devices
DEVICES=$(adb devices | grep -v "List of devices" | grep "device$" | wc -l | tr -d ' ')

if [ "$DEVICES" -eq "0" ]; then
    echo -e "\n${YELLOW}No devices connected.${NC}"
    echo -e "${BLUE}Starting wireless pairing process...${NC}\n"

    echo "On your phone:"
    echo "1. Go to Settings → Developer Options → Wireless debugging"
    echo "2. Tap 'Pair device with pairing code'"
    echo ""

    # Prompt for pairing information
    read -p "Enter IP address (e.g., 192.168.8.169): " IP_ADDRESS
    read -p "Enter pairing port (e.g., 45621): " PAIRING_PORT
    read -p "Enter 6-digit pairing code: " PAIRING_CODE

    echo -e "\n${BLUE}Pairing with device...${NC}"
    if adb pair "${IP_ADDRESS}:${PAIRING_PORT}" "${PAIRING_CODE}"; then
        echo -e "${GREEN}✓ Successfully paired!${NC}\n"
    else
        echo -e "${RED}✗ Pairing failed. Please try again.${NC}"
        exit 1
    fi

    # Prompt for connection port
    echo "On your phone's Wireless debugging screen,"
    echo "find the 'IP address & Port' at the top (different from pairing port)"
    echo ""
    read -p "Enter connection port (e.g., 34925): " CONNECTION_PORT

    echo -e "\n${BLUE}Connecting to device...${NC}"
    if adb connect "${IP_ADDRESS}:${CONNECTION_PORT}"; then
        echo -e "${GREEN}✓ Connected to ${IP_ADDRESS}:${CONNECTION_PORT}${NC}\n"
    else
        echo -e "${RED}✗ Connection failed. Check the port and try again.${NC}"
        exit 1
    fi

    # Wait a moment for connection to stabilize
    sleep 1
elif [ "$DEVICES" -eq "1" ]; then
    DEVICE=$(adb devices | grep "device$" | awk '{print $1}')
    echo -e "${GREEN}✓ Device already connected: ${DEVICE}${NC}\n"
else
    echo -e "${YELLOW}Multiple devices connected. Using first available device.${NC}\n"
    DEVICE=$(adb devices | grep "device$" | head -1 | awk '{print $1}')
    echo -e "${GREEN}Selected device: ${DEVICE}${NC}\n"
fi

# Verify connection
DEVICE=$(adb devices | grep "device$" | head -1 | awk '{print $1}')
if [ -z "$DEVICE" ]; then
    echo -e "${RED}Error: No device connected after pairing process.${NC}"
    exit 1
fi

# Install APK
echo -e "${BLUE}Installing APK to device ${DEVICE}...${NC}"
echo -e "${YELLOW}(This may take a minute over wireless connection)${NC}\n"

if adb -s "$DEVICE" install -r -t "$APK_PATH"; then
    echo -e "\n${GREEN}╔════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║   ✓ APK installed successfully!       ║${NC}"
    echo -e "${GREEN}╚════════════════════════════════════════╝${NC}\n"
else
    echo -e "\n${RED}✗ Installation failed.${NC}"
    exit 1
fi

# Launch app
if [ "$SKIP_LAUNCH" = false ]; then
    echo -e "${BLUE}Launching Meshtastic app...${NC}\n"

    # Stop app if already running
    adb -s "$DEVICE" shell am force-stop "$PACKAGE_NAME" 2>/dev/null || true

    # Launch main activity
    if adb -s "$DEVICE" shell am start -n "$MAIN_ACTIVITY" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER; then
        echo -e "\n${GREEN}✓ App launched successfully!${NC}\n"

        echo -e "${BLUE}Test the new heatmap feature:${NC}"
        echo "1. Navigate to the map view"
        echo "2. Tap the filter icon (tune icon)"
        echo "3. Check 'Show heatmap'"
        echo ""
    else
        echo -e "\n${YELLOW}⚠ Could not launch app automatically. Please open it manually.${NC}\n"
    fi
fi

echo -e "${GREEN}Device: ${DEVICE}${NC}"

