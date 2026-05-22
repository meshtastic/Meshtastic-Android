# Genkit Examples

This reference contains minimal, reproducible examples (MREs) for common Genkit patterns.

> **Disclaimer**: These examples use **Google AI** models (`googleAI`, `gemini-*`) for demonstration. The patterns apply to **any provider**. To use a different provider:
> 1. Search the docs for the correct plugin: `genkit docs:search "plugins"`.
> 2. Install and configure the plugin.
> 3. Swap the model reference in the code.

## Basic Text Generation

```ts
import { genkit } from "genkit";
import { googleAI } from "@genkit-ai/google-genai";

const ai = genkit({
  plugins: [googleAI()],
});

const { text } = await ai.generate({
  model: googleAI.model('gemini-2.5-flash'),
  prompt: 'Tell me a story in a pirate accent',
});
```

## Structured Output

```ts
import { z } from 'genkit';

const JokeSchema = z.object({
  setup: z.string().describe('The setup of the joke'),
  punchline: z.string().describe('The punchline'),
});

const response = await ai.generate({
  model: googleAI.model('gemini-2.5-flash'),
  prompt: 'Tell me a joke about developers.',
  output: { schema: JokeSchema },
});

// response.output is strongly typed
const joke = response.output; 
if (joke) {
  console.log(`${joke.setup} ... ${joke.punchline}`);
}
```

## Streaming

```ts
const { stream, response } = ai.generateStream({
  model: googleAI.model('gemini-2.5-flash'),
  prompt: 'Tell a long story about a developer using Genkit.',
});

for await (const chunk of stream) {
  console.log(chunk.text);
}

// Await the final response
const finalResponse = await response;
console.log('Complete:', finalResponse.text);
```

## Advanced Configuration

### Thinking Mode (Gemini 3 Only)

Enable "thinking" process for complex reasoning tasks.

```ts
const response = await ai.generate({
  model: googleAI.model('gemini-3.1-pro-preview'),
  prompt: 'what is heavier, one kilo of steel or one kilo of feathers',
  config: {
    thinkingConfig: {
      thinkingLevel: 'HIGH', // or 'LOW'
      includeThoughts: true, // Returns thought process in response
    },
  },
});
```

### Google Search Grounding

Enable models to access current information via Google Search.

```ts
const response = await ai.generate({
  model: googleAI.model('gemini-2.5-flash'),
  prompt: 'What are the top tech news stories this week?',
  config: {
    googleSearchRetrieval: true,
  },
});

// Access grounding metadata (sources)
const groundingMetadata = (response.custom as any)?.candidates?.[0]?.groundingMetadata;
if (groundingMetadata) {
  console.log('Sources:', groundingMetadata.groundingChunks);
}
```

## Multimodal Generation

### Image Generation / Editing

**Critical**: You MUST set `responseModalities: ['TEXT', 'IMAGE']` when using image generation models.

```ts
// Generate an image
const { media } = await ai.generate({
  model: googleAI.model('gemini-2.5-flash-image'),
  config: { responseModalities: ['TEXT', 'IMAGE'] },
  prompt: "generate a picture of a unicorn wearing a space suit on the moon",
});
// media.url contains the data URI
```

```ts
// Edit an image
const { media } = await ai.generate({
  model: googleAI.model('gemini-2.5-flash-image'),
  config: { responseModalities: ['TEXT', 'IMAGE'] },
  prompt: [
    { text: "change the person's outfit to a banana costume" },
    { media: { url: "https://example.com/photo.jpg" } },
  ],
});
```

### Speech Generation (TTS)

Generate audio from text.

```ts
import { writeFile } from 'node:fs/promises';

const { media } = await ai.generate({
  model: googleAI.model('gemini-2.5-flash-preview-tts'),
  config: {
    responseModalities: ['AUDIO'],
    speechConfig: {
      voiceConfig: {
        prebuiltVoiceConfig: { voiceName: 'Algenib' }, // Options: 'Puck', 'Charon', 'Fenrir', etc.
      },
    },
  },
  prompt: 'Genkit is an amazing library',
});

// The response contains raw PCM data in media.url (base64 encoded).
// CAUTION: This is NOT an MP3/WAV file. It requires conversion (e.g., PCM to WAV).
// DO NOT GUESS. Run `genkit docs:search "speech audio"` to find the correct
// conversion code for your provider.
```
