# Flutter SDK

Consult this file when writing Flutter application code (Dart) that interacts with the SQL Connect backend.

### Best Practices for Agents
- **Understand Operation Storage**: SQL Connect queries and mutations are stored on the server like Cloud Functions. **Whenever you update operations, you must regenerate the SDK and redeploy services** that use it to avoid breaking clients.
- **Resilient Enum Handling**: The generated SDK forces handling of unknown values for enumerations. Client code must unwrap the `EnumValue` object into either `Known` or `Unknown` to handle schema updates gracefully.
- **Use Ref for Subscriptions**: Call `.ref()` on operation methods to get a `QueryRef` for advanced usage like subscriptions.
- **Builder Pattern for Optionals**: Use the builder pattern for mutations with optional fields.

### Installation

```bash
flutter pub add firebase_data_connect
```

### Imports

```dart
import 'package:firebase_data_connect/firebase_data_connect.dart';
// Import generated connector
import 'generated/movies.dart'; 
```

### Initialization

```dart
// For local development with emulator
MoviesConnector.instance.dataConnect.useDataConnectEmulator('127.0.0.1', 9399);
```

### Calling Operations

#### Basic Query
```dart
final response = await MoviesConnector.instance.listMovies().execute();
print(response.data.movies);
```

#### Mutation with Optional Fields (Builder Pattern)
```dart
await MoviesConnector.instance.createMovie(
  title: 'Empire Strikes Back', 
  releaseYear: 1980, 
  genre: 'Sci-Fi' 
).rating(5).execute();
```

### Resilient Enum Handling
When dealing with schema enumerations, use the forced unwrapping pattern to handle unknown values (e.g., when a new value is added to the backend but client is old).

```dart
final result = await MoviesConnector.instance.listMovies().execute();

if (result.data != null && result.data!.isNotEmpty) {
  handleEnumValue(result.data![0].aspectratio);
}

void handleEnumValue(EnumValue<AspectRatio> aspectValue) {
  if (aspectValue.value != null) {
    switch(aspectValue.value!) {
      case AspectRatio.ACADEMY:
        print("Academy aspect");
        break;
      case AspectRatio.WIDESCREEN:
        print("Widescreen aspect");
        break;
      // Add other known cases...
    }
  } else {
    print("Unknown aspect ratio detected: ${aspectValue.stringValue}");
  }
}
```

### Client-Side Caching
Enable caching in `connector.yaml` to reduce requests and support offline scenarios.

```yaml
generate:
  dartSdk: # Or the appropriate block for your project
    outputDir: ../dart/
    package: "dataconnect_generated"
    clientCache:
      maxAge: 5s
      storage: memory # Or persistent for native
```

Use policies in code:
```dart
// Only serve cached values
await queryRef.execute(fetchPolicy: QueryFetchPolicy.cacheOnly);

// Unconditionally fetch fresh values
await queryRef.execute(fetchPolicy: QueryFetchPolicy.serverOnly);
```

### Real-time Subscriptions

```dart
final queryRef = MoviesConnector.instance.getMovieById(id: "<MOVIE_ID>").ref();
final subscription = queryRef.subscribe().listen((result) {
  final movie = result.data.movie;
  if (movie != null) {
    updateUi(movie.title);
  }
});
```

### Data Type Mapping Reference
- GraphQL `Timestamp` -> Dart `firebase_data_connect.Timestamp`
- GraphQL `Int` -> Dart `int`
- GraphQL `Date` -> Dart `DateTime`
- GraphQL `UUID` -> Dart `string`
- GraphQL `Float` -> Dart `double`
- GraphQL `Boolean` -> Dart `bool`
