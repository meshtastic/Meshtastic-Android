# Genkit Core Framework

Genkit Dart is an AI SDK for Dart that provides a unified interface for text generation, structured output, tool calling, and agentic workflows.

## Initialization

```dart
import 'package:genkit/genkit.dart';
import 'package:genkit_google_genai/genkit_google_genai.dart'; // Or any other plugin

void main() async {
  // Pass plugins to use into the Genkit constructor
  final ai = Genkit(plugins: [googleAI()]);
}
```

## Generate Text

```dart
final response = await ai.generate(
  model: googleAI.gemini('gemini-2.5-flash'), // Needs a model reference from a plugin
  prompt: 'Explain quantum computing in simple terms.',
);

print(response.text);
```

## Stream Responses
```dart
final stream = ai.generateStream(
  model: googleAI.gemini('gemini-2.5-flash'),
  prompt: 'Write a short story about a robot learning to paint.',
);

await for (final chunk in stream) {
  print(chunk.text);
}
```

## Embed Text
```dart
final embeddings = await ai.embedMany(
  documents: [
    DocumentData(content: [TextPart(text: 'Hello world')]),
  ],
  embedder: googleAI.textEmbedding('text-embedding-004'),
);

print(embeddings.first.embedding);
```

## Define Tools
Models can use define actions and access external data via custom defined tools.
Requires the `schemantic` library for schema definitions.

```dart
import 'package:schemantic/schemantic.dart';

@Schema()
abstract class $WeatherInput {
  String get location;
}

final weatherTool = ai.defineTool(
  name: 'getWeather',
  description: 'Gets the current weather for a location',
  inputSchema: WeatherInput.$schema,
  fn: (input, _) async {
    // Call your weather API here
    return 'Weather in ${input.location}: 72°F and sunny';
  },
);

final response = await ai.generate(
  model: googleAI.gemini('gemini-2.5-flash'),
  prompt: 'What\'s the weather like in San Francisco?',
  toolNames: ['getWeather'], // Use the tools
);
```

## Structured Output

You can ensure the generative model returns a typed JSON object by providing an `outputSchema`.

```dart
@Schema()
abstract class $Person {
  String get name;
  int get age;
}

// ... inside main ...

final response = await ai.generate(
  model: googleAI.gemini('gemini-2.5-flash'),
  prompt: 'Generate a person named John Doe, age 30',
  outputSchema: Person.$schema, // Force the model to return this schema
);

final person = response.output; // Typed Person object
print('Name: ${person.name}, Age: ${person.age}');
```

## Define Flows
Wrap your AI logic in flows for better observability, testing, and deployment:

```dart
final jokeFlow = ai.defineFlow(
  name: 'tellJoke',
  inputSchema: .string(),
  outputSchema: .string(),
  fn: (topic, _) async {
    final response = await ai.generate(
      model: googleAI.gemini('gemini-2.5-flash'),
      prompt: 'Tell me a joke about $topic',
    );
    return response.text; // Value return
  },
);

final joke = await jokeFlow('programming');
print(joke);
```

### Streaming Flows
Stream data from your flows using `context.sendChunk(...)` and returning the final value:

```dart
final streamStory = ai.defineFlow(
  name: 'streamStory',
  inputSchema: .string(),
  outputSchema: .string(),
  streamSchema: .string(),
  fn: (topic, context) async {
    final stream = ai.generateStream(
      model: googleAI.gemini('gemini-2.5-flash'),
      prompt: 'Write a story about $topic',
    );

    await for (final chunk in stream) {
      context.sendChunk(chunk.text); // Stream the chunks
    }
    return 'Story complete'; // Value return
  },
);
```

## Calling remote Flows from a dart client
The `genkit` package provides `package:genkit/client.dart` representing remote Genkit actions that can be invoked or streamed using type-safe definitions.

1. Defines a remote action
```dart
import 'package:genkit/client.dart';

final stringAction = defineRemoteAction(
  url: 'http://localhost:3400/my-flow',
  inputSchema: .string(),
  outputSchema: .string(),
);
```

2. Call the Remote Action (Non-streaming)
```dart
final response = await stringAction(input: 'Hello from Dart!');
print('Flow Response: $response');
```

3. Call the Remote Action (Streaming)
Use the `.stream()` method on the action flow, and access `stream.onResult` to wait on the async return value.
```dart
final streamAction = defineRemoteAction(
  url: 'http://localhost:3400/stream-story',
  inputSchema: .string(),
  outputSchema: .string(),
  streamSchema: .string(),
);

final stream = streamAction.stream(
  input: 'Tell me a short story about a Dart developer.',
);

await for (final chunk in stream) {
  print('Chunk: $chunk'); 
}

final finalResult = await stream.onResult;
print('\nFinal Response: $finalResult');
```

## Calling remote Flows from a Javascript client

Install `genkit` npm package:

```bash
npm install genkit
```

1. Call a remote flow (non-streaming)

