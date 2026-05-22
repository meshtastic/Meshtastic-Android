# Web SDK

Consult this file when writing client-side web code (TypeScript/JavaScript) that interacts with the SQL Connect backend.

### Best Practices for Agents
- **Understand Operation Storage**: SQL Connect queries and mutations are stored on the server like Cloud Functions. **Whenever you update operations, you must regenerate the SDK and redeploy services** that use it to avoid breaking clients.
- **Resilient Enum Handling**: JavaScript/TypeScript does not enforce exhaustive checks on enums. Always add a `default` branch to `switch` statements or an `else` branch to handle unknown values gracefully when schemas evolve.
- **TanStack Query vs. Native**: You can generate hooks for React/Angular using TanStack Query. Choose either TanStack or SQL Connect's built-in real-time and caching support, but do not use both in the same project. SQL Connect offers normalized caching and remote invalidation.
- **Emulator Connection**: `connectDataConnectEmulator` is only required if connecting to the emulator. Otherwise, the generated SDK auto-creates the instance.

### Installation

```bash
npm install firebase
firebase init dataconnect:sdk
```

### Initialization

```typescript
import { connectDataConnectEmulator, getDataConnect } from 'firebase/data-connect';
import { connectorConfig } from '@dataconnect/generated';

const dataConnect = getDataConnect(connectorConfig);
// Configure the SDK to use local emulator
connectDataConnectEmulator(dataConnect, 'localhost', 9399);
```

### Calling Operations

#### Using `executeQuery` (Preferred for clarity)
```typescript
import { executeQuery } from 'firebase/data-connect';
import { listMoviesRef } from '@dataconnect/generated';

const ref = listMoviesRef();
const { data } = await executeQuery(ref);
console.log(data.movies);
```

#### Using Action Shortcuts
```typescript
import { listMovies } from '@dataconnect/generated';

listMovies().then(data => showInUI(data));
```

### Resilient Enum Handling
Use a `default` case or check against `Object.values`.

```typescript
import { getOldestMovie } from '@dataconnect/generated';

const queryResult = await getOldestMovie();

if (queryResult.data) {
  const oldestMovieAspectRatio = queryResult.data.originalAspectRatio;
  switch (oldestMovieAspectRatio) {
      case AspectRatio.ACADEMY:
      case AspectRatio.WIDESCREEN:
        console.log('Filmed in Academy or Widescreen!');
        break;
      default:
        // The default case will catch FULLSCREEN, etc.
        console.log('Not filmed in Academy or Widescreen.');
        break;
  }
}
```

### Client-Side Caching
Enable caching in `connector.yaml`:

```yaml
generate:
  javascriptSdk:
    outputDir: ../web/
    package: "@dataconnect/generated"
    clientCache:
      maxAge: 5s
      storage: memory # Only memory is supported on Web
```

Use policies in code:
```typescript
await executeQuery(queryRef, QueryFetchPolicy.CACHE_ONLY);
await executeQuery(queryRef, QueryFetchPolicy.SERVER_ONLY);
```

### Subscriptions (Realtime)
Use `subscribe()` to receive live updates.

#### Web (Vanilla JS)
```typescript
import { subscribe } from 'firebase/data-connect';
import { getMovieByIdRef } from '@dataconnect/generated';

const queryRef = getMovieByIdRef({ id: "<MOVIE_ID>" });

const unsubscribe = subscribe(queryRef, (result) => {
  console.log("Updated result:", result);
});
```

### TanStack Query Support (React)
To use React hooks, re-run `firebase init dataconnect:sdk` after adding React.

#### Usage
```typescript
import { useListAllMovies } from "@dataconnect/generated/react";

function MyComponent() {
  const { isLoading, data, error } = useListAllMovies();
  // handle loading, error, and data
}
```

### Data Type Mapping Reference
- GraphQL `Timestamp` -> TypeScript `string`
- GraphQL `Date` -> TypeScript `string`
- GraphQL `UUID` -> TypeScript `string`
- GraphQL `Int64` -> TypeScript `string`
- GraphQL `Double` -> TypeScript `number`
- GraphQL `Float` -> TypeScript `number`
