# Evals — Genkit Python

## Two types of evaluators

1. **Built-in** — ship with `genkit-plugin-evaluators`, register with `register_genkit_evaluators(ai)`
2. **BYO (LLM-based)** — define your own scoring logic with `ai.define_evaluator()`

## Install

```bash
uv add genkit-plugin-evaluators
```

## Dataset format

A JSON file, one object per test case:
```json
[
  {"testCaseId": "case1", "input": "x", "output": "banana", "reference": "ba?a?a"},
  {"testCaseId": "case2", "input": "x", "output": "apple",  "reference": "ba?a?a"}
]
```

Fields: `testCaseId`, `input`, `output`, `reference` (reference optional for some evaluators).

## Built-in evaluators

```python
from genkit.plugins.evaluators import register_genkit_evaluators
register_genkit_evaluators(ai)
```

Registered evaluators include `genkitEval/regex`. Run via CLI:
```bash
genkit eval:run datasets/my_dataset.json --evaluators=genkitEval/regex
```

## BYO evaluator

```python
from genkit.evaluator import BaseDataPoint, Details, EvalFnResponse, EvalStatusEnum, Score

async def my_eval(datapoint: BaseDataPoint, _options: dict | None = None) -> EvalFnResponse:
    """Score output against reference."""
    output = str(datapoint.output or '')
    reference = str(datapoint.reference or '')
    passed = output.strip() == reference.strip()
    return EvalFnResponse(
        test_case_id=datapoint.test_case_id or '',
        evaluation=Score(
            score=1.0 if passed else 0.0,
            status=EvalStatusEnum.PASS if passed else EvalStatusEnum.FAIL,
            details=Details(reasoning='Exact match check'),
        ),
    )

ai.define_evaluator(
    name='byo/my_eval',
    display_name='My Eval',
    definition='Checks exact match of output vs reference.',
    fn=my_eval,
)
```

## LLM-based evaluator (judge model pattern)

Use a prompt + stronger model to score. See `py/samples/evaluators/src/main.py` for full examples (`byo/maliciousness`, `byo/answer_accuracy`).

Core pattern:
```python
async def llm_eval(datapoint: BaseDataPoint, _options: dict | None = None) -> EvalFnResponse:
    prompt = ai.prompt('my_judge_prompt')
    rendered = await prompt.render(input={'output': str(datapoint.output), 'reference': str(datapoint.reference)})
    response = await ai.generate(model='googleai/gemini-flash-latest', messages=rendered.messages)
    score = float(response.text.strip())
    return EvalFnResponse(
        test_case_id=datapoint.test_case_id or '',
        evaluation=Score(score=score, status=EvalStatusEnum.PASS if score >= 0.5 else EvalStatusEnum.FAIL),
    )
```

## Run evals via CLI

```bash
genkit eval:run datasets/my_dataset.json --evaluators=byo/my_eval
genkit eval:run datasets/my_dataset.json --evaluators=genkitEval/regex,byo/my_eval
```

Results appear in the Dev UI under **Evaluate** (http://localhost:4000).
