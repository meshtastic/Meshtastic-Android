# Common Errors & Pitfalls

## When Typecheck Fails

**Before searching source code or docs**, check the sections below. Many type errors are caused by deprecated APIs or incorrect imports.

## Genkit v1.x vs Pre-1.0 Migration

Genkit v1.x introduced significant API changes. This section covers critical syntax updates.

### Package Imports

- **Correct (v1.x)**: Import core functionality (zod, genkit) from the main `genkit` package and plugins from their specific packages.
  ```ts
  import { z, genkit } from 'genkit';
  import { googleAI } from '@genkit-ai/google-genai';
  ```

- **Incorrect (Pre-1.0)**: Importing from `@genkit-ai/ai`, `@genkit-ai/core`, or `@genkit-ai/flow`. These packages are internal/deprecated for direct use.
  ```ts
  import { genkit } from "@genkit-ai/core"; // INCORRECT
  import { defineFlow } from "@genkit-ai/flow"; // INCORRECT
  ```

### Model References

- **Correct**: Use plugin-specific model factories or string identifiers (prefaced by plugin name).
  ```ts
  // Using model factory (v1.x - Preferred)
  await ai.generate({ model: googleAI.model('gemini-2.5-flash'), ... });

  // Using string identifier
  await ai.generate({ model: 'googleai/gemini-2.5-flash', ...});
  // Or
  await ai.generate({ model: 'vertexai/gemini-2.5-flash', ...});
  ```
- **Incorrect**: Using imported model objects directly or string identifiers without plugin name.
  ```ts
  await ai.generate({ model: gemini15Pro, ... }); // INCORRECT (Pre-1.0)
  await ai.generate({ model: 'gemini-2.5-flash', ... }); // INCORRECT (No plugin prefix)
  ```

### Model Selection (Gemini)

- **Preferred**: Use `gemini-2.5-*` models for best performance and features.
  ```ts
  model: googleAI.model('gemini-2.5-flash') // PREFERRED
  ```
- **DEPRECATED**: `gemini-1.5-*` models are deprecated and will throw errors.
  ```ts
  model: googleAI.model('gemini-1.5-flash') // ERROR (Deprecated)
  ```

### Response Access

- **Correct (v1.x)**: Access properties directly.
  ```ts
  response.text; // CORRECT
  response.output; // CORRECT
  ```
- **Incorrect (Pre-1.0)**: Calling as methods.
  ```ts
  response.text(); // INCORRECT
  response.output(); // INCORRECT
  ```

### Streaming Generation

- **Correct (v1.x)**: Do NOT await `generateStream`. Iterate over `stream` directly. Await `response` property for final result.
  ```ts
  const {stream, response} = ai.generateStream(...); // NO await here
  for await (const chunk of stream) { ... }          // Iterate stream
  const finalResponse = await response;              // Await response property
  ```
- **Incorrect (Pre-1.0)**: Calling stream as a function or awaiting the generator incorrectly.
  ```ts
  for await (const chunk of stream()) { ... } // INCORRECT
  await response();                           // INCORRECT
  ```

### Initialization

- **Correct (v1.x)**: Instantiate `genkit`.
  ```ts
  const ai = genkit({ plugins: [...] });
  ```
- **Incorrect (Pre-1.0)**: Global configuration.
  ```ts
  configureGenkit({ plugins: [...] }); // INCORRECT
  ```

### Flow Definitions

- **Correct (v1.x)**: Define flows on the `ai` instance.
  ```ts
  ai.defineFlow({...}, (input) => {...});
  ```
- **Incorrect (Pre-1.0)**: Importing `defineFlow` globally.
  ```ts
  import { defineFlow } from "@genkit-ai/flow"; // INCORRECT

You should never import `@genkit-ai/flow`, `@genkit-ai/ai` or `@genkit-ai/core` packages directly.

## Zod & Schema Errors

- **Import Source**: ALWAYS use `import { z } from "genkit"`.
  - Using `zod` directly from `zod` package may cause instance mismatches or compatibility issues.
- **Supported Types**: Stick to basic types: scalar (`string`, `number`, `boolean`), `object`, and `array`.
  - Avoid complex Zod features unless strictly necessary and verified.
- **Descriptions**: Always use `.describe('...')` for fields in output schemas to guide the LLM.

## Tool Usage

- **Tool Not Found**: Ensure tools are registered in the `tools` array of `generate` or provided via plugins.
- **MCP Tools**: Use the `ServerName:tool_name` format when referencing MCP tools.

## Multimodal & Image Generation

- **Missing responseModalities**: When using image generation models (like `gemini-2.5-flash-image`), you **MUST** specify the response modalities in the config.
  ```ts
  config: {
    responseModalities: ["TEXT", "IMAGE"]
  }
  ```
  Failure to do so will result in errors or incorrect output format.

## Audio & Speech Generation

- **Raw PCM Data vs MP3**: Some providers (e.g., Google GenAI) return raw PCM data, while others (e.g., OpenAI) return MP3.
  - **DO NOT assume MP3 format.**
  - **DO NOT embed raw PCM in HTML audio tags.**
  - **Action**: Run `genkit docs:search "speech audio"` to find provider-specific conversion steps (e.g., PCM to WAV).
