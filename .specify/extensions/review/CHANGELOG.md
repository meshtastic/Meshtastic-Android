# Changelog

All notable changes to this extension will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.1] - 2026-04-04

### Fixed

- Removed invalid alias `speckit.review` (two-segment name); the canonical command `speckit.review.run` is now the only entry point — fixes `Validation Error: Invalid alias` on `specify extension add`
- Added alias naming validation to CI workflow to catch invalid aliases before release

## [1.0.0] - 2026-03-05

### Added

- Command: `/speckit.review.run` (alias: `/speckit.review`) — coordinator that orchestrates all agents
- Command: `/speckit.review.code` — code quality reviewer (guideline compliance, bugs, security)
- Command: `/speckit.review.comments` — comment accuracy analyzer (documentation, comment rot)
- Command: `/speckit.review.tests` — test coverage analyzer (behavioral coverage, critical gaps)
- Command: `/speckit.review.errors` — error handling reviewer (silent failures, catch blocks)
- Command: `/speckit.review.types` — type design analyzer (encapsulation, invariants)
- Command: `/speckit.review.simplify` — code simplification advisor (clarity, complexity)
- Targeted review via aspect arguments (`/speckit.review.run tests errors`)

### Requirements

- Spec Kit: >=0.1.0
- git: Required for change detection
