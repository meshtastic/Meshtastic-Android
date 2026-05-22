# Genkit Documentation & CLI

This reference lists common tasks and workflows using the `genkit` CLI. For authoritative command details, always run `genkit --help` or `genkit <command> --help`.

## Prerequisites:

Ensure that the CLI is on `genkit-cli` version >= 1.29.0. If not, or if an older version (1.x < 1.29.0) is present, update the Genkit CLI version. Alternatively, to run commands with a specific version or without global installation, prefix them with `npx -y genkit-cli@^1.29.0`.

## Documentation

-   **Search docs**: `genkit docs:search <query>`
    -   Example: `genkit docs:search "streaming"`
    -   Example: `genkit docs:search "rag retrieval"`
-   **Read doc**: `genkit docs:read <path>`
    -   Example: `genkit docs:read js/overview.md`
-   **List docs**: `genkit docs:list`

## Development Workflow

-   **Start Dev Mode**: `genkit start -- <command>`
    -   Runs the provided command in Genkit dev mode, enabling the Developer UI (usually at http://localhost:4000).
    -   **Node.js (TypeScript)**:
        ```bash
        genkit start -- npx tsx --watch src/index.ts
        ```
    -   **Next.js**:
        ```bash
        genkit start -- npx next dev
        ```

## Flow Execution

-   **Run a flow**: `genkit flow:run <flowName> '<inputJSON>'`
    -   Executes a flow directly from the CLI. Useful for testing.
    -   **Simple Input**:
        ```bash
        genkit flow:run tellJoke '"chicken"'
        ```
    -   **Object Input**:
        ```bash
        genkit flow:run generateStory '{"subject": "robot", "genre": "sci-fi"}'
        ```

## Evaluation

-   **Evaluate a flow**: `genkit eval:flow <flowName> [data]`
    -   Runs a flow and evaluates the output against configured evaluators.
    -   **Example (Single Input)**:
        ```bash
        genkit eval:flow answerQuestion '[{"testCaseId": "1", "input": {"question": "What is Genkit?"}}]'
        ```
    -   **Example (Batch Input)**:
        ```bash
        genkit eval:flow answerQuestion --input inputs.json
        ```

-   **Run Evaluation**: `genkit eval:run <dataset>`
    -   Evaluates a dataset against configured evaluators.
    -   **Example**:
        ```bash
        genkit eval:run dataset.json --output results.json
        ```