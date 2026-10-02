#!/usr/bin/env python3
"""Summarise a protobufs pin bump for its pull request.

Usage: protobufs-bump-summary.py OLD_VERSION NEW_VERSION --protobufs DIR [--before XML --after XML]

OLD/NEW are catalog versions (2.8.0.111-g45f6b7e-SNAPSHOT or 2.8.1); DIR is a clone of meshtastic/protobufs
that has both commits. --before/--after are values/schema_strings.xml as it was and as `sync` rewrote it.
Prints Markdown: the compare link, the pull requests merged in between, what changed in the .proto files, and
which settings strings android will now show differently.
"""
import argparse
import html
import re
import subprocess
import sys

SNAPSHOT = re.compile(r"^\d+\.\d+\.\d+\.\d+-g([0-9a-f]+)-SNAPSHOT$")
RELEASE = re.compile(r"^\d+\.\d+\.\d+$")
STRING = re.compile(r'<string name="([^"]+)"[^>]*>(.*?)</string>', re.S)
ADDED = {
    "fields": re.compile(r"^\+\s+(?:optional\s+|repeated\s+)?[\w.]+\s+\w+\s*=\s*\d+"),
    "enum values": re.compile(r"^\+\s+[A-Z][A-Z0-9_]*\s*=\s*(?:0x[0-9A-Fa-f]+|-?\d+)"),
    "labels": re.compile(r"^\+.*\blabel:"),
    "descriptions": re.compile(r"^\+.*\bdescription:"),
    "firmware gates": re.compile(r"^\+.*\b(?:since_firmware|deprecated_since):"),
}


def ref(version):
    m = SNAPSHOT.match(version)
    if m:
        return m.group(1)
    if RELEASE.match(version):
        return "v" + version
    sys.exit(f"unrecognised protobufs version: {version}")


def git(repo, *args):
    return subprocess.run(["git", "-C", repo, *args], check=True, capture_output=True, text=True).stdout


def merged_prs(repo, old, new):
    out = []
    for line in git(repo, "log", "--first-parent", "--format=%s%x1f%b%x1e", f"{old}..{new}").split("\x1e"):
        if not line.strip():
            continue
        subject, _, body = line.strip().partition("\x1f")
        m = re.match(r"Merge pull request #(\d+) from \S+", subject)
        if m:
            title = body.strip().splitlines()[0] if body.strip() else ""
            out.append(f"- #{m.group(1)} {title}".rstrip())
            continue
        m = re.search(r"\(#(\d+)\)$", subject)
        out.append(f"- #{m.group(1)} {subject[: m.start()].strip()}" if m else f"- {subject}")
    return out


def proto_changes(repo, old, new):
    stat = git(repo, "diff", "--stat=100", f"{old}..{new}", "--", "meshtastic/*.proto").strip().splitlines()
    diff = git(repo, "diff", "-U0", f"{old}..{new}", "--", "meshtastic/*.proto")
    counts = {name: sum(1 for line in diff.splitlines() if rx.match(line)) for name, rx in ADDED.items()}
    return stat, counts


def strings(path):
    with open(path, encoding="utf-8") as f:
        return {m.group(1): html.unescape(m.group(2)) for m in STRING.finditer(f.read())}


def schema_delta(before, after):
    a, b = strings(before), strings(after)
    added = sorted(k for k in b if k not in a)
    removed = sorted(k for k in a if k not in b)
    changed = sorted(k for k in a if k in b and a[k] != b[k])
    lines = []
    if added:
        lines.append(f"**{len(added)} new strings**")
        lines += [f"- `{k}`: {b[k]}" for k in added]
    if changed:
        lines.append(f"**{len(changed)} changed strings**")
        lines += [f"- `{k}`: {a[k]} → {b[k]}" for k in changed]
    if removed:
        lines.append(f"**{len(removed)} removed strings** (a control still using one of these will not compile)")
        lines += [f"- `{k}`" for k in removed]
    return lines or ["No settings string changes."]


def main():
    p = argparse.ArgumentParser()
    p.add_argument("old")
    p.add_argument("new")
    p.add_argument("--protobufs", required=True)
    p.add_argument("--before")
    p.add_argument("--after")
    args = p.parse_args()
    old, new = ref(args.old), ref(args.new)

    out = ["<!-- protobufs-bump-summary -->", f"## protobufs `{args.old}` → `{args.new}`", ""]
    out.append(f"Compare: https://github.com/meshtastic/protobufs/compare/{old}...{new}")
    out.append("")
    prs = merged_prs(args.protobufs, old, new)
    out.append("### Merged upstream")
    out += prs or ["- nothing between these commits"]
    out.append("")
    stat, counts = proto_changes(args.protobufs, old, new)
    out.append("### Schema")
    out += [f"    {s}" for s in stat] or ["    no .proto changes"]
    added = ", ".join(f"{v} {k}" for k, v in counts.items() if v)
    if added:
        out.append("")
        out.append(f"Added lines: {added}.")
    out.append("")
    if args.before and args.after:
        out.append("### Settings strings (`values/schema_strings.xml`)")
        out += schema_delta(args.before, args.after)
    print("\n".join(out))


if __name__ == "__main__":
    main()
