# `:baselineProfile`

Generates a [Baseline Profile](https://developer.android.com/topic/performance/baselineprofiles/overview)
for `:androidApp` — AOT-compiling the cold-start and first-frame code paths so ART doesn't pay the
JIT cost on first launch. Targets the **google** flavor (the variant most users run).

## Generate the profile (run on a device/emulator)

```bash
./gradlew :androidApp:generateGoogleReleaseBaselineProfile
```

Output is merged into `androidApp/src/google/generated/baselineProfiles/baseline-prof.txt`.
**Commit that file** — release builds package it via `androidx.profileinstaller`.

## Quantify the win

```bash
./gradlew :androidApp:benchmarkGoogleReleaseBaselineProfile
```

Compare `startupCompilationNone` vs `startupCompilationBaselineProfiles` in the output.

## Scope / TODO

- The journey (`BaselineProfileGenerator`) is cold-start only, since CI has no paired radio.
  Extend it with post-connection screens (node list, map, message thread) once a fake transport or
  connected device is wired into the harness — a more representative journey yields a better profile.
- For hermetic CI generation, swap `useConnectedDevices = true` in `build.gradle.kts` for a
  [Gradle Managed Device](https://developer.android.com/topic/performance/baselineprofiles/measure-baselineprofile#gradle-managed).
- f-droid currently inherits no profile (only `google` is produced). Add a second flavor here if
  the f-droid startup path ever diverges enough to matter.
