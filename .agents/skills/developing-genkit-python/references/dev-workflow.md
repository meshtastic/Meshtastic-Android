# Dev Workflow — Genkit Python

## Agent responsibility

After generating code, always give the developer:
1. The full pre-run checklist with copy-paste commands using absolute paths
2. The `genkit start` command to run in their terminal (foreground — it's expected to block)
3. Step-by-step Dev UI instructions so they can test without guessing

Do not offer to run it for them. Give them the commands and let them run it.

---

## Step 1 — Get a Gemini API key

If the developer doesn't have one:
> Get a free key at https://aistudio.google.com/apikey — click **"Create API key"**, copy it.

---

## Step 2 — Set the API key

Open a terminal and run:
```bash
export GEMINI_API_KEY=your-api-key-here
```

To persist across sessions, add it to your shell profile:
```bash
echo 'export GEMINI_API_KEY=your-api-key-here' >> ~/.zshrc && source ~/.zshrc
```

---

## Step 3 — Install dependencies

Replace `/path/to/your-project` with the actual full path to the project (e.g. `/Users/yourname/projects/my-genkit-app`):

```bash
cd /path/to/your-project
uv add genkit genkit-plugin-google-genai
```

(Requires a project with `pyproject.toml` — run `uv init` in an empty directory first if needed.)

---

## Step 4 — Start the Dev UI

Run this in your terminal. **It will block — that's expected.** Leave this terminal open while you use the Dev UI.

```bash
cd /path/to/your-project
GEMINI_API_KEY=your-api-key-here genkit start -- uv run src/main.py
```

You'll see output like:
```
Genkit Tools UI: http://localhost:4000
```

The Dev UI is now running at **http://localhost:4000**

To stop it: press `Ctrl+C` in the terminal.

---

## Step 5 — Test in the Dev UI

1. Open **http://localhost:4000** in your browser
2. Click **"Run"** in the left sidebar
3. Find your flow by name (e.g. `summarize`, `chat`, `joke_generator`)
4. In the input box, paste your input as JSON — e.g:
   ```json
   {"text": "hello world"}
   ```
5. Click the **"Run"** button — the output appears on the right
6. Click **"Traces"** in the left sidebar to inspect every step, model call, token count, and latency

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `genkit: command not found` | Run: `npm install -g genkit-cli` |
| `GEMINI_API_KEY not set` | Run: `export GEMINI_API_KEY=your-key` |
| Port 4000 already in use | Use: `genkit start --port 4001 -- uv run src/main.py` |
| `uv: command not found` | Run: `curl -LsSf https://astral.sh/uv/install.sh \| sh` |
| Flow not showing in Dev UI | Make sure `genkit start` output shows no errors |
