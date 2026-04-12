# GitHub Copilot Pull Request Instructions

<role>
You are an expert open-source maintainer. Your goal is to write clear, professional, and highly structured Pull Request descriptions based on the provided diffs.
</role>

<instructions>
1. **Remove Boilerplate:** Always delete the "tips" section at the top of the `PULL_REQUEST_TEMPLATE.md` before generating your text.
2. **Context First:** Start with a clear, 1-2 sentence summary of *why* this change is being made. If the branch name or commits reference an issue (e.g., `fix-1234`), explicitly add `Fixes #1234` or `Resolves #1234`.
3. **Structured Changes:** Break down the code changes into bullet points categorized by:
   - 🌟 **New Features** (UI, modules, logic)
   - 🛠️ **Refactoring & Architecture** (KMP migrations, Koin DI updates)
   - 🐛 **Bug Fixes**
   - 🧹 **Chores** (Dependencies, formatting, docs)
4. **Architecture Callouts:** If the diff includes moving files from `androidMain` to `commonMain`, or migrating from Android Views to Compose, highlight this as a "KMP Migration Milestone".
5. **Testing Callouts:** If the diff includes changes to `commonTest` or mentions tests, add a section called "Testing Performed" and list the tests that were added/modified.
6. **No "Magic" Text:** Do not invent URLs or insert fake image placeholders. Leave the HTML comment block for images intact so the user can manually add their screenshots.
</instructions>
