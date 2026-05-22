# Genkit Python Examples

Minimal patterns for common Genkit APIs. Examples use **Google AI** (`GoogleAI`, `googleai/...`); other providers use the same patterns with the right plugin and model prefix.

## Public imports

Use **`genkit`**, **`genkit.plugins.*`**, **`genkit.embedder`**, **`genkit.evaluator`**, and **`genkit.model`** (and similar public modules) only — not internal packages (`genkit._core`, etc.).

```python
from genkit import Genkit, ActionRunContext
from genkit.plugins.google_genai import GoogleAI

ai = Genkit(plugins=[GoogleAI()], model='googleai/gemini-flash-latest')
```

---

## Structured output

```python
from pydantic import BaseModel, TypeAdapter

class CityInfo(BaseModel):
    name: str
    population: int
    country: str

response = await ai.generate(
    prompt='Give facts about Tokyo.',
    output_format='json',
    output_schema=CityInfo,
)
city = response.output

# Arrays
schema = TypeAdapter(list[CityInfo]).json_schema()
response = await ai.generate(
    prompt='List 3 cities.',
    output_format='array',
    output_schema=schema,
)
```

Output formats: `'text'`, `'json'`, `'array'`, `'enum'`, `'jsonl'`.

---

## Streaming (text)

```python
sr = ai.generate_stream(prompt='Tell me a story.')
async for chunk in sr.stream:
    if chunk.text:
        print(chunk.text, end='', flush=True)
final = await sr.response  # final.text
```

---

## Text and media parts

```python
# Non-streaming
response = await ai.generate(prompt='...')
for media in response.media:
    print(media.content_type, (media.url or '')[:80])

# Streaming — media usually complete on the final response
from genkit import MediaPart

sr = ai.generate_stream(prompt='...')
async for chunk in sr.stream:
    if chunk.text:
        print(chunk.text, end='', flush=True)
final = await sr.response
for media in final.media:
    print(media.content_type, (media.url or '')[:80])

if final.message:
    for part in final.message.content:
        if isinstance(part.root, MediaPart) and part.root.media:
            print(part.root.media.content_type)
```

---

## Streaming + structured output

```python
class StoryAnalysis(BaseModel):
    title: str
    genre: str
    summary: str

sr = ai.generate_stream(
    prompt='Write a short story then analyze it.',
    output_format='json',
    output_schema=StoryAnalysis,
)
async for chunk in sr.stream:
    if chunk.text:
        print(chunk.text, end='', flush=True)
final = await sr.response
analysis = final.output
```

---

## Flows

```python
class SummarizeInput(BaseModel):
    text: str

@ai.flow()
async def summarize(input: SummarizeInput) -> str:
    response = await ai.generate(prompt=f'Summarize: {input.text}')
    return response.text
```

---

## Streaming flows

```python
@ai.flow()
async def stream_story(subject: str, ctx: ActionRunContext) -> str:
    sr = ai.generate_stream(prompt=f'Story about {subject}.')
    full = ''
    async for chunk in sr.stream:
        if chunk.text:
            ctx.send_chunk(chunk.text)
            full += chunk.text
    return full
```

---

## Tools

Parameters must be a **Pydantic `BaseModel`** (bare scalars → 400 from Gemini). Use **`@ai.tool()`**, not `@ai.define_tool()`.

```python
class WeatherInput(BaseModel):
    city: str

@ai.tool()
async def get_weather(input: WeatherInput) -> str:
    return f'Sunny in {input.city}'

response = await ai.generate(prompt='Weather in Paris?', tools=[get_weather])
```

---

## Embeddings

```python
from genkit.plugins.google_genai import GeminiEmbeddingModels

embedder = f'googleai/{GeminiEmbeddingModels.GEMINI_EMBEDDING_001}'
embeddings = await ai.embed(embedder=embedder, content='The sky is blue.')
vector = embeddings[0].embedding

embeddings = await ai.embed_many(
    embedder=embedder,
    content=['The sky is blue.', 'Grass is green.'],
)
```

Common embedders: `googleai/gemini-embedding-001`, `googleai/gemini-embedding-exp-03-07`.
