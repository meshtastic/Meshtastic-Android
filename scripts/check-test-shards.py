#!/usr/bin/env python3
"""Check that every module with tests is wired into a reusable-check.yml test shard.

The shard task lists are hand-maintained. Every module in settings.gradle.kts must
have a test task in the shard matrix or be exempted here, and an exempt test-less
module that gains test sources fails until it is wired into a shard. Exits non-zero
on drift.
"""

import re
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

# Modules whose tests run in a dedicated job or only on-device --
# exempt unconditionally.
COVERED_ELSEWHERE = {
    ':screenshot-tests',   # dedicated screenshot-check job
    ':docs-screenshots',   # doc-screenshot generation (screenshot tooling)
    ':baselineprofile',    # benchmark module, instrumented-only
    ':store-screenshots',  # store-listing screenshots from the real app, instrumented-only
}
# Modules with no unit-test sources yet. One of these gaining test
# sources fails the guard: move it into a shard in reusable-check.yml
# and remove it from this list.
NO_TESTS_YET = {
    ':core:di',
    ':core:nfc',
    ':core:resources',
}


def has_test_sources(module: str) -> bool:
    root = REPO_ROOT / module.lstrip(':').replace(':', '/')
    return any(
        f.suffix == '.kt'
        for d in root.glob('src/*')
        if 'test' in d.name.lower()
        for f in d.rglob('*.kt')
    )


def main() -> None:
    settings = (REPO_ROOT / 'settings.gradle.kts').read_text()
    check = (REPO_ROOT / '.github/workflows/reusable-check.yml').read_text()

    modules = set(re.findall(r'"(:[^"]+)"', settings))

    shards = check.split('# ── Sharded Unit Tests')[1].split('# ── Android Build')[0]
    # A commented-out task runs nothing, so it must not count as coverage.
    shards = '\n'.join(re.sub(r'(^|\s)#.*$', '', line) for line in shards.splitlines())

    problems = []
    # Codecov uploads are gated on matrix.shard.flags, so a shard without it uploads nothing.
    matrix = shards.split('shard:', 1)[1].split('steps:', 1)[0]
    for entry in re.split(r'\n\s*- name: ', matrix)[1:]:
        if not re.search(r'^\s*flags: \S', entry, re.MULTILINE):
            problems.append(f'shard {entry.split()[0]} has no flags: line -- its Codecov uploads would all be skipped')
    for m in sorted(modules):
        if m in COVERED_ELSEWHERE:
            continue
        if m in NO_TESTS_YET:
            if has_test_sources(m):
                problems.append(f'{m} is exempt as test-less but has test sources -- wire it into a shard')
        # Require an actual test task (allTests / test / test<Variant>UnitTest),
        # not just any reference -- a lone kover entry must not satisfy this.
        elif not re.search(rf'{re.escape(m)}:(allTests|test)', shards):
            problems.append(f'{m} has no test task in any reusable-check.yml test shard')

    if problems:
        print('CI shard coverage drift detected:')
        for p in problems:
            print('  -', p)
        raise SystemExit(1)
    exempt = COVERED_ELSEWHERE | NO_TESTS_YET
    print(f'{len(modules) - len(modules & exempt)} modules verified against the shard matrix.')


if __name__ == '__main__':
    main()
