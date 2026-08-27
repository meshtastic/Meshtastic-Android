#!/usr/bin/env python3
"""Check the flatpak platform-dependency list still declares every per-architecture root.

`flatpak-sources.json` is generated on an x86_64 runner, so it only ever contains the URLs an x86_64
resolution needs. The arm64 Flatpak then builds *offline* against that manifest, so every artifact it
resolves differently has to be force-resolved during generation — that is what the `platformDependencies`
block in the root build.gradle.kts is for.

Since flatpak-sources 0.2.0 those coordinates resolve transitively, so a platform artifact's own natives
come along by themselves and no longer need declaring; this used to check that they were, and that half is
gone. What transitive resolution cannot supply is a **root**. If desktopApp starts resolving a
per-architecture runtime that nothing here declares, there is no root to expand from, nothing warns, and
the miss surfaces as `Could not find <jar>` eleven minutes into the arm64 build. That is exactly how the
maplibre desktop runtime was missed on #6901, so it is what this still guards.

Offline and deterministic: it reads the two build scripts and the version catalog, nothing else.

Run from the repository root:

    python3 scripts/verify-flatpak/check-platform-deps.py
"""

from __future__ import annotations

import re
from pathlib import Path
from typing import NoReturn

# Suffixes that mark an *architecture*, so an artifact carrying one is built per CPU. An OS-only suffix
# does not count: `location-runtime-linux` is the same jar on both Linux arches, so the generation host's
# own resolution already captures it.
ARCH_SUFFIXES = ("arm64", "aarch64", "x64", "x86_64")

CATALOG = Path("gradle/libs.versions.toml")
BUILD_SCRIPT = Path("build.gradle.kts")
DESKTOP_SCRIPT = Path("desktopApp/build.gradle.kts")


def catalog_versions() -> dict[str, str]:
    """The `[versions]` table, so a version we also declare can be checked against its one source."""
    text = CATALOG.read_text()
    versions_table = text.split("[versions]", 1)[1].split("\n[", 1)[0]
    return dict(re.findall(r'^([\w.-]+)\s*=\s*"([^"]+)"', versions_table, re.MULTILINE))


def catalog_libraries() -> dict[str, str]:
    """The `[libraries]` table as alias -> `group:artifact:version`, with version.ref resolved."""
    text = CATALOG.read_text()
    versions = catalog_versions()
    table = text.split("[libraries]", 1)[1].split("\n[", 1)[0]
    libraries = {}
    for alias, body in re.findall(r"^([\w.-]+)\s*=\s*\{([^}]*)\}", table, re.MULTILINE):
        module = re.search(r'module\s*=\s*"([^"]+)"', body)
        ref = re.search(r'version\.ref\s*=\s*"([^"]+)"', body)
        literal = re.search(r'version\s*=\s*"([^"]+)"', body)
        version = versions.get(ref.group(1)) if ref else (literal.group(1) if literal else None)
        if module and version:
            libraries[alias] = f"{module.group(1)}:{version}"
    return libraries


def desktop_linux_runtimes() -> set[str]:
    """The per-arch Linux artifacts desktopApp itself asks for.

    Read from the source rather than assumed, so a runtime added there in future is caught here rather
    than eleven minutes into an arm64 Flatpak build. desktopApp names them through catalog accessors —
    `libs.some.artifact.linux.arm64` — whose dotted alias is the catalog key with dashes.
    """
    text = DESKTOP_SCRIPT.read_text()
    libraries = catalog_libraries()
    found = set()
    for accessor in re.findall(r"libs((?:\.[a-z0-9]+)+)", text):
        alias = accessor.lstrip(".").replace(".", "-")
        coordinate = libraries.get(alias)
        if coordinate and "linux" in alias and any(alias.endswith(s) for s in ARCH_SUFFIXES):
            found.add(coordinate)
    return found


def script_versions() -> dict[str, str]:
    """`val x = catalog.findVersion("alias")` bindings in the build script, resolved to versions.

    The script interpolates these into coordinate templates, so they have to be substituted before a
    template can be compared with anything.
    """
    text = BUILD_SCRIPT.read_text()
    versions = catalog_versions()
    bindings = re.findall(r'val (\w+)\s*=\s*catalog\.findVersion\("([\w.-]+)"\)', text)
    return {name: versions[alias] for name, alias in bindings if alias in versions}


