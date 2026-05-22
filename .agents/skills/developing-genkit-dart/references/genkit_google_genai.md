# Genkit Google GenAI Plugin (`genkit_google_genai`)

The Google AI plugin provides an interface against the official Google AI Gemini API.

## Usage

```dart
import 'package:genkit/genkit.dart';
import 'package:genkit_google_genai/genkit_google_genai.dart';

void main() async {
  // Initialize Genkit with the Google AI plugin
  final ai = Genkit(plugins: [googleAI()]);

  // Generate text
  final response = await ai.generate(
    model: googleAI.gemini('gemini-2.5-flash'),
    prompt: 'Tell me a joke about a developer.',
  );

  print(response.text);
}
```

## Embeddings

```dart
final embeddings = await ai.embedMany(
  embedder: googleAI.textEmbedding('text-embedding-004'),
  documents: [
    DocumentData(content: [TextPart(text: 'Hello world')]),
  ],
);
```

## Image Generation

The plugin also supports image generation models such as `gemini-2.5-flash-image`.

### Example (Nano Banana)

```dart
// Define an image generation flow
ai.defineFlow(
  name: 'imageGenerator',
  inputSchema: .string(defaultValue: 'A banana riding a bike'),
  outputSchema: Media.$schema,
  fn: (input, context) async {
    final response = await ai.generate(
      model: googleAI.gemini('gemini-2.5-flash-image'),
      prompt: input,
    );
    if (response.media == null) {
      throw Exception('No media generated');
    }
    return response.media!;
  },
);
```

The media (url field) contain base64 encoded data uri. You can decode it and save it as a file.

## Text-to-Speech (TTS)

You can use text-to-speech models to generate audio from text. The generated `Media` object will contain base64 encoded PCM audio in its data URI.

```dart
// Define a TTS flow
ai.defineFlow(
  name: 'textToSpeech',
  inputSchema: .string(defaultValue: 'Genkit is an amazing AI framework!'),
  outputSchema: Media.$schema,
  fn: (prompt, _) async {
    final response = await ai.generate(
      model: googleAI.gemini('gemini-2.5-flash-preview-tts'),
      prompt: prompt,
      config: GeminiTtsOptions(
        responseModalities: ['AUDIO'],
        speechConfig: SpeechConfig(
          voiceConfig: VoiceConfig(
            prebuiltVoiceConfig: PrebuiltVoiceConfig(voiceName: 'Puck'),
          ),
        ),
      ),
    );
    
    if (response.media != null) {
      return response.media!;
    }
    throw Exception('No audio generated');
  },
);
```

Google AI also supports multi-speaker TTS by configuring a `MultiSpeakerVoiceConfig` inside `SpeechConfig`.
