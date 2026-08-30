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

/**
 * Read a YAML list field out of a raw frontmatter block (the `raw` from parseFrontmatter).
 * `fields` cannot carry these: a block list leaves the key's own line empty.
 * Handles both the block form used by every page —
 *     aliases:
 *       - bluetooth
 *       - usb
 * — and the inline form `aliases: [bluetooth, usb]`. Returns [] when the key is absent.
 */
function parseListField(rawFrontmatter, key) {
    const lines = rawFrontmatter.split("\n");
    const values = [];
    let inList = false;

    for (const line of lines) {
        if (!inList) {
            const head = line.match(new RegExp(`^${key}:\\s*(.*)$`));
            if (!head) continue;

            const inline = head[1].trim();
            if (inline.startsWith("[")) {
                return inline
                    .replace(/^\[|\]$/g, "")
                    .split(",")
                    .map(v => v.trim().replace(/^["']|["']$/g, ""))
                    .filter(v => v.length > 0);
            }
            if (inline.length > 0) return [inline.replace(/^["']|["']$/g, "")];
            inList = true;
            continue;
        }

        const item = line.match(/^\s+-\s+(.*)$/);
        if (item) {
            values.push(item[1].trim().replace(/^["']|["']$/g, ""));
            continue;
        }
        // Any non-item line at this point ends the list.
        if (line.trim().length > 0) break;
    }

    return values;
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

module.exports = { parseFrontmatter, parseListField, discoverSlugs, forEachDocPage };
