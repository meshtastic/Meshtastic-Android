#!/usr/bin/env python3
"""Drive the running Meshtastic desktop app through the compose-hot-reload MCP server.

Speaks MCP JSON-RPC over the stdio of `./gradlew :desktopApp:hotMcpServer` (the same
server android/.mcp.json registers), so it works with no MCP client attached at all.
The app itself must already be running — launch it with :desktopApp:hotRunAsync first
(see SKILL.md). Each invocation spawns the server, waits for it to attach to the app,
executes the given commands in order, and exits.

Usage:
  driver.py [--repo DIR] CMD [CMD ...]

Commands (executed left to right):
  tools                 list the server's tools and their input schemas
  status                print connection status
  wait                  poll status until "connected":true (120 s timeout)
  windows               list app windows
  tree                  print the semantic tree (all windows)
  tree=SUBSTR           print only tree lines whose text matches SUBSTR (case-insensitive)
  click=NODEID          click a node by id from the tree
  longclick=NODEID      long-click a node
  type=NODEID:TEXT      set the text content of an editable node
  scroll_to=NODEID:IDX  scroll item IDX of scrollable container NODEID into view
  ss=PATH.png           screenshot the app window to PATH (absolute path)
  reload                recompile + hot-swap current sources into the running app
  restart               relaunch the app process (needed for singleton/init state)
  reset_ui              reset the UI to its entry point
  raise                 bring the app window frontmost (required before ss —
                        the screenshot captures the on-screen region)
  err                   print the current UI error, if any
  logs                  print recent app logs
  sleep=SECONDS         pause between commands (animations, connection settling)

Example — poke the Connections screen and screenshot it:
  driver.py wait tree=Connections click=42 sleep=1 ss=/tmp/conn.png
"""

import base64
import json
import os
import queue
import re
import subprocess
import sys
import threading
import time

NIX_POISON = ["DEVELOPER_DIR", "SDKROOT", "CC", "CXX", "LD", "AR", "NM", "RANLIB", "STRIP", "NIX_CC"]
JDK_GLOB = os.path.expanduser("~/.gradle/jdks/jetbrains_s_r_o_-25-*/*/Contents/Home")


def clean_env():
    """The Nix dev shell's Darwin stdenv breaks the MapLibre FFI and Skiko; strip it."""
    env = {k: v for k, v in os.environ.items() if k not in NIX_POISON}
    env["PATH"] = "/usr/bin:/bin:/usr/sbin:/sbin"
    import glob

    jdks = sorted(glob.glob(JDK_GLOB))
    if jdks:
        env["JAVA_HOME"] = jdks[-1]
    return env


class HotMcp:
    def __init__(self, repo):
        self.proc = subprocess.Popen(
            ["./gradlew", "--no-daemon", "--quiet", "--console=plain", ":desktopApp:hotMcpServer"],
            cwd=repo,
            env=clean_env(),
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            bufsize=1,
        )
        stdin, stdout = self.proc.stdin, self.proc.stdout
        assert stdin is not None and stdout is not None
        self.stdin, self.stdout = stdin, stdout
        # readline() would block past any deadline if the server keeps stdout open without
        # writing; a pump thread + queue makes the RPC timeout real.
        self._lines: "queue.Queue[str | None]" = queue.Queue()

        def _pump(out, q):
            for line in out:
                q.put(line)
            q.put(None)

        threading.Thread(target=_pump, args=(self.stdout, self._lines), daemon=True).start()
        self.next_id = 1
        self._rpc("initialize", {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {"name": "run-meshtastic-android-driver", "version": "1"},
        })
        self._notify("notifications/initialized")

    def _send(self, obj):
        self.stdin.write(json.dumps(obj) + "\n")
        self.stdin.flush()

    def _notify(self, method):
        self._send({"jsonrpc": "2.0", "method": method})

    def _rpc(self, method, params, timeout=180):
        rid = self.next_id
        self.next_id += 1
        self._send({"jsonrpc": "2.0", "id": rid, "method": method, "params": params})
        deadline = time.time() + timeout
        while True:
            remaining = deadline - time.time()
            if remaining <= 0:
                break
            try:
                line = self._lines.get(timeout=remaining)
            except queue.Empty:
                break
            if line is None:
                raise RuntimeError("hotMcpServer closed its stdout (is another instance running?)")
            line = line.strip()
            if not line.startswith("{"):
                continue  # gradle noise
            try:
                msg = json.loads(line)
            except json.JSONDecodeError:
                continue
            if msg.get("id") == rid:
                if "error" in msg:
                    raise RuntimeError(f"{method}: {msg['error']}")
                return msg.get("result")
        raise TimeoutError(f"{method}: no response in {timeout}s")

    def call(self, tool, args=None):
        return self._rpc("tools/call", {"name": tool, "arguments": args or {}})

    def ensure_connected(self, timeout=90):
        """The server attaches to the app asynchronously after initialize; poll before UI calls."""
        deadline = time.time() + timeout
        while time.time() < deadline:
            s = "".join(c.get("text", "") for c in (self.call("status") or {}).get("content", []))
            if '"connected":true' in s.replace(" ", ""):
                return s
            time.sleep(2)
        raise TimeoutError(f"app not connected after {timeout}s — is :desktopApp:hotRunAsync running? status: {s[:300]}")

    def close(self):
        try:
            self.stdin.close()
        except OSError:
            pass
        try:
            self.proc.wait(timeout=10)
        except subprocess.TimeoutExpired:
            self.proc.kill()


