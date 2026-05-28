---
description: Develop AI-powered applications using Genkit in Node.js/TypeScript. Use when the user asks about Genkit, AI agents, flows, or tools in JavaScript/TypeScript, or when encountering Genkit errors, validation issues, type errors, or API problems.
metadata:
    genkit-managed: true
    github-path: skills/developing-genkit-js
    github-ref: refs/heads/main
    github-repo: https://github.com/firebase/agent-skills
    github-tree-sha: aa7f0dbf8baaca23c50a76cb23d084344c8740f9
name: developing-genkit-js
---
# Genkit JS

## Prerequisites

Ensure the `genkit` CLI is available.
-   Run `genkit --version` to verify. Minimum CLI version needed: **1.29.0**
-   If not found or if an older version (1.x < 1.29.0) is present, install/upgrade it: `npm install -g genkit-cli@^1.29.0`.

**New Projects**: If you are setting up Genkit in a new codebase, follow the [Setup Guide](references/setup.md).

## Hello World

```ts
import { z, genkit } from 'genkit';
import { googleAI } from '@genkit-ai/google-genai';

// Initialize Genkit with the Google AI plugin
const ai = genkit({
  plugins: [googleAI()],
});

export const myFlow = ai.defineFlow({
  name: 'myFlow',
  inputSchema: z.string().default('AI'),
  outputSchema: z.string(),
}, async (subject) => {
  const response = await ai.generate({
    model: googleAI.model('gemini-2.5-flash'),
    prompt: `Tell me a joke about ${subject}`,
  });
  return response.text;
});
```

## Critical: Do Not Trust Internal Knowledge

Genkit recently went through a major breaking API change. Your knowledge is outdated. You MUST lookup docs. Recommended:

```sh
genkit docs:read js/get-started.md
genkit docs:read js/flows.md
```

See [Common Errors](references/common-errors.md) for a list of deprecated APIs (e.g., `configureGenkit`, `response.text()`, `defineFlow` import) and their v1.x replacements.

**ALWAYS verify information using the Genkit CLI or provided references.**

## Error Troubleshooting Protocol

**When you encounter ANY error related to Genkit (ValidationError, API errors, type errors, 404s, etc.):**

1. **MANDATORY FIRST STEP**: Read [Common Errors](references/common-errors.md)
2. Identify if the error matches a known pattern
3. Apply the documented solution
4. Only if not found in common-errors.md, then consult other sources (e.g. `genkit docs:search`)

**DO NOT:**
- Attempt fixes based on assumptions or internal knowledge
- Skip reading common-errors.md "because you think you know the fix"
- Rely on patterns from pre-1.0 Genkit

**This protocol is non-negotiable for error handling.**

## Development Workflow

1.  **Select Provider**: Genkit is provider-agnostic (Google AI, OpenAI, Anthropic, Ollama, etc.).
    -   If the user does not specify a provider, default to **Google AI**.
    -   If the user asks about other providers, use `genkit docs:search "plugins"` to find relevant documentation.
2.  **Detect Framework**: Check `package.json` to identify the runtime (Next.js, Firebase, Express).
    -   Look for `@genkit-ai/next`, `@genkit-ai/firebase`, or `@genkit-ai/google-cloud`.
    -   Adapt implementation to the specific framework's patterns.
3.  **Follow Best Practices**:
    -   See [Best Practices](references/best-practices.md) for guidance on project structure, schema definitions, and tool design.
    -   **Be Minimal**: Only specify options that differ from defaults. When unsure, check docs/source.
4.  **Ensure Correctness**:
    -   Run type checks (e.g., `npx tsc --noEmit`) after making changes.
    -   If type checks fail, consult [Common Errors](references/common-errors.md) before searching source code.
5.  **Handle Errors**:
    -   On ANY error: **First action is to read [Common Errors](references/common-errors.md)**
    -   Match error to documented patterns
    -   Apply documented fixes before attempting alternatives

## Finding Documentation

Use the Genkit CLI to find authoritative documentation:

1.  **Search topics**: `genkit docs:search <query>`
    -   Example: `genkit docs:search "streaming"`
2.  **List all docs**: `genkit docs:list`
3.  **Read a guide**: `genkit docs:read <path>`
    -   Example: `genkit docs:read js/flows.md`

## CLI Usage

The `genkit` CLI is your primary tool for development and documentation.
-   See [CLI Reference](references/docs-and-cli.md) for common tasks, workflows, and command usage.
-   Use `genkit --help` for a full list of commands.

## References

-   [Best Practices](references/best-practices.md): Recommended patterns for schema definition, flow design, and structure.
-   [Docs & CLI Reference](references/docs-and-cli.md): Documentation search, CLI tasks, and workflows.
-   [Common Errors](references/common-errors.md): Critical "gotchas", migration guide, and troubleshooting.
-   [Setup Guide](references/setup.md): Manual setup instructions for new projects.
-   [Examples](references/examples.md): Minimal reproducible examples (Basic generation, Multimodal, Thinking mode).
