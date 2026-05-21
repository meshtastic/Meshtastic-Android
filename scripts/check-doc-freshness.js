#!/usr/bin/env node
// scripts/check-doc-freshness.js
// Reports doc pages whose last_updated frontmatter is older than a threshold.
// Exit 0 = all fresh, Exit 1 = stale pages found (advisory).
//
// Usage: node scripts/check-doc-freshness.js [docs-dir] [--max-age-days=180]

"use strict";

const fs = require("fs");
const path = require("path");
const { parseFrontmatter, forEachDocPage } = require("./lib/frontmatter");

const args = process.argv.slice(2);
const positional = args.filter(a => !a.startsWith("--"));
const DOCS_DIR = path.resolve(positional[0] || path.join("docs", "en"));

const maxAgeArg = args.find(a => a.startsWith("--max-age-days="));
const MAX_AGE_DAYS = maxAgeArg ? parseInt(maxAgeArg.split("=")[1], 10) : 180;

const now = new Date();
let staleCount = 0;
let totalCount = 0;

console.log(`Checking doc freshness (max age: ${MAX_AGE_DAYS} days)...`);
console.log("");

forEachDocPage(DOCS_DIR, (filePath, slug, section) => {
    totalCount++;
    const content = fs.readFileSync(filePath, "utf-8");
    const { fields } = parseFrontmatter(content);

    if (!fields.last_updated) {
        console.log(`  ⚠  ${section}/${slug}.md — missing last_updated field`);
        staleCount++;
        return;
    }

    const lastUpdated = new Date(fields.last_updated);
    const ageDays = Math.floor((now - lastUpdated) / (1000 * 60 * 60 * 24));

    if (ageDays > MAX_AGE_DAYS) {
        console.log(`  ⚠  ${section}/${slug}.md — ${ageDays} days old (last: ${fields.last_updated})`);
        staleCount++;
    }
});

console.log("");
if (staleCount > 0) {
    console.log(`${staleCount}/${totalCount} page(s) need review (older than ${MAX_AGE_DAYS} days or missing date).`);
    process.exit(1);
} else {
    console.log(`All ${totalCount} pages are fresh (updated within ${MAX_AGE_DAYS} days).`);
    process.exit(0);
}
