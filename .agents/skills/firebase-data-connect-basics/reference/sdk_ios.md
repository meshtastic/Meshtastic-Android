# iOS SDK

Consult this file when writing iOS application code (Swift) that interacts with the SQL Connect backend.

### Best Practices for Agents
- **Understand Operation Storage**: SQL Connect queries and mutations are stored on the server like Cloud Functions. **Whenever you update operations, you must regenerate the SDK and redeploy services** that use it to avoid breaking clients.
- **Resilient Enum Handling**: The generated SDK forces handling of unknown values by adding an `._UNKNOWN` case. Swift enforces exhaustive switch statements, so you must handle this case.
- **Observable Macro**: By default, query refs support the `@Observable` macro (iOS 17+), making them ideal for binding to SwiftUI views. The bindable query results are available in the `data` variable of the query ref.
- **Handle Errors**: Use `try await` with operation execution as they are asynchronous and may throw errors.

### Dependencies (Package.swift or SPM)

Configure the generated SDK as a package dependency in Xcode.

### Initialization

Retrieve the generated connector instance:

```swift
import FirebaseCore
import FirebaseDataConnect

// Assuming connector name is 'movies' in connector.yaml
// The connector name is the lower camel case connectorId defined in connector.yaml suffixed with the word 'Connector'
let connector = DataConnect.moviesConnector

// For local development with emulator
// Defaults to 127.0.0.1:9399
connector.useEmulator() 
// Or specify a non-default port:
// connector.useEmulator(port: 9999)
```

### Calling Operations

#### Basic Query
```swift
let result = try await connector.listMovies.execute()
for movie in result.data.movies {
    print(movie.title)
}
```

#### Mutation
```swift
let mutationResult = try await connector.createMovieMutation.execute(
  title: "Empire Strikes Back",
  releaseYear: 1980,
  genre: "Sci-Fi",
  rating: 5
)
```

### Resilient Enum Handling
Handle generated enums exhaustively, including the `._UNKNOWN` case.

```swift
do {
    let result = try await DataConnect.moviesConnector.listMovies.execute()
    if let data = result.data {
        for movie in data.movies {
            switch movie.aspectratio {
                case .ACADEMY: print("academy")
                case .WIDESCREEN: print("widescreen")
                case .ANAMORPHIC: print("anamorphic")
                case ._UNKNOWN(let unknownAspect): print("Unknown: \(unknownAspect)")
            }
        }
    }
} catch {
    // handle error
}
```

### Client-Side Caching
Enable caching in `connector.yaml` to reduce requests, support offline scenarios, enable realtime support for queries.

```yaml
generate:
  swiftSdk:
    outputDir: "../ios"
    package: "FirebaseDataConnectGenerated"
    clientCache:
      maxAge: 5s
      storage: persistent # Default for iOS is persistent
```

Use cache policies in code:
```swift
try await execute(fetchPolicy: .cacheOnly)
try await execute(fetchPolicy: .serverOnly)
```

### Subscriptions (Realtime)

#### SwiftUI Example

```swift
import Combine
import SwiftUI

struct ListMovieView: View {
    // QueryRef has the Observable attribute, so its properties will
    // automatically trigger updates on changes.
    private var queryRef = connector.listMoviesByGenreQuery.ref(genre: "Sci-Fi")

    // Store the handle to unsubscribe from query updates.
    @State private var querySub: AnyCancellable?

    var body: some View {
        VStack {
            // Use the query results in a View.
            ForEach(queryRef.data?.movies ?? [], id: \.id) { movie in
                    Text(movie.title)
                }
        }
        .onAppear {
            // Subscribe to the query for updates using the Observable macro.
            Task {
                do {
                    querySub = try await queryRef.subscribe().sink { _ in }
                } catch {
                    print("Error subscribing to query: \(error)")
                }
            }
        }
        .onDisappear {
          querySub?.cancel()
        }
    }
}
```

### Data Type Mapping Reference
- GraphQL `UUID` -> Swift `UUID`
- GraphQL `Date` -> Swift `FirebaseDataConnect.LocalDate`
- GraphQL `Timestamp` -> Swift `FirebaseCore.Timestamp`
- GraphQL `Int` -> Swift `Int`
- GraphQL `Float` -> Swift `Double`
- GraphQL `Boolean` -> Swift `Bool`
