---
applyTo: "**/commonMain/**/*.kt"
---

# KMP commonMain Rules

- NEVER import `java.*` or `android.*` in `commonMain`.
- Use `org.meshtastic.core.common.util.ioDispatcher` instead of `Dispatchers.IO`.
- Use Okio (`BufferedSource`/`BufferedSink`) instead of `java.io.*`.
- Use `kotlinx.coroutines.sync.Mutex` instead of `java.util.concurrent.locks.*`.
- Use `atomicfu` or Mutex-guarded `mutableMapOf()` instead of `ConcurrentHashMap`.
- Use `jetbrains-*` catalog aliases for lifecycle/navigation dependencies.
- Use `compose-multiplatform-*` catalog aliases for CMP dependencies.
- Never use plain `androidx.compose` dependencies in `commonMain`.
- Strings: use `stringResource(Res.string.key)` from `core:resources`. No hardcoded strings.
- CMP `stringResource` only supports `%N$s` and `%N$d` — pre-format floats with `NumberFormatter.format()`.
- Use `MetricFormatter` from `core:common` for display strings (temperature, voltage, percent, signal). Avoid scattered `formatString("%.1f°C", val)` calls.
- Check `gradle/libs.versions.toml` before adding dependencies.
- Use `safeCatching {}` from `core:common` instead of `runCatching {}` in coroutine/suspend contexts. Keep `runCatching` only in cleanup/teardown code.
- Use `kotlinx.coroutines.CancellationException`, not `kotlin.coroutines.cancellation.CancellationException`.
