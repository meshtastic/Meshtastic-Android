# Changelog

All notable changes to this extension will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.3] - 2026-03-30

### Fixed

- Removed invalid alias `speckit.verify` (two-segment name); the canonical command `speckit.verify.run` is now the only entry point — fixes `Validation Error: Invalid alias` on `specify extension add`
- Added alias naming validation to CI workflow to catch invalid aliases before release

## [1.0.2] - 2026-03-30

### Fixed

- Removed `yq` external dependency from Bash and PowerShell config loading scripts; YAML parsing now uses only built-in tools (`grep`/`sed` and `Select-String`)

## [1.0.1] - 2026-03-26

### Added

- Cross-platform configuration loading scripts (Bash and PowerShell)
- CI workflow with Bash and PowerShell test suites
- `.extensionignore` for cleaner extension packaging
- Allow running verification from any branch by prompting the user to select a feature when not on a feature branch

### Changed

- Aligned Step 3 spec/plan loading with Steps 5–6 consumption in verify command
- Partial alignment with analyze extension conventions
- Removed review handoffs in favour of streamlined flow

### Fixed

- Fixed load configuration handling

## [1.0.0] - 2026-02-28

### Added

- Initial release of the Verify extension
- Command: `/speckit.verify.run` — post-implementation verification
- Checks implemented code against spec, plan, tasks, and constitution to catch gaps before review
- Produces a verification report with findings, metrics, and next actions
- `after_implement` hook for automatic verification prompting

### Requirements

- Spec Kit: >=0.1.0
