"use strict";

const fs = require("fs");
const path = require("path");

const FM_RE = /^---\n([\s\S]*?)\n---\n/;

/**
 * Parse YAML-ish frontmatter from a markdown string.
 * Returns { fields: { key: string }, body: string, raw: string }.
 * `fields` maps lowercase keys to their raw string values (no YAML arrays).
 */
function parseFrontmatter(content) {
    const match = content.match(FM_RE);
    if (!match) return { fields: {}, body: content, raw: "" };

    const raw = match[1];
    const body = content.slice(match[0].length);
    const fields = {};

    for (const line of raw.split("\n")) {
        const kv = line.match(/^(\w[\w_-]*):\s*(.*)/);
        if (kv) fields[kv[1]] = kv[2].trim();
    }

    return { fields, body, raw };
}

/** Discover all .md page slugs under docs/{section}/ */
function discoverSlugs(docsDir, section) {
    const dir = path.join(docsDir, section);
    if (!fs.existsSync(dir)) return new Set();
    return new Set(
        fs.readdirSync(dir)
            .filter(f => f.endsWith(".md"))
            .map(f => f.replace(/\.md$/, "")),
    );
}

/** Iterate all doc pages, calling fn(filePath, slug, section) */
function forEachDocPage(docsDir, fn) {
    for (const section of ["user", "developer"]) {
        const dir = path.join(docsDir, section);
        if (!fs.existsSync(dir)) continue;
        for (const file of fs.readdirSync(dir).filter(f => f.endsWith(".md")).sort()) {
            fn(path.join(dir, file), file.replace(/\.md$/, ""), section);
        }
    }
}

module.exports = { parseFrontmatter, discoverSlugs, forEachDocPage };
