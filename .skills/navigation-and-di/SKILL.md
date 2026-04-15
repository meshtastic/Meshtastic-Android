# Skill: DI and Navigation 3 Architecture

## Description
This skill covers dependency injection (Koin Annotations 4.2.x) and JetBrains Navigation 3 (1.1.x) architecture, constraints, and anti-patterns within the Meshtastic-Android KMP codebase.

## Dependency Injection (Koin)

### Guidelines
1. **Annotations First:** Use `@Module`, `@ComponentScan`, and `@KoinViewModel` annotations directly in `commonMain` shared modules to encapsulate dependency graphs per feature.
2. **App Root Assembly:** Don't assume feature/core `@Module` classes are active automatically. Ensure they are included by the app root module (`@Module(includes = [...])`) in `app/src/main/kotlin/org/meshtastic/app/di/AppKoinModule.kt` and `desktop/.../DesktopKoinModule.kt`.
3. **No Platform Bleed:** Don't put Android framework dependencies (`Context`, `Activity`, `Application`) into shared `commonMain` business logic. Inject interfaces instead.
4. **Resolution:** Resolve app-layer wrappers via `koinViewModel()` or injected bindings within Compose navigation graphs.

### Anti-Patterns
- **A1 Module Compile Safety:** Do **not** enable `compileSafety`. It is a single boolean that enables A1 per-module checks — there is no separate A3 full-graph mode. Runtime graph verification is handled by `KoinVerificationTest` and `DesktopKoinTest` instead.
- **Default Parameters:** Do **not** expect Koin to inject default parameters automatically. The K2 plugin's `skipDefaultValues = true` behavior skips parameters with default Kotlin values.

### Koin Startup Pattern (K2 Compiler Plugin)
The project uses the **K2 Compiler Plugin** (`koin-compiler-plugin`, not KSP). The canonical startup uses the plugin's typed `startKoin<T>()` stub, which the plugin transforms at compile time via IR:
```kotlin
// Bootstrap class — separate from @Module, references the root module graph
@KoinApplication(modules = [AppKoinModule::class])
object AndroidKoinApp

// In Application.onCreate()
startKoin<AndroidKoinApp> {
    androidContext(this@MeshUtilApplication)
    workManagerFactory()
}
```
- `@KoinApplication` goes on a **dedicated bootstrap object**, not on a `@Module` class.
- `startKoin<T>()` (from `org.koin.plugin.module.dsl`) is a compiler plugin stub — if the plugin isn't applied, it throws `NotImplementedError`.
- `stopKoin()` uses the standard runtime API (`org.koin.core.context.stopKoin`).
- `compileSafety` must stay **disabled** — it enables A1 per-module checks that break our inverted-dependency architecture. There is no separate A3 full-graph flag.

## Navigation 3

### Guidelines
1. **Types:** Use Navigation 3 types consistently (`NavKey`, `NavBackStack`, `EntryProviderScope`).
2. **Typed Routes:** Keep route definitions in `core:navigation/src/commonMain/.../Routes.kt` as `@Serializable sealed interface` hierarchies. Don't use ad-hoc strings.
3. **Graph Assembly:** Define feature navigation graphs as extension functions on `EntryProviderScope<NavKey>` in `commonMain` (e.g., `fun EntryProviderScope<NavKey>.settingsGraph(backStack)`).
4. **Host Integration:** Use `MeshtasticNavDisplay` (from `core:ui/commonMain`) as the Navigation 3 host. Do not configure decorators manually inside feature modules.
5. **Back Handlers:** Use `NavigationBackHandler` from `androidx.navigationevent:navigationevent-compose` for back gestures in multiplatform code. Do not use Android's `BackHandler`.
6. **Deep Links:** Use `DeepLinkRouter.route()` in `core:navigation` to synthesize typed backstacks from RESTful paths.

### Anti-Patterns
- **Single Backstack for Multiple Tabs:** Do **not** use a single `NavBackStack` list for multiple tabs. Use `MultiBackstack` (from `core:navigation`).
- **Decorator Reuse Across Tabs:** Do **not** reuse the same `NavEntryDecorator` instances across different backstacks. When rendering an active tab in `MeshtasticNavDisplay`, you **must** supply a fresh set of decorators (using `remember(backStack) { ... }`) bound to the active backstack instance to prevent permanent `ViewModelStore` destruction.
- **Custom Backstack Mutation:** Do **not** mutate back navigation with custom stacks disconnected from the app backstack. Mutate `NavBackStack<NavKey>` directly with `add(...)` and `removeLastOrNull()`.

## Reference Anchors
- **App Startup / Koin Bootstrap:** `app/src/main/kotlin/org/meshtastic/app/MeshUtilApplication.kt`
- **DI Bootstrap Object:** `app/src/main/kotlin/org/meshtastic/app/di/AndroidKoinApp.kt`
- **DI App Wiring:** `app/src/main/kotlin/org/meshtastic/app/di/AppKoinModule.kt`
- **Shared Routes:** `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/Routes.kt`
- **Desktop Nav Shell:** `desktop/src/main/kotlin/org/meshtastic/desktop/ui/DesktopMainScreen.kt`
