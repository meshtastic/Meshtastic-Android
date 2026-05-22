---
description: Develop AI-powered applications using Genkit in Python. Use when the user asks about Genkit, AI agents, flows, or tools in Python, or when encountering Genkit errors, import issues, or API problems.
metadata:
    genkit-managed: true
    github-path: skills/developing-genkit-python
    github-ref: refs/heads/main
    github-repo: https://github.com/firebase/agent-skills
    github-tree-sha: e1043041631a680b0f3f12b1c5718d71706e2c15
name: developing-genkit-python
---
# Genkit Python

## Prerequisites

- **Runtime**: Python **3.14+**, **`uv`** for deps ([install](https://docs.astral.sh/uv/getting-started/installation/)).
- **CLI**: `genkit --version` — install via `npm install -g genkit-cli` if missing.

**New projects:** [Setup](references/setup.md) (bootstrap + env). **Patterns and code samples:** [Examples](references/examples.md).

## Hello World

```python
from genkit import Genkit
from genkit.plugins.google_genai import GoogleAI

ai = Genkit(
    plugins=[GoogleAI()],
    model='googleai/gemini-flash-latest',
)

async def main():
    response = await ai.generate(prompt='Tell me a joke about Python.')
    print(response.text)

if __name__ == '__main__':
    ai.run_main(main())
```

## Critical: Do Not Trust Internal Knowledge

The Python SDK changes often — verify imports and APIs against the references here or upstream docs. On **any** error, read [Common Errors](references/common-errors.md) first.

## Development Workflow

1. Default provider: **Google AI** (`GoogleAI()`), **`GEMINI_API_KEY`** in the environment.
2. Model IDs: always prefixed, e.g. **`googleai/gemini-flash-latest`** (always-on-latest Flash alias; same pattern as other skills).
3. Entrypoint: **`ai.run_main(main())`** for Genkit-driven apps (not `asyncio.run()` for long-lived servers started with `genkit start` — see [Common Errors](references/common-errors.md)).
4. After generating code, follow [Dev Workflow](references/dev-workflow.md) for `genkit start` and the Dev UI.
5. On errors: step 1 is always [Common Errors](references/common-errors.md).

## References

- [Examples](references/examples.md): Structured output, streaming, flows, tools, embeddings.
- [Setup](references/setup.md): New project bootstrap and plugins.
- [Common Errors](references/common-errors.md): Read first when something breaks.
- [FastAPI](references/fastapi.md): HTTP, `genkit_fastapi_handler`, parallel flows.
- [Dotprompt](references/dotprompt.md): `.prompt` files and helpers.
- [Evals](references/evals.md): Evaluators and datasets.
- [Dev Workflow](references/dev-workflow.md): `genkit start`, Dev UI, checklist.
