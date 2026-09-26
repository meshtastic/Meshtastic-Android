#!/usr/bin/env python3
"""Check that every module root has an entry in pull-request.yml's check-changes filter.

Each top-level directory holding a module in settings.gradle.kts needs a '<root>/**'
line, or a PR that touches only that module skips CI. Exits non-zero on drift.
"""

import re
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

# Filter roots that are intentionally not Gradle module roots
# (CI/workflow implementation + shared build infrastructure).
ALLOWED_INFRA_ROOTS = {'.github', 'build-logic', 'config', 'gradle'}
ALLOWED_EXTRA_ROOTS = {'baselineprofile'}


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
        for path in re.findall(r"-\s*'([^']+/\*\*)'", workflow)
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
