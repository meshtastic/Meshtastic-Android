# Changelog

All notable changes to the Optimize extension will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-04-03

### Added

- Constitution audit command (`/speckit.optimize.run`) with 6 analysis categories:
  - Token Budget Analysis
  - Rule Health Analysis
  - AI Interpretability Analysis
  - Semantic Compression
  - Constitution Coherence
  - Governance Echo Detection
- Token usage tracker (`/speckit.optimize.tokens`) with historical trend support
- Session learning command (`/speckit.optimize.learn`) for AI mistake pattern detection
- Configuration template with category toggles and thresholds
- Suggest-only design: no modifications without explicit user consent
- Spec-kit standard path resolution with redirect following
