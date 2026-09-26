#!/usr/bin/env python3
"""Check that every module root has an entry in pull-request.yml's android filter.

Each top-level directory holding a module in settings.gradle.kts needs a '<root>/**'
line in the android filter, or a PR that touches only that module skips CI. Entries
in the other filters do not count. Exits non-zero on drift.
"""

import re
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

# Filter roots that are intentionally not Gradle module roots
# (CI/workflow implementation + shared build infrastructure).
ALLOWED_INFRA_ROOTS = {'.github', 'build-logic', 'config', 'gradle'}
ALLOWED_EXTRA_ROOTS = {'baselineprofile'}


def android_filter(workflow: str) -> str:
    """Return the entries of the android filter, which is what gates validate-and-build."""
    lines = workflow.split('\n')
    try:
        filters = next(i for i, line in enumerate(lines) if line.strip() == 'filters: |')
        start = next(i for i in range(filters + 1, len(lines)) if lines[i].strip() == 'android:')
    except StopIteration:
        raise SystemExit('check-changes filter drift detected: no android filter in pull-request.yml')
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

    filter_paths = {
        path.split('/')[0]
        for path in re.findall(r"-\s*'([^']+/\*\*)'", android_filter(workflow))
    }

    missing = sorted(expected_roots - filter_paths)
    unexpected = sorted(filter_paths - expected_roots - ALLOWED_INFRA_ROOTS)

    if missing or unexpected:
        print('check-changes filter drift detected:')
        if missing:
            print('  Missing roots:', ', '.join(missing))
        if unexpected:
            print('  Unexpected roots:', ', '.join(unexpected))
        raise SystemExit(1)

    print('check-changes filter is aligned with settings.gradle module roots.')


if __name__ == '__main__':
    main()
