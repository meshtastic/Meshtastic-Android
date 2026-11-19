#!/usr/bin/env python3
"""Check that pull-request.yml's android and web filters cover the modules they gate.

Each top-level directory holding a module in settings.gradle.kts needs a '<root>/**'
line in the android filter, or a PR that touches only that module skips CI. Entries
in the other filters do not count. Each module with the web target (meshtastic.kmp.web
or meshtastic.web.application) needs a '<module dir>/**' or '<root>/**' line in the web
filter, or a PR that breaks only its web build skips web-check. Exits non-zero on drift.
"""

import re
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

# Filter roots that are intentionally not Gradle module roots
# (CI/workflow implementation + shared build infrastructure).
ALLOWED_INFRA_ROOTS = {'.github', 'build-logic', 'config', 'gradle'}
ALLOWED_EXTRA_ROOTS = {'baselineprofile'}


WEB_PLUGINS = ('libs.plugins.meshtastic.kmp.web)', 'libs.plugins.meshtastic.web.application)')


def paths_filter(workflow: str, name: str) -> str:
    """Return the entries of the named paths-filter filter in check-changes."""
    lines = workflow.split('\n')
    try:
        filters = next(i for i, line in enumerate(lines) if line.strip() == 'filters: |')
        start = next(i for i in range(filters + 1, len(lines)) if lines[i].strip() == f'{name}:')
    except StopIteration:
        raise SystemExit(f'check-changes filter drift detected: no {name} filter in pull-request.yml')
    indent = len(lines[start]) - len(lines[start].lstrip())
    end = start + 1
    while end < len(lines):
        line = lines[end]
        if line.strip() and len(line) - len(line.lstrip()) <= indent:
            break
        end += 1
    return '\n'.join(lines[start + 1:end])


def main() -> None:
    settings = (REPO_ROOT / 'settings.gradle.kts').read_text()
    workflow = (REPO_ROOT / '.github/workflows/pull-request.yml').read_text()

    module_roots = {
        module.split(':')[0]
        for module in re.findall(r'":([^"]+)"', settings)
    }
    expected_roots = module_roots | ALLOWED_EXTRA_ROOTS

    # Only a whole-root entry covers a root: 'core/ble/**' leaves the rest of core/ unfiltered.
    filter_paths = set(re.findall(r"-\s*'([^'/]+)/\*\*'", paths_filter(workflow, 'android')))

    missing = sorted(expected_roots - filter_paths)
    unexpected = sorted(filter_paths - expected_roots - ALLOWED_INFRA_ROOTS)

    web_paths = set(re.findall(r"-\s*'([^']+)/\*\*'", paths_filter(workflow, 'web')))
    module_dirs = {module.replace(':', '/') for module in re.findall(r'":([^"]+)"', settings)}
    web_modules = sorted(
        d for d in module_dirs
        if (REPO_ROOT / d / 'build.gradle.kts').is_file()
        and any(plugin in (REPO_ROOT / d / 'build.gradle.kts').read_text() for plugin in WEB_PLUGINS)
    )
    web_missing = [m for m in web_modules if m not in web_paths and m.split('/')[0] not in web_paths]

    if missing or unexpected or web_missing:
        print('check-changes filter drift detected:')
        if missing:
            print('  Missing roots:', ', '.join(missing))
        if unexpected:
            print('  Unexpected roots:', ', '.join(unexpected))
        if web_missing:
            print('  Web modules missing from the web filter:', ', '.join(web_missing))
        raise SystemExit(1)

    print(f'check-changes filter is aligned with settings.gradle module roots and {len(web_modules)} web modules.')


if __name__ == '__main__':
    main()