```ts
import { runFlow } from 'genkit/beta/client';

async function callHelloFlow() {
  try {
    const result = await runFlow({
      url: 'http://127.0.0.1:3400/helloFlow', // Replace with your deployed flow's URL
      input: { name: 'Genkit User' },
    });
    console.log('Non-streaming result:', result.greeting);
  } catch (error) {
    console.error('Error calling helloFlow:', error);
  }
}

callHelloFlow();
```

2. Call a remote flow (streaming)

```ts
import { streamFlow } from 'genkit/beta/client';

async function streamHelloFlow() {
  try {
    const result = streamFlow({
      url: 'http://127.0.0.1:3400/helloFlow', // Replace with your deployed flow's URL
      input: { name: 'Streaming User' },
    });

    // Process the stream chunks as they arrive
    for await (const chunk of result.stream) {
      console.log('Stream chunk:', chunk);
    }

    // Get the final complete response
    const finalOutput = await result.output;
    console.log('Final streaming output:', finalOutput.greeting);
  } catch (error) {
    console.error('Error streaming helloFlow:', error);
  }
}

streamHelloFlow();
```

## Data Models

Genkit uses standard data models for representing prompts (messages & parts) and responses. These classes are implemented using schemantic library.

```dart
import 'package:genkit/genkit.dart';
import 'package:schemantic/schemantic.dart';

@Schema()
abstract class $MyDataModel {
  // uses Genkit's Message schema (not schemantic's Message)
  List<$Message> get messages;
  List<$Part> get parts;
}

void example() {
  // --- Parts ---
  // A Text part
  final textPart = TextPart(text: 'some text', metadata: {'foo': 'bar'});

  // A Media/Image part
  final mediaPart = MediaPart(
    media: Media(url: 'https://...', contentType: 'image/png'),
    metadata: {'foo': 'bar'},
  );

  // A Tool Request initiated by the model
  final toolRequestPart = ToolRequestPart(
    toolRequest: ToolRequest(
      name: 'get_weather',
      ref: 'abc',
      input: {'location': 'Paris, France'},
    ),
    metadata: {'foo': 'bar'},
  );

  // The resulting data from a Tool execution
  final toolResponsePart = ToolResponsePart(
    toolResponse: ToolResponse(
      name: 'get_weather',
      ref: 'abc',
      output: {'temperature': '20C'},
    ),
    metadata: {'foo': 'bar'},
  );

  // Model reasoning (e.g. for Claude's "thinking" models)
  final reasoningPart = ReasoningPart(
    reasoning: 'thinking...',
    metadata: {'foo': 'bar'},
  );

  // A custom fallback part
  final customPart = CustomPart(
    custom: {'provider': {'specific': 'data'}},
    metadata: {'foo': 'bar'},
  );

  // --- Messages ---
  final systemMessage = Message(
    role: Role.system,
    content: [textPart, mediaPart],
    metadata: {'foo': 'bar'},
  );

  final userMessage = Message(
    role: Role.user,
    content: [textPart, mediaPart], // Can contain media (multimodal)
  );

  final modelMessage = Message(
    role: Role.model,
    // Models can emit text, tool requests, reasoning, or custom parts
    content: [textPart, toolRequestPart, reasoningPart, customPart],
  );

  // --- Ergonomic Data Access (schema_extensions.dart) ---
  // The Genkit SDK provides extensions on `Message` and `Part` to easily access fields
  // without needing to cast them manually.

  // Get concatenated text from all TextParts in a Message
  print(modelMessage.text); 
  
  // Get the first Media object from a Message
  print(modelMessage.media?.url);

  // Iterate over tool requests in a Message
  for (final toolReq in modelMessage.toolRequests) {
    print(toolReq.name);
  }

  // Inspect individual parts
  for (final part in modelMessage.content) {
    if (part.isText) print(part.text);
    if (part.isMedia) print(part.media?.url);
    if (part.isToolRequest) print(part.toolRequest?.name);
    if (part.isToolResponse) print(part.toolResponse?.name);
    if (part.isReasoning) print(part.reasoning);
    if (part.isCustom) print(part.custom);
  }

  // --- Streaming Chunks ---
  // Data emitted by ai.generateStream() calls
  final generateResponseChunk = ModelResponseChunk(
    content: [textPart],
    index: 0, // Index of the message this chunk belongs to
    aggregated: false, 
  );

  // Chunks also have text and media accessors
  print(generateResponseChunk.text);

  // --- Advanced: Schemas ---
  // Use Genkit type schemas directly in Schemantic validations
  final messageSchema = Message.$schema;
  final partSchema = Part.$schema;
  
  final mySchema = SchemanticType.map(
    .string(),
    .list(Message.$schema), // Requires a list of Messages
  );

  // --- Generate Response ---
  // ai.generate() returns a GenerateResponseHelper which provides ergonomic getters
  // over the underlying ModelResponse:
  final response = await ai.generate(...);
  
  print(response.text); // Concatenated text
  print(response.media?.url); // First media part
  print(response.toolRequests); // All tool requests
  print(response.interrupts); // Tool requests that triggered an interrupt
  print(response.messages); // Full history of the conversation, including the request and response
  print(response.output); // Structured typed output (if outputSchema was used)
}
```
