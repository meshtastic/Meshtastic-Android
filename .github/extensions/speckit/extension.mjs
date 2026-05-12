// Extension: speckit
// Spec Kit SDD workflow tools for Meshtastic Android

import { joinSession } from "@github/copilot-sdk/extension";
import { readdir, readFile, stat } from "node:fs/promises";
import { join, basename } from "node:path";

const SPECS_DIR = join(process.cwd(), "specs");
const SPECIFY_DIR = join(process.cwd(), ".specify");

async function dirExists(p) {
    try {
        return (await stat(p)).isDirectory();
    } catch {
        return false;
    }
}

async function fileExists(p) {
    try {
        return (await stat(p)).isFile();
    } catch {
        return false;
    }
}

async function discoverSpecs() {
    if (!(await dirExists(SPECS_DIR))) return [];
    const entries = await readdir(SPECS_DIR, { withFileTypes: true });
    const specs = [];
    for (const entry of entries) {
        if (!entry.isDirectory()) continue;
        const specDir = join(SPECS_DIR, entry.name);
        const specFile = join(specDir, "spec.md");
        if (!(await fileExists(specFile))) continue;

        const files = await readdir(specDir, { withFileTypes: true });
        const artifacts = [];
        for (const f of files) {
            if (f.isFile()) artifacts.push(f.name);
            if (f.isDirectory()) {
                const subFiles = await readdir(join(specDir, f.name));
                for (const sf of subFiles) artifacts.push(`${f.name}/${sf}`);
            }
        }

        const hasSpec = artifacts.includes("spec.md");
        const hasPlan = artifacts.includes("plan.md");
        const hasTasks = artifacts.includes("tasks.md");

        let title = entry.name;
        try {
            const content = await readFile(specFile, "utf-8");
            const match = content.match(/^#\s+(.+)/m);
            if (match) title = match[1];
        } catch { /* use dir name */ }

        let taskStats = null;
        if (hasTasks) {
            try {
                const tasksContent = await readFile(join(specDir, "tasks.md"), "utf-8");
                const taskLines = tasksContent.match(/^[-*]\s+\[[ x]\]/gm) || [];
                const done = tasksContent.match(/^[-*]\s+\[x\]/gmi) || [];
                taskStats = { total: taskLines.length, done: done.length };
            } catch { /* skip */ }
        }

        specs.push({
            id: entry.name,
            title,
            artifacts,
            hasSpec,
            hasPlan,
            hasTasks,
            taskStats,
            path: specDir,
        });
    }
    return specs.sort((a, b) => a.id.localeCompare(b.id));
}

const session = await joinSession({
    tools: [
        {
            name: "speckit_list",
            description:
                "List all feature specs in the specs/ directory with their artifacts and task progress. " +
                "Use this to discover which specs exist and their current state.",
            parameters: { type: "object", properties: {} },
            skipPermission: true,
            handler: async () => {
                const specs = await discoverSpecs();
                if (specs.length === 0) {
                    return "No specs found in specs/ directory. Use /speckit.specify to create one.";
                }

                const lines = ["# Feature Specs\n"];
                for (const s of specs) {
                    const status = [];
                    if (s.hasSpec) status.push("spec ✓");
                    if (s.hasPlan) status.push("plan ✓");
                    if (s.hasTasks) status.push("tasks ✓");

                    let progress = "";
                    if (s.taskStats) {
                        const pct = s.taskStats.total > 0
                            ? Math.round((s.taskStats.done / s.taskStats.total) * 100)
                            : 0;
                        progress = ` | ${s.taskStats.done}/${s.taskStats.total} tasks (${pct}%)`;
                    }

                    lines.push(`## ${s.id}`);
                    lines.push(`**${s.title}**`);
                    lines.push(`Artifacts: ${status.join(", ")}${progress}`);
                    lines.push(`Files: ${s.artifacts.join(", ")}`);
                    lines.push(`Path: ${s.path}\n`);
                }
                return lines.join("\n");
            },
        },
        {
            name: "speckit_load",
            description:
                "Load the primary artifacts (spec.md, plan.md, tasks.md) for a specific feature spec. " +
                "Provide the spec ID (directory name, e.g. '20260511-211823-compose-screenshot-testing') or a partial match. " +
                "Optionally load only specific artifacts.",
            parameters: {
                type: "object",
                properties: {
                    spec_id: {
                        type: "string",
                        description:
                            "The spec directory name or partial match (e.g. '001', 'mesh-discovery', 'node-list')",
                    },
                    artifacts: {
                        type: "array",
                        items: { type: "string" },
                        description:
                            "Which artifacts to load. Defaults to ['spec.md', 'plan.md', 'tasks.md']. " +
                            "Can include any file path like 'data-model.md', 'contracts/deep-links.md', etc.",
                    },
                },
                required: ["spec_id"],
            },
            skipPermission: true,
            handler: async (args) => {
                const specs = await discoverSpecs();
                const query = args.spec_id.toLowerCase();
                const match = specs.find(
                    (s) =>
                        s.id.toLowerCase() === query ||
                        s.id.toLowerCase().includes(query),
                );

                if (!match) {
                    const available = specs.map((s) => s.id).join(", ");
                    return `No spec matching '${args.spec_id}'. Available: ${available || "none"}`;
                }

                const toLoad = args.artifacts || ["spec.md", "plan.md", "tasks.md"];
                const results = [];

                for (const artifact of toLoad) {
                    const filePath = join(match.path, artifact);
                    if (await fileExists(filePath)) {
                        const content = await readFile(filePath, "utf-8");
                        results.push(`--- ${artifact} (${match.id}) ---\n${content}`);
                    } else {
                        results.push(`--- ${artifact} --- NOT FOUND`);
                    }
                }

                return results.join("\n\n");
            },
        },
        {
            name: "speckit_constitution",
            description:
                "Display the project constitution that all specs must conform to. " +
                "The constitution defines non-negotiable principles for the Meshtastic Android project.",
            parameters: { type: "object", properties: {} },
            skipPermission: true,
            handler: async () => {
                const constitutionPath = join(SPECIFY_DIR, "memory", "constitution.md");
                if (!(await fileExists(constitutionPath))) {
                    return "No constitution found at .specify/memory/constitution.md. Use /speckit.constitution to create one.";
                }
                return await readFile(constitutionPath, "utf-8");
            },
        },
        {
            name: "speckit_status",
            description:
                "Show overall Spec Kit workflow status: specs count, constitution version, " +
                "template availability, and readiness for each workflow stage.",
            parameters: { type: "object", properties: {} },
            skipPermission: true,
            handler: async () => {
                const specs = await discoverSpecs();
                const hasConstitution = await fileExists(join(SPECIFY_DIR, "memory", "constitution.md"));
                const hasTemplates = await dirExists(join(SPECIFY_DIR, "templates"));
                const hasExtensions = await fileExists(join(SPECIFY_DIR, "extensions.yml"));

                let constitutionVersion = "none";
                if (hasConstitution) {
                    try {
                        const content = await readFile(
                            join(SPECIFY_DIR, "memory", "constitution.md"),
                            "utf-8",
                        );
                        const match = content.match(/\*\*Version\*\*:\s*([\d.]+)/i) ||
                            content.match(/version[:\s]+v?([\d.]+)/i);
                        if (match) constitutionVersion = `v${match[1]}`;
                    } catch { /* skip */ }
                }

                let templateList = [];
                if (hasTemplates) {
                    try {
                        const entries = await readdir(join(SPECIFY_DIR, "templates"));
                        templateList = entries.filter((e) => e.endsWith(".md"));
                    } catch { /* skip */ }
                }

                const lines = [
                    "# Spec Kit Status\n",
                    `**Constitution:** ${hasConstitution ? `✓ (${constitutionVersion})` : "✗ not found"}`,
                    `**Templates:** ${templateList.length > 0 ? `✓ (${templateList.join(", ")})` : "✗ none"}`,
                    `**Extensions:** ${hasExtensions ? "✓ configured" : "✗ not found"}`,
                    `**Specs:** ${specs.length} feature(s)\n`,
                ];

                if (specs.length > 0) {
                    lines.push("| Spec | Spec.md | Plan.md | Tasks.md | Progress |");
                    lines.push("|------|---------|---------|----------|----------|");
                    for (const s of specs) {
                        const progress = s.taskStats
                            ? `${s.taskStats.done}/${s.taskStats.total}`
                            : "—";
                        lines.push(
                            `| ${s.id} | ${s.hasSpec ? "✓" : "✗"} | ${s.hasPlan ? "✓" : "✗"} | ${s.hasTasks ? "✓" : "✗"} | ${progress} |`,
                        );
                    }
                }

                lines.push(
                    "\n## Workflow Commands",
                    "specify → clarify → plan → tasks → analyze → implement",
                    "\nUse `/speckit.specify` to start a new feature, or `speckit_load` to review an existing one.",
                );

                return lines.join("\n");
            },
        },
    ],
    hooks: {
        onSessionStart: async () => {
            const specs = await discoverSpecs();
            if (specs.length === 0) return;

            const summary = specs
                .map((s) => {
                    const progress = s.taskStats
                        ? ` (${s.taskStats.done}/${s.taskStats.total} tasks)`
                        : "";
                    return `- ${s.id}: ${s.title}${progress}`;
                })
                .join("\n");

            return {
                additionalContext: [
                    `[Spec Kit] ${specs.length} feature spec(s) found in specs/:`,
                    summary,
                    "",
                    "Use speckit_list, speckit_load, speckit_status, or speckit_constitution tools for spec details.",
                    "Use /speckit.specify, /speckit.plan, /speckit.tasks, /speckit.analyze, /speckit.implement for workflow commands.",
                ].join("\n"),
            };
        },
    },
});
