# Contributing to Meshtastic-Android

Thank you for your interest in contributing to Meshtastic-Android! We welcome contributions from everyone. Please take a moment to review these guidelines to help us maintain a high-quality, collaborative project.

## How to Contribute

- **Fork the repository** and create your branch from `main` or the appropriate feature branch.
- **Make your changes** in a logical, atomic manner.
- **Test your changes** thoroughly before submitting a pull request.
- **Submit a pull request** (PR) with a clear description of your changes and the problem they solve.
- If you are addressing an existing issue, please reference it in your PR (e.g., `Fixes #123`).

## Code Style

- Follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) for Kotlin code.
- Use Android Studio's default formatting settings.
- We use [spotless](https://github.com/diffplug/spotless) for automated code formatting. You can run `./gradlew spotlessApply` to format your code automatically.
  - You can also run `./gradlew spotlessInstallGitPrePushHook --no-configuration-cache` to install a pre-push Git hook that will run a `spotlessCheck`.
- Write clear, descriptive variable and function names.
- Add comments where necessary, especially for complex logic.
- Keep methods and classes focused and concise.
- Use localised strings; edit the English [`strings.xml`](app/src/main/res/values/strings.xml) file. CrowdIn will manage translations to other languages.
  - For example,

    ```kotlin
    // instead of hardcoding a string in your code:
    Text("Settings")

    // use the localised string resource:
    Text(stringResource(R.string.settings))
    ```

### Linting

Meshtastic-Android uses [Detekt](https://detekt.dev/) for static code analysis and linting of Kotlin code.

- Run `./gradlew detekt` before submitting your pull request to ensure your code passes all lint checks.
- Fix any Detekt warnings or errors reported in your code.
- It is possible to suppress warnings individually, but this should be used very sparingly.
- You can find Detekt configuration in the `config/detekt` directory. If you believe a rule should be changed or suppressed, discuss it in your PR.

Consistent linting helps keep the codebase clean and maintainable.

### Testing

Meshtastic-Android uses both unit tests and instrumented UI tests to ensure code quality and reliability.

- **Unit tests** are located in `app/src/test/java/` and should be written for all new logic where possible.
- **Instrumented tests** (including UI tests using Jetpack Compose) are located in `app/src/androidTest/java/`. For Compose UI, use the [Jetpack Compose Testing APIs](https://developer.android.com/jetpack/compose/testing).

#### Guidelines for Testing

- Add or update tests for any new features or bug fixes.
- Ensure all tests pass by running:
  - `./gradlew test` for unit tests
  - `./gradlew connectedAndroidTest` for instrumented tests
- For UI components, write Compose UI tests to verify user interactions and visual elements. See existing tests in `DebugFiltersTest.kt` for examples.
- If your change is difficult to test, explain why in your pull request.

Comprehensive testing helps prevent regressions and ensures a stable experience for all users.


## Pull Requests

- branches should start with:
    - bugfix
    - enhancement
    - dependencies
    - repo
    - reserved (release, automation)
- Ensure your branch is up to date with the latest `main` branch before submitting a PR.
- Provide a meaningful title and description for your PR.
- Inlude information on how to test and/or replicate if it is not obvious.
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
- The Meshtastic Android project is subject to the code of conduct for the parent project, which can be [found here:](https://meshtastic.org/docs/legal/conduct/)
- Help others by reviewing pull requests and answering questions when possible.

Thank you for helping make Meshtastic-Android better! 
