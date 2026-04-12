---
applyTo: "**/androidMain/**/*.kt"
---

# Android Source-Set Rules

- This is `androidMain` — Android framework imports (`android.*`, `java.*`) are allowed here.
- Do NOT put business logic here. Business logic belongs in `commonMain`.
- If you find identical pure-Kotlin logic in both `androidMain` and `jvmMain`, extract it to `commonMain`.
- Use `expect`/`actual` only for small platform primitives. Prefer interfaces + DI.
- Keep `expect` declarations in `FileIo.kt` and shared helpers in `FileIoUtils.kt` to avoid JVM duplicate class errors.
