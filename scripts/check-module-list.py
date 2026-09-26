#!/usr/bin/env python3
"""Check ALL_MODULES_FULL in RootConventionPlugin.kt against settings.gradle.kts.

The list is a hand-maintained copy of the settings includes, because iterating
subprojects {} is incompatible with Isolated Projects. A module missing from it is
absent from Dokka aggregation, Kover aggregation and kmpSmokeCompile. Exits non-zero
on drift.
"""

import re
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

# Test harnesses and generators kept out of root aggregation. Exempt in both
# directions: the guard neither forces them in nor lets them be added back.
EXEMPT = {
    ':baselineprofile',
    ':core:konsist',
    ':docs-screenshots',
    ':schema-strings',
    ':screenshot-tests',
    ':store-screenshots',
}


def main() -> None:
    settings = (REPO_ROOT / 'settings.gradle.kts').read_text()
    plugin = (
        REPO_ROOT / 'build-logic/convention/src/main/kotlin/RootConventionPlugin.kt'
    ).read_text()

    include = settings[settings.index('include('):]
    modules = set(re.findall(r'"(:[^"]+)"', include[: include.index('\n)')]))

    # ALL_MODULES_FULL appears twice (declaration and use), so bound the slice to
    # the declaration's own closing paren rather than searching for the name.
    decl = plugin[plugin.index('ALL_MODULES_FULL ='):]
    listed = set(re.findall(r'"(:[^"]+)"', decl[: decl.index('\n    )')]))

    missing = sorted(modules - listed - EXEMPT)
    extra = sorted(listed - modules)
    readded = sorted(listed & EXEMPT)

    problems = []
    for m in missing:
        problems.append(
            f'{m} is in settings.gradle.kts but not ALL_MODULES_FULL -- it is absent '
            'from Dokka/Kover aggregation and kmpSmokeCompile'
        )
    for m in extra:
        problems.append(f'{m} is in ALL_MODULES_FULL but no longer in settings.gradle.kts')
    for m in readded:
        problems.append(
            f'{m} is exempt from root aggregation (#6412) but present in '
            'ALL_MODULES_FULL -- remove it, or drop it from the exempt set here'
        )

    if problems:
        print('Root module list drift detected:')
        for p in problems:
            print('  -', p)
        raise SystemExit(1)

    print(f'{len(listed)} modules verified against settings.gradle.kts '
          f'({len(EXEMPT)} exempt).')


if __name__ == '__main__':
    main()