def catalog_derived(platforms: set[str]) -> set[str]:
    """Coordinates the build script derives from the catalog rather than spelling out.

    It looks up an alias built as `"<prefix>-$platform"` once per target platform, so the alias prefix in
    the script plus the platform list says exactly what it will produce. Read from the script rather than
    assumed, so deleting the derivation leaves those coordinates undeclared here too. Any interpolated
    string literal counts as a candidate prefix; one that names no catalog alias yields nothing.
    """
    text = BUILD_SCRIPT.read_text()
    libraries = catalog_libraries()
    found = set()
    for prefix in re.findall(r'"([\w-]+)-\$\w+"', text):
        for platform in platforms:
            coordinate = libraries.get(f"{prefix}-{platform}")
            if coordinate:
                found.add(coordinate)
    return found


def declared() -> tuple[set[str], set[str]]:
    """The platforms and the coordinate templates the build script declares."""
    text = BUILD_SCRIPT.read_text()
    platforms_line = re.search(r"val platforms\s*=\s*setOf\(([^)]*)\)", text)
    templates_block = re.search(r"platformDependencies\.set\((.*?)\n\s*\)\n", text, re.DOTALL)
    if platforms_line is None or templates_block is None:
        fail(["could not find targetPlatforms/platformDependencies in build.gradle.kts"])
    return (
        set(re.findall(r'"([^"]+)"', platforms_line.group(1))),
        set(re.findall(r'"([^"]+)"', templates_block.group(1))),
    )


def expand(templates: set[str], platforms: set[str]) -> set[str]:
    """Every coordinate the plugin will actually force-resolve."""
    return {template.replace("{platform}", platform) for template in templates for platform in platforms}


def substitute(coordinate: str, versions: dict[str, str]) -> str:
    """A coordinate template with its `$name` version interpolations replaced by real versions."""
    for name, version in versions.items():
        coordinate = coordinate.replace(f"${name}", version)
    return coordinate


def fail(problems: list[str]) -> NoReturn:
    print("Flatpak platform-dependency drift detected:")
    for problem in problems:
        print("  -", problem)
    print()
    print("Fix: update platformDependencies in build.gradle.kts. See its comment for why each is needed.")
    raise SystemExit(1)


def main() -> None:
    versions = catalog_versions()
    platforms, templates = declared()
    substitutions = script_versions()
    literals = {substitute(c, substitutions) for c in expand(templates, platforms)}
    coordinates = literals | catalog_derived(platforms)
    problems: list[str] = []

    unresolved = sorted(c for c in coordinates if "$" in c)
    if unresolved:
        problems.append(
            f"the interpolated version in {unresolved} could not be resolved — has the build script changed shape?"
        )

    # Nothing hand-written should restate a version the catalog already holds. The build script reads
    # those from the catalog now, so this fires only if someone reintroduces a literal.
    for key, module in (
        ("maplibre-compose", "org.maplibre.compose:maplibre-compose-runtime-vulkan-"),
        ("compose-multiplatform", "org.jetbrains.compose.desktop:desktop-jvm-"),
    ):
        expected = versions.get(key)
        pinned = {c.split(":")[2] for c in literals if c.startswith(module)}
        if expected and pinned and pinned != {expected}:
            problems.append(
                f"{module}* is pinned to {sorted(pinned)} but gradle/libs.versions.toml sets {key} = {expected}"
            )

    # The one thing transitive resolution cannot do for us: supply a root. Anything desktopApp resolves per
    # Linux arch has to be declared, or the arm64 build has no URL for it and nothing warns.
    for runtime in sorted(desktop_linux_runtimes()):
        if runtime not in coordinates:
            problems.append(f"desktopApp resolves {runtime} on Linux, which is not declared")

    if problems:
        fail(problems)
    print(f"OK: {len(coordinates)} platform coordinates declared, covering every per-arch runtime desktopApp resolves.")


if __name__ == "__main__":
    main()
