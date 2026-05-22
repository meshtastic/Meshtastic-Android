# Android SDK

Consult this file when writing Android application code (Kotlin) that interacts with the SQL Connect backend.

### Best Practices for Agents
- **Understand Operation Storage**: SQL Connect queries and mutations are stored on the server like Cloud Functions. **Whenever you update operations, you must regenerate the SDK and redeploy services** that use it to avoid breaking clients.
- **Resilient Enum Handling**: The generated SDK forces handling of unknown values by wrapping them in `EnumValue`. You must unwrap it into `EnumValue.Known` or `EnumValue.Unknown` to handle schema updates gracefully.
- **Flow Behavior**: While you can collect a Flow from a query, note that **this Flow is not updated in real-time automatically** by default. It only produces a result when a new query result is retrieved using a call to the query's `execute()` method.
- **Leverage Coroutines**: Call `.execute()` within a coroutine scope for asynchronous operations.

### Dependencies (build.gradle.kts)

Ensure you have the Kotlin Serialization plugin and standard SQL Connect dependencies:

```kotlin
plugins {
    kotlin("plugin.serialization") version "1.8.22" // Must match Kotlin version
}

dependencies {
    // [AGENT] Fetch the latest available BoM version from https://firebase.google.com/support/release-notes/android before adding this
    implementation(platform("com.google.firebase:firebase-bom:34.12.0"))
    implementation("com.google.firebase:firebase-dataconnect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.5.1")
}
```

### Initialization

Retrieve the generated connector instance:

```kotlin
import com.google.firebase.dataconnect.generated.MoviesConnector

val connector = MoviesConnector.instance

// For local development with emulator
// Defaults to correct host for Android emulator (10.0.2.2)
connector.dataConnect.useEmulator()
// Or specify a non-default port:
// connector.dataConnect.useEmulator(port = 9999)
```

### Calling Operations

#### Basic Query
```kotlin
val result = connector.listMovies.execute()
result.data.movies.forEach { movie ->
    println(movie.title)
}
```

#### Mutation
```kotlin
val newMovie = connector.createMovie.execute(
    title = "Empire Strikes Back",
    releaseYear = 1980,
    genre = "Sci-Fi",
    rating = 5
)
```

### Resilient Enum Handling
Unwrap the `EnumValue` to handle known and unknown cases safely.

```kotlin
val result = connector.listMovies.execute()

result.data.movies.forEach { movie ->
    when (val aspect = movie.aspectratio) {
        is EnumValue.Known -> println("Known aspect: ${aspect.value.name}")
        is EnumValue.Unknown -> println("Unknown aspect: ${aspect.stringValue}")
    }
}
```

### Client-Side Caching
Enable caching in `connector.yaml` to reduce requests and support offline scenarios.

```yaml
generate:
  kotlinSdk:
    outputDir: "../android"
    package: "com.google.firebase.dataconnect.generated"
    clientCache:
      maxAge: 5s
      storage: persistent # Default for Android is persistent
```

Use policies in code:
```kotlin
val queryResult = queryRef.execute(QueryRef.FetchPolicy.CACHE_ONLY)
val queryResult = queryRef.execute(QueryRef.FetchPolicy.SERVER_ONLY)
```

### Data Type Mapping Reference
- GraphQL `String` -> Kotlin `String`
- GraphQL `Int` -> Kotlin `Int` (32-bit)
- GraphQL `Float` -> Kotlin `Double` (64-bit)
- GraphQL `Boolean` -> Kotlin `Boolean`
- GraphQL `UUID` -> Kotlin `java.util.UUID`
- GraphQL `Date` -> Kotlin `com.google.firebase.dataconnect.LocalDate`
- GraphQL `Timestamp` -> Kotlin `com.google.firebase.Timestamp`
- GraphQL `Int64` -> Kotlin `Long`
- GraphQL `Any` -> Kotlin `com.google.firebase.dataconnect.AnyValue`
