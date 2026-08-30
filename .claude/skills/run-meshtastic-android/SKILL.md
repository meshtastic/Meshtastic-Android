---
name: run-meshtastic-android
description: Run, launch, drive, and screenshot the Meshtastic app — the Compose Desktop app via hot reload (semantic clicks, live reload, window screenshots) or the Android app on an emulator (scripted deeplink bring-up, uiautomator taps, screencap). Use when asked to run the app, verify a UI change in the real app, take a screenshot, or exercise a flow end to end against a simulated radio.
---

# Run Meshtastic (Desktop & Emulator)

Two binaries, two drivers, one simulated radio. All paths are relative to the repo
root. Both drivers are Python 3, stdlib only, and print `--- <cmd> done ---` per
step on stderr.

- **Desktop** (`:desktopApp`, Compose/JVM, runs on this machine): launch with the
  hot-reload run task, drive through `.claude/skills/run-meshtastic-android/driver.py`,
  which speaks MCP JSON-RPC to `:desktopApp:hotMcpServer` — semantic tree, clicks
  by node id, `reload` (recompile + hot-swap), window screenshots.
- **Emulator** (`:androidApp` fdroid debug): drive through
  `.claude/skills/run-meshtastic-android/driver_emulator.py` — scripted deeplink
  bring-up, uiautomator-based taps, screencap.
- **Radio**: neither app does much without one. `mcp__meshtastic__replay_start`
  (meshtastic MCP) serves a simulated Meshtastic TCP radio; the desktop app reaches
  it at `127.0.0.1:<port>`, an AVD at `10.0.2.2:<port>`. One client per session —
  run the desktop and emulator against **different ports** (e.g. 4403 and 4404).

## Prerequisites

- Gradle runs go through the machine-wide queue: `~/.claude/bin/gradle-queue`.
  Everything after its `--` is **Gradle arguments** — it runs `./gradlew` itself
  (`gradle-queue -- ./gradlew tasks` fails with `Task './gradlew' not found`).
- The JetBrains 25 JDK Gradle provisioned at
  `~/.gradle/jdks/jetbrains_s_r_o_-25-*/…/Contents/Home` (the drivers find it themselves).
- Emulator leg: a running AVD (`adb devices`) with the fdroid debug build installed
  (`./gradlew :androidApp:installFdroidDebug` via the queue if missing).
- A simulated radio, e.g. `replay_start(source="meshcon", sim_nodes=30, port=4403,
  rate=2, loop=true, sim_profile={"traceroute_pairs_per_hour": 0})` — mute the
  traceroutes or their modals bury whatever you are testing.

## Run: Desktop (agent path)

Kill stray instances first — two apps fight over the pid file and the MCP server
reports `connected:false` forever:

```bash
pgrep -fl "MainKt|devtools.Main"   # kill any hits before launching
```

