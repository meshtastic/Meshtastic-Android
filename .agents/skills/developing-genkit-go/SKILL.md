---
description: Develop AI-powered applications using Genkit in Go. Use when the user asks to build AI features, agents, flows, or tools in Go using Genkit, or when working with Genkit Go code involving generation, prompts, streaming, tool calling, or model providers.
metadata:
    genkit-managed: true
    github-path: skills/developing-genkit-go
    github-ref: refs/heads/main
    github-repo: https://github.com/firebase/agent-skills
    github-tree-sha: 551cc4211a9782a7237da5e762ea8ca7c3bf5eec
name: developing-genkit-go
---
# Genkit Go

Genkit Go is an AI SDK for Go that provides generation, structured output, streaming, tool calling, prompts, and flows with a unified interface across model providers.

## Hello World

```go
package main

import (
	"context"
	"fmt"
	"log"
	"net/http"

	"github.com/genkit-ai/genkit/go/ai"
	"github.com/genkit-ai/genkit/go/genkit"
	"github.com/genkit-ai/genkit/go/plugins/googlegenai"
	"github.com/genkit-ai/genkit/go/plugins/server"
)

func main() {
	ctx := context.Background()
	g := genkit.Init(ctx, genkit.WithPlugins(&googlegenai.GoogleAI{}))

	genkit.DefineFlow(g, "jokeFlow", func(ctx context.Context, topic string) (string, error) {
		return genkit.GenerateText(ctx, g,
			ai.WithModelName("googleai/gemini-flash-latest"),
			ai.WithPrompt("Tell me a joke about %s", topic),
		)
	})

	mux := http.NewServeMux()
	for _, f := range genkit.ListFlows(g) {
		mux.HandleFunc("POST /"+f.Name(), genkit.Handler(f))
	}
	log.Fatal(server.Start(ctx, "127.0.0.1:8080", mux))
}
```

## Core Features

Load the appropriate reference based on what you need:

| Feature | Reference | When to load |
| --- | --- | --- |
| Initialization | [references/getting-started.md](references/getting-started.md) | Setting up `genkit.Init`, plugins, the `*Genkit` instance pattern |
| Generation | [references/generation.md](references/generation.md) | `Generate`, `GenerateText`, `GenerateData`, streaming, output formats |
| Prompts | [references/prompts.md](references/prompts.md) | `DefinePrompt`, `DefineDataPrompt`, `.prompt` files, schemas |
| Tools | [references/tools.md](references/tools.md) | `DefineTool`, tool interrupts, `RestartWith`/`RespondWith` |
| Middleware | [references/middleware.md](references/middleware.md) | `ai.Middleware`, `ai.WithUse`, `Hooks` (Generate/Model/Tool), built-ins (`Retry`, `Fallback`, `ToolApproval`, `Filesystem`, `Skills`) |
| Flows & HTTP | [references/flows-and-http.md](references/flows-and-http.md) | `DefineFlow`, `DefineStreamingFlow`, `genkit.Handler`, HTTP serving |
| Model Providers | [references/providers.md](references/providers.md) | Google AI, Vertex AI, Anthropic, OpenAI-compatible, Ollama setup |

## Genkit CLI

Check if installed: `genkit --version`

**Installation:**
```bash
curl -sL cli.genkit.dev | bash
```

**Key commands:**

```bash
# Start app with Developer UI (tracing, flow testing) at http://localhost:4000
genkit start -- go run .
genkit start -o -- go run .   # also opens browser

# Run a flow directly from the CLI
genkit flow:run myFlow '{"data": "input"}'
genkit flow:run myFlow '{"data": "input"}' --stream   # with streaming
genkit flow:run myFlow '{"data": "input"}' --wait      # wait for completion

# Look up Genkit documentation
genkit docs:search "streaming" go
genkit docs:list go
genkit docs:read go/flows.md
```

See [references/getting-started.md](references/getting-started.md) for full CLI and Developer UI details.

## Key Guidance

- **Pass `g` explicitly.** The `*Genkit` instance returned by `genkit.Init` is the central registry. Pass it to all Genkit functions rather than storing it as a global. This is a core pattern throughout the SDK.
- **Wrap AI logic in flows.** Flows give you tracing, observability, HTTP deployment via `genkit.Handler`, and the ability to test from the Developer UI and CLI. Any generation call worth keeping should live in a flow.
- **Use `jsonschema:"description=..."` struct tags on output types.** The model uses these descriptions to understand what each field should contain. Without them, structured output quality drops significantly.
- **Write good tool descriptions.** The model decides which tools to call based on their description string. Vague descriptions lead to missed or incorrect tool calls.
- **Use `.prompt` files for complex prompts.** They separate prompt content from Go code, support Handlebars templating, and can be iterated on without recompilation. Code-defined prompts are better for simple, single-line cases.
- **Reach for built-in middleware before writing one.** `Retry`, `Fallback`, `ToolApproval`, `Filesystem`, and `Skills` cover the common cross-cutting needs and compose with each other via `ai.WithUse`. See [references/middleware.md](references/middleware.md). When you do write custom middleware, allocate per-call state in closures captured by `New`, and guard anything that `WrapTool` mutates because tools may run concurrently.
- **Look up the latest model IDs.** Model names change frequently. Check provider documentation for current model IDs rather than relying on hardcoded names. See [references/providers.md](references/providers.md).
