# Genkit OpenAI Plugin (`genkit_openai`)

OpenAI-compatible API plugin for Genkit Dart. Supports OpenAI models and other compatible APIs (xAI, DeepSeek, Together AI, Groq, etc.).

## Basic Usage

```dart
import 'dart:io';
import 'package:genkit/genkit.dart';
import 'package:genkit_openai/genkit_openai.dart';

void main() async {
  final ai = Genkit(plugins: [
    openAI(apiKey: Platform.environment['OPENAI_API_KEY']),
  ]);

  final response = await ai.generate(
    model: openAI.model('gpt-4o'),
    prompt: 'Tell me a joke.',
  );
}
```

## Options

`OpenAIOptions` allows configuring sampling temperature, nucleus sampling, token generation, seed, etc:
`config: OpenAIOptions(temperature: 0.7, maxTokens: 100)`

## Groq API override

Specify custom `baseUrl` and custom models to integrate with third-party providers.

```dart
final ai = Genkit(plugins: [
  openAI(
    apiKey: Platform.environment['GROQ_API_KEY'],
    baseUrl: 'https://api.groq.com/openai/v1',
    models: [
      CustomModelDefinition(
        name: 'llama-3.3-70b-versatile',
        info: ModelInfo(
          label: 'Llama 3.3 70B',
          supports: {'multiturn': true, 'tools': true, 'systemRole': true},
        ),
      ),
    ],
  ),
]);

final response = await ai.generate(
  model: openAI.model('llama-3.3-70b-versatile'),
  prompt: 'Hello!',
);
```