Launch (the Nix dev shell's Darwin stdenv breaks the MapLibre FFI — strip it):

```bash
env -u DEVELOPER_DIR -u SDKROOT -u CC -u CXX -u LD -u AR -u NM -u RANLIB -u STRIP -u NIX_CC \
  JAVA_HOME=$(ls -d ~/.gradle/jdks/jetbrains_s_r_o_-25-*/*/Contents/Home | tail -1) \
  PATH=/usr/bin:/bin:/usr/sbin:/sbin \
  ~/.claude/bin/gradle-queue -- :desktopApp:hotRunAsync
```

`BUILD SUCCESSFUL` + `desktopApp/build/run/main/main.pid` on disk means the app is up.

Drive it. Each driver invocation spawns a fresh `hotMcpServer`, auto-waits for it to
attach (asynchronous — the driver polls `status` for you), runs the commands in
order, and exits:

```bash
python3 .claude/skills/run-meshtastic-android/driver.py tree            # semantic tree (JSON, node ids)
python3 .claude/skills/run-meshtastic-android/driver.py click=170 sleep=1.5 tree
python3 .claude/skills/run-meshtastic-android/driver.py raise ss=/tmp/app.png
python3 .claude/skills/run-meshtastic-android/driver.py reload          # recompile + hot-swap edits
```

Run `driver.py` with no arguments for the full command list (`type=NODEID:TEXT`,
`scroll_to=NODEID:IDX`, `restart`, `err`, `logs`, …). `tools` prints the server's
live tool schemas if they've drifted.

**Verified flow** (connect to a sim and see its mesh): nav-rail tabs are semantic
`Tab` nodes — `Connect` opened via `click=<its id from tree>`, then the `Network`
radio button, then the device row for `127.0.0.1` under Recent Network Devices.
The sim's `replay_status` flips to `connected:true` within seconds and the Nodes
tab fills with the sim's mesh (`RPLY Replay Observer`, …).

The connection card can sit on "Reconnecting…" while packets already flow — the
label lags the config download. Trust `replay_status` and the Nodes list, not the
card text.

**Screenshots capture the window's on-screen region**, so the window must be
frontmost: always `raise` before `ss`. If `ss` shows your terminal, that's why.

`hotMcpServer` and `reload` compile **outside** gradle-queue (a long-lived stdio
server can't hold a slot) — check `~/.claude/bin/gradle-queue --status` before a
`reload` if other sessions may be building, and keep those runs short.

### Desktop deeplink launch (no clicking — but no hot reload)

The desktop app parses the same Meshtastic deeplink URIs from its **program args**
(`Main.kt` accepts `meshtastic://` and `https://meshtastic.org/...`), so a connected
app is one command:

```bash
env -u DEVELOPER_DIR -u SDKROOT -u CC -u CXX -u LD -u AR -u NM -u RANLIB -u STRIP -u NIX_CC \
  JAVA_HOME=$(ls -d ~/.gradle/jdks/jetbrains_s_r_o_-25-*/*/Contents/Home | tail -1) \
  PATH=/usr/bin:/bin:/usr/sbin:/sbin \
  ~/.claude/bin/gradle-queue -- :desktopApp:run --args="https://meshtastic.org/connections?address=t127.0.0.1:4403"
```

Verified against a sim the app had never connected to before, so it is the deeplink
acting, not last-device auto-reconnect. Caveats, all observed:

- `hotRunAsync` does **not** accept `--args` (its option list: --auto, --className,
  --funName, --mainClass, --stdout/--stderr only) — deeplink launch means the plain
  `run` task, which trades away hot reload. Long driving session → `hotRunAsync` +
  the driver's click path; quick "get me a connected app" → `run --args=…`.
- `run` blocks, so it **holds a gradle-queue slot for the app's whole lifetime**.
  Keep such runs short, or other sessions' builds will queue behind your app.
- The deeplink races last-device auto-reconnect: the app can connect to its
  remembered device first, then switch to the deeplink's target a moment later —
  if the remembered device is another sim, that sim briefly shows a client too.
- No trust dialog blocked the localhost connect in testing (unlike the Android
  build, which pops one for a never-seen device).

## Run: Emulator (agent path)

Scripted bring-up only — never hand-walk onboarding or the manual-IP dialog:

```bash
python3 .claude/skills/run-meshtastic-android/driver_emulator.py -s emulator-5554 \
  connect=t10.0.2.2:4404 wait_text=RPLY ss=/tmp/emu.png
```

`connect` force-stops the app, relaunches `org.meshtastic.app.MainActivity` with the
debug-only `skip_onboarding` extra and the `/connections?address=` deeplink
(`t` = TCP, `x` = BLE, `s` = serial, `n` = disconnect — full path list in
`docs/en/developer/navigation-and-deep-links.md`), then waits for the trust dialog
newer builds pop and taps its **Connect** button. Success looks like the Connection
screen showing `RPLY Replay Observer` with a **Disconnect** button, and
`replay_status` reporting `connected:true`.

Other commands: `dump`, `find=TEXT`, `tap_text=TEXT`, `tap=X,Y`, `text=`, `key=`,
`swipe=`, `launch`, `stop` — run with no arguments for the list. Default package is
`com.geeksville.mesh.fdroid.debug` (`-p` to override).

## Run (human path)

`./gradlew :desktopApp:run` (via the queue, same env hygiene) opens the window
without hot reload; Ctrl-C to stop. The emulator app is just the launcher icon —
but a debug build launched by icon lands on onboarding; the deeplink path above is
faster even for humans.

## Stopping

- Desktop: take the pid from the app's own pid file — it is a Java properties
  file (not a bare pid) and self-deletes on clean exit:

  ```bash
  kill $(sed -n 's/^pid=//p' desktopApp/build/run/main/main.pid)
  ```

  If the pid file is gone but a process lingers, `pgrep -af "MainKt|devtools.Main"`,
  check each match's path for **this** checkout, and kill that specific PID — a bare
  `pkill` on the pattern can take down another checkout's or session's app.
- Emulator: `driver_emulator.py -s <serial> stop`.
- Sim: `replay_stop`. Sessions the sim created are real user data in the app's DB;
  the app's last-selected device is now the sim — switch back on the Connect screen
  if a real radio should reconnect.

## Gotchas

- **`gradle-queue -- ./gradlew …` fails**: args after `--` go to `./gradlew`,
  which the wrapper runs itself. And piping its output (`| tail`) eats the exit
  code — check for `BUILD SUCCESSFUL` in the text, not `$?`.
- **`tap_text` matches substrings**: bare `Connect` also matches "Stop
  **Connect**ing" and "Re**connect**ing…". The driver tries exact text first;
  wait on the trust dialog's title ("Connect to this device"), not its button.
- **The MCP server attaches asynchronously** — a `tree` fired immediately after
  spawn returns "No application is currently connected". The driver auto-waits;
  if it times out, the app isn't running (or a stray instance holds the pid file).
- **`take_screenshot` needs the window visible** — `raise` first (System Events
  `AXRaise` targeting the window literally named "Meshtastic Desktop"; with two
  java processes, pid-based frontmosting picks the wrong one).
- **One client per simulated node.** Two apps pointed at the same sim don't
  queue — they fight, stealing the connection back and forth so both flap
  between Connected and Reconnecting. The desktop app holding port 4403 means
  the emulator needs its own `replay_start` on 4404.
- **`adb shell input text` can leave a trailing space**; dialogs' Add buttons
  silently no-op on it. And don't press BACK to dismiss the keyboard — it closes
  the dialog.
- **Swipe near x≈30** in lists; mid-screen swipes get eaten by embedded maps.
  Never busy-loop adb — pace with `adb shell sleep 2` or the emulator drops offline.
- The desktop app auto-reconnects to its last device on launch — it may already
  be connected to a real radio when you attach; check the Connect screen before
  assuming the sim.

## Troubleshooting

- `Task './gradlew' not found in root project` → you passed `./gradlew` after
  `gradle-queue --`; drop it.
- `BUILD FAILED in 1s` from `hotRunAsync` with slots free → read the full output;
  the queue wrapper's exit code vanishes behind pipes.
- Screenshot is your terminal → `raise` before `ss` (window wasn't frontmost).
- `connected:false` forever from `status` → stray `MainKt` from another checkout
  or worktree; `pgrep -fl MainKt`, kill, relaunch.
- Trust dialog never tapped, app stuck on dialog → older driver matched
  "Reconnecting…"; re-run `tap_text=Connect` (exact match wins now).
- UI card stuck "Reconnecting…" but sim says `connected:true` → not stuck; config
  download in progress. Check the Nodes tab for the sim's nodes.
- Connection flapping → `desktopApp/build/run/main/hotRun.stderr.txt` carries the
  transport-level story ("Handshake stall detected at Stage 1 … requesting forced
  transport restart" is the app self-recovering, not a crash). Also check that a
  second app isn't fighting for the same sim (one client per simulated node).
- A bare `status` right after spawn can report `connected:false` while the app is
  fine — the server attach is asynchronous; `wait` (or any UI command, which
  auto-waits) is the truth.
