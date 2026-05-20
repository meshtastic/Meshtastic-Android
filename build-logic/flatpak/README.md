# Meshtastic Flatpak Source Manifest Generator

This directory contains the isolated, lightweight `:flatpak` subproject under `build-logic`. It registers and exposes the formal Gradle plugin `meshtastic.flatpak` (`FlatpakConventionPlugin`) to automate generating Flathub-compliant offline dependency manifests.

---

## Purpose

To build sandboxed desktop applications on Flathub completely offline (`--offline`), the Flatpak builder requires an exact, pre-calculated registry of all remote dependency locations and their cryptographic hashes (`flatpak-sources.json`). 

Previously, this logic was mixed in loose scripts or monolithic build conventions. Isolating it into this standalone subproject provides:
* **Clean Boundaries**: Decouples packaging/publishing details from standard multiplatform compile configurations.
* **First-Class Configuration Caching**: Safe from eager state evaluations and non-serializable property capture.
* **Ease of Sharing/Publishing**: Simplifies future distribution or independent publication of the plugin.

---

## Key Features

### 1. Snapshot Metadata Harvesting
Standard Maven snapshot repositories (e.g., Sonatype Snapshots) return `404` errors when fetching non-timestamped `-SNAPSHOT` dependencies directly. This plugin dynamically locates and parses local cached `maven-metadata.xml` files inside Gradle's cached directories, resolves the unique timestamped snapshot coordinate, and constructs exact, direct download URLs while preserving local filename bindings.

### 2. JitPack URL Routing
Automatically identifies external dependencies belonging to the `com.github.*` group (hosted on JitPack) and routes their `primaryUrl` to `https://jitpack.io` instead of attempting standard Maven Central lookup, preventing sandboxed download failures.

### 3. High-Performance Optimizations
* **Single-Pass Metadata Indexing**: Scans cached metadata files exactly once on-demand, caching them in an in-memory `$O(1)$` lookup map.
* **Deferred Cryptographic Hashing**: Defers expensive SHA-256 calculation until after candidate files are fully deduplicated and sorted.

---

## Usage

Apply the plugin to the root project's `build.gradle.kts`:

```kotlin
plugins {
    id("meshtastic.flatpak")
}
```

### Running the Generator Task

Execute the registered custom task to sweep your Gradle local modules cache and generate/overwrite the root `flatpak-sources.json`:

```bash
./gradlew :generateFlatpakSourcesFromCache
```

### Custom Cache Directory

By default, the task scans the standard Gradle user home caches directory (`~/.gradle/caches/modules-2/files-2.1`). You can supply a custom cache directory using the `flatpak.cache.dir` Gradle property:

```bash
./gradlew :generateFlatpakSourcesFromCache -Pflatpak.cache.dir="/custom/cache/path"
```

---

## Architecture

* **[FlatpakConventionPlugin.kt](src/main/kotlin/FlatpakConventionPlugin.kt)**: Registers the `generateFlatpakSourcesFromCache` task using lazy provider configuration.
* **[GenerateFlatpakSourcesTask.kt](src/main/kotlin/org/meshtastic/flatpak/GenerateFlatpakSourcesTask.kt)**: The native JVM-based custom task responsible for Gradle files scanning, metadata harvesting, and JSON generation.
