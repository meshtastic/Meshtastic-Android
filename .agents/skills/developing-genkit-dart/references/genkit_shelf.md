# Genkit Shelf Plugin (`genkit_shelf`)

Shelf integration for Genkit Dart, used to serve Genkit Flows.

## Standalone Server
Serve Genkit Flows easily on an isolated HTTP server using `startFlowServer`.

```dart
import 'package:genkit/genkit.dart';
import 'package:genkit_shelf/genkit_shelf.dart';

void main() async {
  final ai = Genkit();

  final flow = ai.defineFlow(
    name: 'myFlow',
    inputSchema: .string(),
    outputSchema: .string(),
    fn: (String input, _) async => 'Hello $input',
  );

  await startFlowServer(
    flows: [flow],
    port: 8080,
  );
}
```

## Existing Shelf Application
Mount Genkit Flow endpoints directly to an existing Shelf `Router` using `shelfHandler`. 

```dart
import 'package:genkit/genkit.dart';
import 'package:genkit_shelf/genkit_shelf.dart';
import 'package:shelf/shelf.dart';
import 'package:shelf/shelf_io.dart' as io;
import 'package:shelf_router/shelf_router.dart';

void main() async {
  final ai = Genkit();

  final flow = ai.defineFlow(
    name: 'myFlow',
    inputSchema: .string(),
    outputSchema: .string(),
    fn: (String input, _) async => 'Hello $input',
  );

  final router = Router();

  // Mount the flow handler at a specific path
  router.post('/myFlow', shelfHandler(flow));

  // Start the server
  await io.serve(router.call, 'localhost', 8080);
}
```

Access deployed flows using genkit client libraries (from Dart or JS).
