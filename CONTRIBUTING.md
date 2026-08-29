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
- `release/*` and `automation/*` are reserved for maintainers and automated workflows.
- Ensure your branch is up to date with the latest `main` branch before submitting a PR.
- Provide a meaningful title and description for your PR.
- Include information on how to test and/or replicate if it is not obvious.
- Include screenshots or logs if your change affects the UI or user experience.
- Be responsive to feedback and make requested changes promptly.
- Squash commits if requested by a maintainer.

## Issue Reporting

- Search existing issues before opening a new one to avoid duplicates.
- Provide a clear and descriptive title.
- Include steps to reproduce, expected behavior, and actual behavior.
- Attach logs, screenshots, or other helpful context if applicable.

## Community Standards

- Be respectful and considerate in all interactions.
- The Meshtastic Android project is subject to the [Meshtastic code of conduct](https://meshtastic.org/docs/legal/conduct/).
- Help others by reviewing pull requests and answering questions when possible.

Thank you for helping make Meshtastic-Android better! 
