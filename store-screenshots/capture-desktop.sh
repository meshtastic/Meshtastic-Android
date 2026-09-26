#!/usr/bin/env bash
#
# Copyright (c) 2026 Meshtastic LLC
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
# Captures the five Flathub screenshots from the real desktop app (a debug build) on a
# virtual display, connected to Demo Mode's hidden showcase mesh.
#
#   capture-desktop.sh <path to the "Meshtastic Desktop" launcher> <output dir>
#
# Needs Xvfb, openbox, xdotool, ImageMagick and Mesa (GLX, zink, lavapipe). Each shot is
# its own launch with that screen's deep link, so nothing is clicked. A frame is kept once
# the window has not changed for a while; the map gets longer for its tiles.
set -euo pipefail

APP=${1:?usage: capture-desktop.sh <launcher> <output dir>}
OUT=${2:?usage: capture-desktop.sh <launcher> <output dir>}
mkdir -p "$OUT"
LOG="$OUT/app.log"

export DISPLAY=:99
Xvfb :99 -screen 0 1600x1000x24 +extension GLX +render -noreset &
sleep 2
# Without a window manager the window can't be placed or sized.
openbox &
sleep 2

# The map needs Skiko's OpenGL renderer, and Skiko refuses any GL adapter named llvmpipe
# or virgl. Zink over lavapipe is software GL under another name.
export LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=zink MESA_LOADER_DRIVER_OVERRIDE=zink
export JAVA_TOOL_OPTIONS="-Dskiko.renderApi=OPENGL"

PID=
WID=

launch() {
    "$APP" "$@" >>"$LOG" 2>&1 &
    PID=$!
    WID=
    for _ in $(seq 1 90); do
        # --onlyvisible: a hidden window can carry the same title and would swallow every command.
        WID=$(xdotool search --onlyvisible --name '^Meshtastic Desktop$' 2>/dev/null | head -1) || true
        [ -n "$WID" ] && break
        sleep 1
    done
    if [ -z "$WID" ]; then
        echo "::error::the desktop app never opened a window" >&2
        exit 1
    fi
    xdotool windowmove "$WID" 0 0
    xdotool windowsize "$WID" 1280 800
}

# settle <minimum s> <maximum s> <stable s>: waits until the window has not changed for <stable> s.
settle() {
    local minimum=$1 maximum=$2 stable=$3 previous='' same=0 elapsed
    sleep "$minimum"
    elapsed=$minimum
    while [ "$elapsed" -lt "$maximum" ]; do
        import -window "$WID" "$OUT/.frame.png"
        local current
        current=$(md5sum <"$OUT/.frame.png")
        if [ "$current" = "$previous" ]; then
            same=$((same + 2))
            [ "$same" -ge "$stable" ] && return 0
        else
            same=0
            previous=$current
        fi
        sleep 2
        elapsed=$((elapsed + 2))
    done
    echo "::warning::the window never settled within ${maximum}s; capturing as is"
}

shoot() {
    import -window "$WID" "$OUT/meshtastic-desktop-$1.png"
    echo "captured meshtastic-desktop-$1.png"
}

quit() {
    kill "$PID" 2>/dev/null || true
    wait "$PID" 2>/dev/null || true
    sleep 3
}

# Connect once. The address is saved, so every later launch reconnects by itself.
launch "https://meshtastic.org/connections?address=mshowcase" --skip-connect-confirm
settle 20 90 6
shoot 04-connections
quit

for shot in "01-nodes nodes" "02-messages messages/0^all" "05-settings settings"; do
    read -r name path <<<"$shot"
    launch "https://meshtastic.org/$path"
    settle 15 60 6
    shoot "$name"
    quit
done

launch "https://meshtastic.org/map"
settle 45 180 10
shoot 03-map
quit

rm -f "$OUT/.frame.png"
