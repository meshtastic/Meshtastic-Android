# Dotprompt — Genkit Python

## What it is

`.prompt` files combine YAML frontmatter (model config, schemas) with Handlebars templates. Keeps prompt logic out of Python code and makes variants easy.

## File format

```yaml
---
model: googleai/gemini-flash-latest
input:
  schema:
    food: string
    ingredients?(array): string   # ? = optional
output:
  schema: Recipe    # references a schema registered with ai.define_schema()
  format: json
---
You are a chef. Generate a recipe for {{food}}.

{{#if ingredients}}
Include these ingredients:
{{list ingredients}}
{{/if}}
```

Place `.prompt` files in a `prompts/` directory and point `prompt_dir` at it.

## Python setup

```python
from pathlib import Path
from pydantic import BaseModel
from genkit import Genkit
from genkit.plugins.google_genai import GoogleAI

ai = Genkit(
    plugins=[GoogleAI()],
    model='googleai/gemini-flash-latest',
    prompt_dir=Path(__file__).resolve().parent.parent / 'prompts',
)

# Register Pydantic models referenced in .prompt output.schema
class Recipe(BaseModel):
    title: str
    steps: list[str]

ai.define_schema('Recipe', Recipe)
```

## Calling a prompt

```python
# Non-streaming — double-call syntax: ai.prompt('name')(input={...})
response = await ai.prompt('recipe')(input={'food': 'banana bread'})
result = Recipe.model_validate(response.output)

# Variant (recipe.robot.prompt file)
response = await ai.prompt('recipe', variant='robot')(input={'food': 'banana bread'})
```

## Streaming from a prompt

```python
from genkit import ActionRunContext

@ai.flow()
async def tell_story(subject: str, ctx: ActionRunContext) -> str:
    result = ai.prompt('story').stream(input={'subject': subject})
    full = ''
    async for chunk in result.stream:
        if chunk.text:
            ctx.send_chunk(chunk.text)
            full += chunk.text
    return full
```

Note: `.stream(input={...})` not `ai.generate_stream(...)` — different call shape for prompts.

## Render without generating (for LLM-judge evals)

```python
rendered = await ai.prompt('my_prompt').render(input={'key': 'value'})
response = await ai.generate(model='googleai/gemini-flash-latest', messages=rendered.messages)
```

## Helpers

Register Python functions callable inside Handlebars templates:
```python
def list_helper(data: object, *args, **kwargs) -> str:
    if not isinstance(data, list):
        return ''
    return '\n'.join(f'- {item}' for item in data)

ai.define_helper('list', list_helper)
```

Then use `{{list ingredients}}` in your `.prompt` file.

## Variants

Name the file `<name>.<variant>.prompt` — e.g. `recipe.robot.prompt`.
Call with `ai.prompt('recipe', variant='robot')`.

## Partials

Use `{{>partial_name param=value}}` in templates. Partial files are named `_partial_name.prompt`.
