# Contributing to Meshtastic-Android

Thank you for your interest in contributing to Meshtastic-Android! We welcome contributions from everyone.

## How to Contribute

- **Fork the repository** and create your branch from `main`.
- **Keep each change focused** — one concern per commit.
- **Test your changes** thoroughly before submitting a pull request.
- **Submit a pull request** (PR) with a clear description of your changes and the problem they solve.
- If you are addressing an existing issue, please reference it in your PR (e.g., `Fixes #123`).
- First-time contributors are asked to sign the CLA — the CLA-assistant bot will prompt you on your first PR.

## Code Style

- Follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) for Kotlin code.
- Use Android Studio's default formatting settings.
- We use [spotless](https://github.com/diffplug/spotless) for automated code formatting. You can run `./gradlew spotlessApply` to format your code automatically.
  - You can also run `./gradlew spotlessInstallGitPrePushHook -Dorg.gradle.isolated-projects=false --no-configuration-cache` to install a pre-push Git hook that will run a `spotlessCheck`.
- Write clear, descriptive variable and function names.
- Add comments where necessary, especially for complex logic.
- Keep methods and classes focused and concise.
- **Strings:** Use localised strings via the **Compose Multiplatform Resource** library in `:core:resources`.
  - Do **not** use the legacy `androidApp/src/main/res/values/strings.xml`.
  - **Definition:** Add strings to `core/resources/src/commonMain/composeResources/values/strings.xml`.
  - **Schema strings:** every label and description in the protobufs field metadata is generated into
    `values/schema_strings.xml`, keyed by schema path (`Res.string.schema_lora_hop_limit`). A settings control
    that edits one protobuf field uses that key. Do not edit the file or write a `schema_` key by hand
    (`./gradlew :schema-strings:sync` regenerates it); wrong wording is a change to `meshtastic/protobufs`.
  - **Usage:**
    ```kotlin
    import org.jetbrains.compose.resources.stringResource
    import org.meshtastic.core.resources.Res
    import org.meshtastic.core.resources.your_string_key

    Text(text = stringResource(Res.string.your_string_key))
    ```

### Linting

Meshtastic-Android uses [Detekt](https://detekt.dev/) for static code analysis and linting of Kotlin code.

- Run `./gradlew detekt` before submitting your pull request to ensure your code passes all lint checks.
- Fix any Detekt warnings or errors reported in your code.
- Suppress individual warnings only as a last resort.
- You can find Detekt configuration in the `config/detekt` directory. If you believe a rule should be changed or suppressed, discuss it in your PR.

### Testing

Meshtastic-Android uses unit tests, Robolectric JVM tests, and instrumented UI tests to ensure code quality and reliability.

- **Unit tests** are located in the `src/test/` directory of each module.
- **Compose UI Tests (JVM)** are preferred for component testing and are also located in `src/test/` using **Robolectric**.
- **Instrumented tests** (including full E2E UI tests) are located in `src/androidTest/`. For Compose UI, use the [Jetpack Compose Testing APIs](https://developer.android.com/jetpack/compose/testing).

#### Guidelines for Testing

- Add or update tests for any new features or bug fixes.
- Ensure all tests pass by running:
  - `./gradlew test` for unit and Robolectric tests (pure-Android modules)
  - `./gradlew allTests` for KMP module tests (`core:*`, `feature:*`) — neither `test` nor `allTests` alone is sufficient; both must pass.
  - `./gradlew kmpSmokeCompile` when touching any KMP module — compiles the non-Android targets the unit tests don't cover
  - `./gradlew connectedAndroidTest` for instrumented tests
- For UI components, write Robolectric Compose tests where possible for faster execution.
- If your change is difficult to test, explain why in your pull request.

## Pull Requests

- Branches use conventional-commit style prefixes, e.g. `feat/<topic>`:
    - `feat/` — new user-visible behavior
    - `fix/` — bug fixes
    - `chore/` — tooling, deps, CI, cleanup
    - `docs/` — documentation only
    - `build/` — build system changes
    - `ci/` — CI workflow changes
    - `refactor/` — code structure changes
    - `test/` — test additions or fixes
    - `deps/` — dependency updates
- Spec-driven work takes no prefix from that list: a numeric spec prefix (`005-tak-v2-protocol`) or the timestamp form `YYYYMMDD-HHMMSS-feature-name` created by `/speckit.git.feature`. Both are valid.
- `release/*` and `automation/*` are reserved for maintainers and automated workflows.
- Ensure your branch is up to date with the latest `main` branch before submitting a PR.
- Provide a meaningful title and description for your PR.
- Include information on how to test and/or replicate if it is not obvious.
- Include logs where they show the behavior, as a fenced block rather than a screenshot of text. For UI changes see Screenshots below.
- Be responsive to feedback and make requested changes promptly.
- Squash commits if requested by a maintainer.

### Writing the description

Delete the tips block from the template first, then:

- **Lead with why.** One or two sentences on the problem the change solves,
  before any list of what changed. If it addresses an issue, say `Fixes #123`.
- **Group what changed** under whichever of these apply, and omit the rest:
  🌟 New Features · 🛠️ Refactoring & Architecture · 🐛 Bug Fixes ·
  🧹 Chores (dependencies, formatting, docs).
- **Call out architecture moves.** Files moving `androidMain` → `commonMain`,
  or Views → Compose, are a KMP migration milestone — say so explicitly rather
  than leaving it to the diff.
- **Add a "Testing Performed" section** whenever tests were added or changed,
  listing them. If a change is hard to test, say why there instead.

### Screenshots

UI changes want images — anything touching Compose, layouts, theming,
navigation, `feature/**` or `core/ui/**`. Use a Before / After table for a
visual change or fix:

| Before | After |
|--------|-------|
| <img src="<url>" width="300"/> | <img src="<url>" width="300"/> |

Paste or drag images directly into the PR so GitHub hosts them, or reference a
committed image by **commit-SHA** raw URL
(`https://raw.githubusercontent.com/<owner>/<repo>/<sha>/<path>`, spaces
encoded as `%20`) so the link survives the branch being deleted. Never use an
external image service.

**Never invent a URL or a placeholder image.** If a UI change has no real
screenshot yet, leave the template's commented image block in place for the
author to fill in.

## Issue Reporting

- Search existing issues before opening a new one to avoid duplicates.
- Provide a clear and descriptive title.
- Include steps to reproduce, expected behavior, and actual behavior.
- Attach logs, screenshots, or other helpful context if applicable.

## Forks and rebrands

Meshtastic-Android is GPL-3.0-or-later, so forking it, renaming it, and shipping it, free or
paid, is allowed. The license and the
[trademark policy](https://meshtastic.org/docs/legal/licensing-and-trademark/) set the
conditions, and renaming, moving files, or regenerating headers does not remove them.

- **Keep the license.** Your version is GPL-3.0-or-later with `LICENSE` intact. Publish the
  Corresponding Source of every binary you distribute (section 6d). Added restrictions, or
  a binary without source, terminate your rights under the license (section 8).
- **Keep the notices.** Every `Copyright (c) <year> Meshtastic LLC` header and the license
  text under it stay as they are. Say prominently that you modified the work, and when
  (section 5a). Stamping your name on files you did not touch claims work you did not do.
- **Keep the legal notices in the app.** Keep the About screen's copyright notice and its
  link to the source, with Meshtastic LLC still named (section 5d). Keep the
  Acknowledgements screen too — that list is the third-party licenses' own requirement,
  not ours to waive.
- **Leave the trademarks out of your branding.** The GPL is a copyright license and grants
  no trademark rights. The Meshtastic name and logo are trademarks of Meshtastic LLC. Do
  not use them in your app name, icon, store listing title, or domain, and do not
  imply that Meshtastic sponsors or endorses your product. Replace `app_name` and the
  launcher icons under `androidApp/src/main/`. The logo in `.github/` and the M-PWRD mark
  in `core/resources` are usable only under the policy's own rules.
- **Describe compatibility plainly.** "Works with Meshtastic® nodes" or "a fork of
  Meshtastic-Android" is fine. Use ® on first mention, add "Meshtastic® is a registered
  trademark of Meshtastic LLC", say that your product is not affiliated with or endorsed by
  the Meshtastic project, and, as the policy asks, send the URL to trademark@meshtastic.org
  within seven days of first use.
- **Use your own identity.** Change `APPLICATION_ID` in `config.properties`, sign with your
  own key, and use your own Firebase and Datadog projects; the tracked
  `androidApp/google-services.json` is a placeholder.

Questions about any of this: trademark@meshtastic.org.

## Community Standards

- Be respectful and considerate in all interactions.
- The Meshtastic Android project is subject to the [Meshtastic code of conduct](https://meshtastic.org/docs/legal/conduct/).
- Help others by reviewing pull requests and answering questions when possible.

Thank you for helping make Meshtastic-Android better! 
