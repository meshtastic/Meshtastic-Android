# FastAPI — Genkit Python

## Install

```bash
uv add genkit-plugin-fastapi fastapi uvicorn
```

---

## Streaming by default

The `genkit_fastapi_handler` decorator auto-streams when the client sends `Accept: text/event-stream`.
No extra setup — just add the header on the frontend and it works.

**Wire format (SSE):**
```
data: {"message": "<chunk text>"}   ← one per ctx.send_chunk() call
data: {"message": "<chunk text>"}
data: {"result": <final output>}    ← sent once when flow completes
```

**Frontend (JS EventSource):**
```js
const res = await fetch('/flow/chat', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', 'Accept': 'text/event-stream' },
  body: JSON.stringify({ data: { topic: 'quantum computing' } }),
});
const reader = res.body.getReader();
// decode and parse each `data: {...}` line
```

**curl test:**
```bash
curl -N -X POST http://localhost:8080/flow/chat \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{"data": {"topic": "quantum computing"}}'
```

---

## Minimal streaming FastAPI app

```python
import uvicorn
from pydantic import BaseModel
from fastapi import FastAPI
from genkit import Genkit
from genkit import ActionRunContext
from genkit.plugins.fastapi import genkit_fastapi_handler
from genkit.plugins.google_genai import GoogleAI

ai = Genkit(plugins=[GoogleAI()], model='googleai/gemini-flash-latest')
app = FastAPI()

class ChatInput(BaseModel):
    topic: str

@app.post('/flow/chat', response_model=None)
@genkit_fastapi_handler(ai)
@ai.flow()
async def chat(input: ChatInput, ctx: ActionRunContext) -> str:
    sr = ai.generate_stream(prompt=f'Tell me about {input.topic}.')
    full = ''
    async for chunk in sr.stream:
        if chunk.text:
            ctx.send_chunk(chunk.text)   # each chunk → SSE event on the wire
            full += chunk.text
    return full

if __name__ == '__main__':
    uvicorn.run(app, host='0.0.0.0', port=8080)
```

**Key:** flow must accept `ctx: ActionRunContext` and call `ctx.send_chunk(text)` to emit SSE chunks.
Without `ctx.send_chunk`, the flow runs but streams nothing — client waits for the final result.

---

## Advanced Use Cases

### Fine-grained control over flow streaming

Complex apps chain flows — a parent orchestrates children. Chunks propagate upward by **passing `ctx` to child flows**.

```python
class ResearchInput(BaseModel):
    topic: str

@ai.flow()
async def research(input: ResearchInput, ctx: ActionRunContext) -> str:
    """Child flow — streams its generate_stream chunks to whoever called it."""
    sr = ai.generate_stream(prompt=f'Explain {input.topic} in depth.')
    full = ''
    async for chunk in sr.stream:
        if chunk.text:
            ctx.send_chunk(chunk.text)   # propagates up through the call stack
            full += chunk.text
    return full


class HeadlineInput(BaseModel):
    text: str

@ai.flow()
async def make_headline(input: HeadlineInput) -> str:
    """Child flow — non-streaming, returns instantly."""
    response = await ai.generate(prompt=f'One-line headline for: {input.text}')
    return response.text.strip()


class ReportInput(BaseModel):
    topic: str

@app.post('/flow/report', response_model=None)
@genkit_fastapi_handler(ai)
@ai.flow()
async def report(input: ReportInput, ctx: ActionRunContext) -> str:
    """Parent flow — calls children, composes a streaming report."""
    # Step 1: fast non-streaming call
    headline = await make_headline(HeadlineInput(text=input.topic))
    ctx.send_chunk(f'# {headline}\n\n')           # send headline immediately

    # Step 2: child flow streams its chunks — passes ctx so they flow up
    body = await research(ResearchInput(topic=input.topic), ctx)

    return f'# {headline}\n\n{body}'
```

**Rules for nested streaming:**
- Child flows that should stream must also accept `ctx: ActionRunContext`
- Pass the parent's `ctx` when calling child flows: `await child(input, ctx)`
- Non-streaming child flows don't need `ctx` — just `await` them normally
- A child that doesn't call `ctx.send_chunk` contributes nothing to the stream (fine for parallel data fetching)

### Executing flows in parallel

Use `asyncio.gather` to run multiple flows concurrently. Only makes sense when children don't need to stream.

```python
import asyncio

class AnalysisInput(BaseModel):
    text: str

class CheckResult(BaseModel):
    issues: list[str]

class CombinedAnalysis(BaseModel):
    issues: list[str]

@ai.flow()
async def check_security(input: AnalysisInput) -> CheckResult:
    # Here the model reviews the text; replace with your real prompt/schema as needed.
    r = await ai.generate(
        prompt=f'List security concerns as a short comma-separated line (or "none"): {input.text[:2000]}',
    )
    raw = (r.text or '').strip()
    issues = [s.strip() for s in raw.split(',') if s.strip() and s.strip().lower() != 'none']
    return CheckResult(issues=issues)

@ai.flow()
async def check_bugs(input: AnalysisInput) -> CheckResult:
    # Model lists possible bugs; tune prompt for your codebase.
    r = await ai.generate(
        prompt=f'List likely bugs or correctness issues as a short comma-separated line (or "none"): {input.text[:2000]}',
    )
    raw = (r.text or '').strip()
    issues = [s.strip() for s in raw.split(',') if s.strip() and s.strip().lower() != 'none']
    return CheckResult(issues=issues)

@ai.flow()
async def check_style(input: AnalysisInput) -> CheckResult:
    # Model suggests style/clarity issues; optional: use output_schema for structured rows.
    r = await ai.generate(
        prompt=f'List style or clarity issues as a short comma-separated line (or "none"): {input.text[:2000]}',
    )
    raw = (r.text or '').strip()
    issues = [s.strip() for s in raw.split(',') if s.strip() and s.strip().lower() != 'none']
    return CheckResult(issues=issues)

@app.post('/flow/analyze', response_model=None)
@genkit_fastapi_handler(ai)
@ai.flow()
async def analyze(input: AnalysisInput) -> CombinedAnalysis:
    security, bugs, style = await asyncio.gather(
        check_security(input),
        check_bugs(input),
        check_style(input),
    )
    return CombinedAnalysis(issues=security.issues + bugs.issues + style.issues)
```

---

## Structured output endpoint (non-streaming)

```python
class SentimentResult(BaseModel):
    sentiment: str        # positive / negative / neutral
    confidence: float     # 0.0–1.0
    key_phrases: list[str]

@app.post('/flow/sentiment', response_model=None)
@genkit_fastapi_handler(ai)
@ai.flow()
async def sentiment(input: AnalysisInput) -> SentimentResult:
    response = await ai.generate(
        prompt=f'Analyze sentiment: {input.text}',
        output_format='json',
        output_schema=SentimentResult,
    )
    return response.output
```

Client calls this without `Accept: text/event-stream` — gets `{"result": {...}}` back.

---

## Decorator order

Must be exactly: `@app.post` → `@genkit_fastapi_handler(ai)` → `@ai.flow()`

```python
@app.post('/flow/chat', response_model=None)   # 1. FastAPI route
@genkit_fastapi_handler(ai)                    # 2. Genkit wire format + streaming
@ai.flow()                                     # 3. Flow registration
async def chat(input: ChatInput, ctx: ActionRunContext) -> str:
    ...
```

---

## Run with Dev UI

```bash
GEMINI_API_KEY=your-key genkit start -- uv run src/main.py
```

Leave the process running until the CLI prints something like:

```
Genkit Developer UI: http://localhost:4000
```

Open that URL. Port may differ if 4000 is busy.