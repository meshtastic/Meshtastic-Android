fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## Android

### android upload_internal

```sh
[bundle exec] fastlane android upload_internal
```

Upload a built Google release bundle to the internal track on Google Play. Pass aab:<path>

### android play_listing

```sh
[bundle exec] fastlane android play_listing
```

Upload the store listing - title, descriptions, feature graphic, icon and screenshots - for every locale under fastlane/metadata/android. Touches no build or track. Dry-runs unless validate_only:false

### android play_track_releases

```sh
[bundle exec] fastlane android play_track_releases
```

Write every release on a Play track (status, version codes, user fraction) as JSON. Pass track:<name> out:<path>. Reads only; the edit is discarded

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
