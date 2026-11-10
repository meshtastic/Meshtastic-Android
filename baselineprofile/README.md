# `:baselineprofile`

Generates a [Baseline Profile](https://developer.android.com/topic/performance/baselineprofiles/overview)
for `:androidApp` — AOT-compiling the cold-start and first-frame code paths so ART doesn't pay the
JIT cost on first launch. Targets the **google** flavor (the variant most users run).

## Generate the profile (run on a device/emulator)

```shell
./gradlew :androidApp:generateBaselineProfile
```

Output is merged into `androidApp/src/main/generated/baselineProfiles/` (`mergeIntoMain` in
`androidApp/build.gradle.kts`): `baseline-prof.txt`, plus `startup-prof.txt` because the generator sets
`includeInStartupProfile`. **Commit both files**: release builds of both flavors package them, and
`androidx.profileinstaller` writes the bundled profile to the device when the app first starts.

## Quantify the win

```shell
./gradlew :baselineprofile:connectedGoogleBenchmarkReleaseAndroidTest
```

Compare `startupCompilationNone` vs `startupCompilationBaselineProfiles` in the output.

## Scope / TODO

- The journey (`BaselineProfileGenerator`) is cold start to first frame only, since CI has no paired
  radio. Post-connection screens (node list, map, message thread) would make a more representative
  journey and a better profile; that needs a mesh in the harness, and Demo Mode's showcase mesh, which
  `:store-screenshots` connects to, is one candidate.
- For hermetic CI generation, swap `useConnectedDevices = true` in `build.gradle.kts` for a
  [Gradle Managed Device](https://developer.android.com/topic/performance/baselineprofiles/measure-baselineprofile#gradle-managed).
- Only `google` is produced, and `fdroid` ships that same profile from `src/main`. Add a second flavor
  here if the `fdroid` startup path ever diverges enough to matter.
