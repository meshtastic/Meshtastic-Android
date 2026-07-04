#!/usr/bin/env node
// scripts/check-doc-coverage.js
// Checks that each user-facing feature module has corresponding documentation.
// Exit 0 = full coverage, Exit 1 = gaps found.
//
// Usage: node scripts/check-doc-coverage.js [repo-root]

"use strict";

const fs = require("fs");
const path = require("path");
const { forEachDocPage } = require("./lib/frontmatter");

const REPO_ROOT = path.resolve(process.argv[2] || ".");
const DOCS_DIR = path.join(REPO_ROOT, "docs", "en");

// Map of feature module directory names to expected doc page slugs.
// Modules not listed here are considered internal (no user-facing docs required).
const MODULE_TO_DOCS = {
    "feature/connections":  { pages: ["connections"], section: "user" },
    "feature/discovery":    { pages: ["discovery"], section: "user" },
    "feature/docs":         { pages: [], section: "user", internal: true },
    "feature/firmware":     { pages: ["firmware"], section: "user" },
    "feature/intro":        { pages: ["onboarding"], section: "user" },
    "feature/map":          { pages: ["map-and-waypoints"], section: "user" },
    "feature/messaging":    { pages: ["messages-and-channels"], section: "user" },
    "feature/node":         { pages: ["nodes", "node-metrics"], section: "user" },
    "feature/settings":     { pages: ["settings-radio-user", "settings-module-admin"], section: "user" },
    "feature/telemetry":    { pages: ["telemetry-and-sensors"], section: "user" },
};

// Collect existing doc pages
const existingPages = new Set();
forEachDocPage(DOCS_DIR, (_filePath, slug, section) => {
    existingPages.add(`${section}/${slug}`);
});

console.log(`Checking doc coverage for ${Object.keys(MODULE_TO_DOCS).length} feature modules...`);
console.log(`Found ${existingPages.size} doc pages.`);
console.log("");

let gaps = 0;

for (const [module, config] of Object.entries(MODULE_TO_DOCS)) {
    if (config.internal) continue;

    const moduleDir = path.join(REPO_ROOT, module);
    if (!fs.existsSync(moduleDir)) continue;

    for (const page of config.pages) {
        const key = `${config.section}/${page}`;
        if (!existingPages.has(key)) {
            console.log(`  ✗ ${module} → missing ${key}.md`);
            gaps++;
        }
    }
}

// Also check for doc pages that reference non-existent modules (orphans)
const documentedModules = new Set();
for (const config of Object.values(MODULE_TO_DOCS)) {
    for (const page of config.pages) {
        documentedModules.add(`${config.section}/${page}`);
    }
}

// Report coverage summary
const coveredModules = Object.entries(MODULE_TO_DOCS)
    .filter(([, c]) => !c.internal)
    .filter(([m]) => fs.existsSync(path.join(REPO_ROOT, m)));
const totalExpected = coveredModules.reduce((sum, [, c]) => sum + c.pages.length, 0);
const covered = totalExpected - gaps;
const pct = totalExpected > 0 ? Math.round((covered / totalExpected) * 100) : 100;

console.log("");
console.log(`Coverage: ${covered}/${totalExpected} required pages present (${pct}%)`);

if (gaps > 0) {
    console.log(`\n${gaps} documentation gap(s) found.`);
    process.exit(1);
} else {
    console.log("All feature modules have documentation coverage.");
    process.exit(0);
}
