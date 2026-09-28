---
name: navigation-and-di
description: Koin Annotations dependency injection and JetBrains Navigation 3 in Meshtastic-Android, including the anti-patterns that compile cleanly and then fail at runtime. Use this whenever you add a screen, a route, a ViewModel or a Koin module, or when navigation or injection behaves unexpectedly.
---

# Skill: DI and Navigation 3 Architecture

## Description
This skill covers dependency injection (Koin Annotations 4.2.x) and Navigation 3 1.2 (the JetBrains `navigation3-ui` mirror over AndroidX `navigation3-runtime`) architecture, constraints, and anti-patterns within the Meshtastic-Android KMP codebase.

## Dependency Injection (Koin)

### Guidelines
1. **Annotations First:** Use `@Module`, `@ComponentScan`, and `@KoinViewModel` annotations directly in `commonMain` shared modules to encapsulate dependency graphs per feature.
2. **App Root Assembly:** Don't assume feature/core `@Module` classes are active automatically. Ensure they are included by the app root module (`@Module(includes = [...])`) in `androidApp/src/main/kotlin/org/meshtastic/app/di/AppKoinModule.kt` and `desktopApp/.../DesktopKoinModule.kt`.
3. **No Platform Bleed:** Don't put Android framework dependencies (`Context`, `Activity`, `Application`) into shared `commonMain` business logic. Inject interfaces instead.
4. **Resolution:** Resolve app-layer wrappers via `koinViewModel()` or injected bindings within Compose navigation graphs.

### Anti-Patterns
- **Compile Safety Outside An Entry Point:** Do **not** enable `compileSafety` on a library module. Validation is whole-graph and runs at the `@KoinApplication` entry point, so a library validates against a graph it cannot see and reports `KOIN-D003` for definitions its consumers supply. `KoinConventionPlugin` enables it only for the modules in `KOIN_ENTRY_POINTS`.
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
- `compileSafety` is **on at the entry points only** (`:androidApp`, `:desktopApp`). Plugin 1.1.0 replaced per-module validation with whole-graph validation, so the flag is only meaningful where the graph is assembled. A new app target must be added to `KOIN_ENTRY_POINTS` or it is never validated.
- A definition two `@Module(includes = ...)` levels below the entry point is invisible to the index. The flavor modules carry `@Configuration` as well as their `includes` for this reason; dropping the `includes` removes them from the **runtime** graph, which `KoinVerificationTest` catches.
- Hand-written DSL `module { }` definitions are not reachable by the assembled graph, which is why `:desktopApp` uses `@Module` classes.

## Navigation 3

### Guidelines
1. **Types:** Use Navigation 3 types consistently (`NavKey`, `NavBackStack`, `EntryProviderScope`).
2. **Typed Routes:** Keep route definitions in `core:navigation/src/commonMain/.../Routes.kt` as `@Serializable sealed interface` hierarchies. Don't use ad-hoc strings.
3. **Graph Assembly:** Define feature navigation graphs as extension functions on `EntryProviderScope<NavKey>` in `commonMain` (e.g., `fun EntryProviderScope<NavKey>.settingsGraph(backStack)`).
4. **Host Integration:** Use `MeshtasticNavDisplay` (from `core:ui/commonMain`) as the Navigation 3 host. It owns the entry decorators; do not create them in app hosts or feature modules.
5. **Scenes:** `MeshtasticNavDisplay` renders `ListDetailSceneStrategy` scenes (`listPane()`, `detailPane()`, `extraPane()` entry metadata) and falls back to a single pane. It registers no dialog or supporting-pane strategy, so that metadata has no effect.
6. **Back Handlers:** Use `NavigationBackHandler` from `androidx.navigationevent:navigationevent-compose` for back gestures in multiplatform code. Do not use Android's `BackHandler`.
7. **Deep Links:** Use `DeepLinkRouter.route()` in `core:navigation` to synthesize typed backstacks from RESTful paths.
8. **Tab Lifetime:** A hidden tab's entry ViewModels and saved state live until that entry is popped from its own stack; switching tabs does not clear them.

### Anti-Patterns
- **Single Backstack for Multiple Tabs:** Do **not** use a single `NavBackStack` list for multiple tabs. Use `MultiBackstack` (from `core:navigation`).
- **Decorator Reuse Across Tabs:** Do **not** decorate several back stacks with one `NavEntryDecorator` set. Navigation 3 pops every entry missing from the stack it is given, so a shared saveable-state or ViewModel-store decorator clears the tab you just left. The `MultiBackstack` overload of `MeshtasticNavDisplay` gives every tab's stack its own saveable-state and ViewModel-store decorators through `rememberDecoratedNavEntries`, following the per-stack decorators of the Navigation 3 multiple back stacks recipe, and passes only the active tab's entries to `NavDisplay`. Its `entryProvider` must therefore resolve every tab's keys, not only the active tab's.
- **Custom Backstack Mutation:** Do **not** mutate back navigation with custom stacks disconnected from the app backstack. Mutate `NavBackStack<NavKey>` directly with `add(...)` and `removeLastOrNull()`.

## Reference Anchors
- **App Startup / Koin Bootstrap:** `androidApp/src/main/kotlin/org/meshtastic/app/MeshUtilApplication.kt`
- **DI Bootstrap Object:** `androidApp/src/main/kotlin/org/meshtastic/app/di/AndroidKoinApp.kt`
- **DI App Wiring:** `androidApp/src/main/kotlin/org/meshtastic/app/di/AppKoinModule.kt`
- **Shared Routes:** `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/Routes.kt`
- **Desktop Nav Shell:** `desktopApp/src/main/kotlin/org/meshtastic/desktop/ui/DesktopMainScreen.kt`
