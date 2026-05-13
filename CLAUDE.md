# Meshtastic Android - Claude Code Guide

@AGENTS.md

## Claude-Specific Instructions

- **Think First:** Always outline your step-by-step reasoning inside `<thinking>` tags before writing code or shell commands. Claude models perform significantly better on complex KMP tasks when they "think out loud" first.
- **Skills:** The `.skills/` directory contains task-specific instruction modules. Load them as needed — only the skill relevant to your current task.
- **Plan Mode:** Use plan mode for architectural changes spanning multiple modules. Write plans to `.agent_plans/` (git-ignored). The Copilot-CLI-specific `/plan`, `/delegate`, `/research`, and `/share` guidance in `AGENTS.md` does not apply to Claude Code — skip the `<copilot_cli_workflow>` section.