RAISE_SCRIPT = """
tell application "System Events"
  repeat with p in (every process whose name is "java")
    repeat with w in (every window of p)
      if name of w is "Meshtastic Desktop" then
        set frontmost of p to true
        perform action "AXRaise" of w
        return "raised"
      end if
    end repeat
  end repeat
end tell
return "not found"
"""


def raise_app():
    r = subprocess.run(["osascript", "-e", RAISE_SCRIPT], capture_output=True, text=True, timeout=30)
    print((r.stdout or r.stderr).strip())


def text_of(result):
    out = []
    for c in (result or {}).get("content", []):
        if c.get("type") == "text":
            out.append(c["text"])
    return "\n".join(out)


def save_image(result, path):
    for c in (result or {}).get("content", []):
        if c.get("type") == "image":
            with open(path, "wb") as f:
                f.write(base64.b64decode(c["data"]))
            return True
    # some tools return the base64 inline in text
    t = text_of(result)
    m = re.search(r"[A-Za-z0-9+/=]{200,}", t or "")
    if m:
        with open(path, "wb") as f:
            f.write(base64.b64decode(m.group(0)))
        return True
    return False


def main():
    argv = sys.argv[1:]
    repo = os.getcwd()
    if argv and argv[0] == "--repo":
        repo = argv[1]
        argv = argv[2:]
    if not argv:
        print(__doc__)
        return 2
    mcp = HotMcp(repo)
    UI_CMDS = {"windows", "tree", "click", "longclick", "type", "scroll_to", "ss", "reload", "restart", "reset_ui", "err", "logs"}  # "raise" is local, no app connection needed
    try:
        for cmd in argv:
            name, _, val = cmd.partition("=")
            if name in UI_CMDS:
                mcp.ensure_connected()
            if name == "tools":
                r = mcp._rpc("tools/list", {})
                for t in r.get("tools", []):
                    print(f"{t['name']}: {json.dumps(t.get('inputSchema', {}).get('properties', {}))}")
            elif name == "status":
                print(text_of(mcp.call("status")))
            elif name == "wait":
                mcp.ensure_connected(timeout=120)
                print("connected")
            elif name == "windows":
                print(text_of(mcp.call("list_windows")))
            elif name == "tree":
                t = text_of(mcp.call("get_semantic_tree"))
                if val:
                    pat = re.compile(re.escape(val), re.I)
                    print("\n".join(ln for ln in t.splitlines() if pat.search(ln)))
                else:
                    print(t)
            elif name == "click":
                print(text_of(mcp.call("click", {"nodeId": int(val)})))
            elif name == "longclick":
                print(text_of(mcp.call("long_click", {"nodeId": int(val)})))
            elif name == "type":
                nid, _, text = val.partition(":")
                print(text_of(mcp.call("type_text", {"nodeId": int(nid), "text": text})))
            elif name == "scroll_to":
                nid, _, idx = val.partition(":")
                print(text_of(mcp.call("scroll_to_index", {"nodeId": int(nid), "index": int(idx or 0)})))
            elif name == "ss":
                r = mcp.call("take_screenshot", {"save_to": os.path.abspath(val)})
                print(text_of(r) or f"saved {val}")
            elif name in ("reload", "restart", "reset_ui"):
                print(text_of(mcp.call(name)))
            elif name == "raise":
                raise_app()
                time.sleep(1)
            elif name == "err":
                print(text_of(mcp.call("get_ui_error")))
            elif name == "logs":
                print(text_of(mcp.call("get_logs")))
            elif name == "sleep":
                time.sleep(float(val))
            else:
                print(f"unknown command: {cmd}", file=sys.stderr)
                return 2
            print(f"--- {cmd} done ---", file=sys.stderr)
    finally:
        mcp.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
