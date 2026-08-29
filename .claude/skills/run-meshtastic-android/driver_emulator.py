#!/usr/bin/env python3
"""Drive the Meshtastic Android app on an emulator/device over adb.

Scripted bring-up (never hand-walk onboarding): launches MainActivity with the
debug-only skip_onboarding extra and a /connections deeplink that auto-connects
to a TCP radio — pair it with a replay-sim radio (an AVD reaches the host at
10.0.2.2). Handles the trust dialog newer builds pop on first connect.

Usage:
  driver_emulator.py [-s SERIAL] [-p PACKAGE] CMD [CMD ...]

Commands (executed left to right):
  connect[=ADDR]        force-stop, then deeplink-launch and auto-connect.
                        ADDR defaults to t10.0.2.2:4403 (t=TCP, x=BLE, s=serial,
                        n=disconnect). Waits for and accepts the trust dialog.
  launch                plain launch (skip_onboarding, no deeplink)
  stop                  force-stop the app
  dump                  print the uiautomator XML of the current screen
  find=TEXT             print nodes whose text/desc contains TEXT (with bounds)
  tap_text=TEXT         tap the center of the first clickable node matching TEXT
  tap=X,Y               tap raw coordinates
  text=STRING           type text into the focused field
  key=KEYCODE           send a keycode (e.g. 4 = BACK — careful, closes dialogs)
  swipe=X1,Y1,X2,Y2     swipe (use x≈30 in lists; mid-screen swipes get eaten by maps)
  ss=PATH.png           screenshot to a local file
  wait_text=TEXT        poll up to 60 s until TEXT appears on screen
  sleep=SECONDS         pause

Example — bring the app up against a replay sim on host port 4404:
  driver_emulator.py connect=t10.0.2.2:4404 wait_text=RPLY ss=/tmp/emu.png
"""

import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

SERIAL = None
PKG = "com.geeksville.mesh.fdroid.debug"
ACTIVITY = "org.meshtastic.app.MainActivity"


def adb(*args, binary=False):
    cmd = ["adb"] + (["-s", SERIAL] if SERIAL else []) + list(args)
    r = subprocess.run(cmd, capture_output=not binary, timeout=120)
    return (r.stdout or b"").decode(errors="replace") if not binary else None


def ui_dump():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    return adb("shell", "cat", "/sdcard/ui.xml")


def nodes(xml):
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return []
    out = []
    for n in root.iter("node"):
        out.append(n.attrib)
    return out


def center(bounds):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def find(text, clickable_only=False, exact=False):
    for n in nodes(ui_dump()):
        t, d = n.get("text", ""), n.get("content-desc", "")
        if exact:
            hit = text.lower() in (t.lower(), d.lower())
        else:
            hit = text.lower() in (t + " " + d).lower()
        if hit and (not clickable_only or n.get("clickable") == "true"):
            yield n


def tap_text(text):
    # exact text match first — substring matching taps "Stop Connecting" when you want "Connect"
    for n in find(text, exact=True):
        c = center(n.get("bounds", ""))
        if c:
            adb("shell", "input", "tap", str(c[0]), str(c[1]))
            return f"tapped exact {text!r} at {c}"
    for n in find(text, clickable_only=True):
        c = center(n.get("bounds", ""))
        if c:
            adb("shell", "input", "tap", str(c[0]), str(c[1]))
            return f"tapped {text!r} at {c}"
    # fall back to any match (some rows are labels inside a clickable parent)
    for n in find(text):
        c = center(n.get("bounds", ""))
        if c:
            adb("shell", "input", "tap", str(c[0]), str(c[1]))
            return f"tapped (non-clickable match) {text!r} at {c}"
    return f"NOT FOUND: {text!r}"


def wait_text(text, timeout=60):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if any(True for _ in find(text)):
            return f"found {text!r}"
        time.sleep(3)
    return f"TIMEOUT waiting for {text!r}"


def connect(addr):
    adb("shell", "am", "force-stop", PKG)
    time.sleep(1)
    adb(
        "shell", "am", "start", "-n", f"{PKG}/{ACTIVITY}",
        "--ez", "skip_onboarding", "true",
        "-a", "android.intent.action.VIEW",
        "-d", f"https://meshtastic.org/connections?address={addr}",
    )
    # Builds >2.8.1 pop a trust dialog on first connect to a new device. Match its
    # title, not bare "Connect" — that substring also matches "Stop Connecting".
    r = wait_text("Connect to this device", timeout=30)
    if r.startswith("found"):
        print(tap_text("Connect"))
    else:
        print("no trust dialog (already trusted)")
    return f"launched with {addr}"


def main():
    global SERIAL, PKG
    argv = sys.argv[1:]
    while argv and argv[0] in ("-s", "-p"):
        if argv[0] == "-s":
            SERIAL = argv[1]
        else:
            PKG = argv[1]
        argv = argv[2:]
    if not argv:
        print(__doc__)
        return 2
    for cmd in argv:
        name, _, val = cmd.partition("=")
        if name == "connect":
            print(connect(val or "t10.0.2.2:4403"))
        elif name == "launch":
            adb("shell", "am", "start", "-n", f"{PKG}/{ACTIVITY}", "--ez", "skip_onboarding", "true")
            print("launched")
        elif name == "stop":
            adb("shell", "am", "force-stop", PKG)
            print("stopped")
        elif name == "dump":
            print(ui_dump())
        elif name == "find":
            for n in find(val):
                print(f"{n.get('text') or n.get('content-desc')!r} clickable={n.get('clickable')} bounds={n.get('bounds')}")
        elif name == "tap_text":
            print(tap_text(val))
        elif name == "tap":
            x, y = val.split(",")
            adb("shell", "input", "tap", x, y)
            print(f"tapped {x},{y}")
        elif name == "text":
            adb("shell", "input", "text", val)
            print("typed (beware: 'input text' can append a trailing space)")
        elif name == "key":
            adb("shell", "input", "keyevent", val)
            print(f"key {val}")
        elif name == "swipe":
            adb("shell", "input", "swipe", *val.split(","))
            print(f"swipe {val}")
        elif name == "ss":
            with open(val, "wb") as f:
                subprocess.run(
                    ["adb"] + (["-s", SERIAL] if SERIAL else []) + ["exec-out", "screencap", "-p"],
                    stdout=f, timeout=60,
                )
            print(f"saved {val}")
        elif name == "wait_text":
            print(wait_text(val))
        elif name == "sleep":
            time.sleep(float(val))
        else:
            print(f"unknown command: {cmd}", file=sys.stderr)
            return 2
        print(f"--- {cmd} done ---", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
