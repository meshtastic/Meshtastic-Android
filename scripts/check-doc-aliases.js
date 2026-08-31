#!/usr/bin/env node
// scripts/check-doc-aliases.js
// Checks that every alias a page declares in frontmatter is also carried by that page's
// entry in DocBundleLoader.kt, the in-app docs index.
// Exit 0 = every authored alias is registered, Exit 1 = drift found.
//
// Contract: the loader MUST be a superset of the frontmatter, not identical to it.
// Only the loader's list reaches the running app — DefaultDocBundleLoader.stripFrontmatter()
// discards the frontmatter before the markdown is rendered, and KeywordSearchEngine scores
// against DocPage.aliases, which comes from the loader. sync-android-docs.js strips aliases
// for Docusaurus too, so an alias that exists only in frontmatter is a search term the author
// wrote and no consumer ever sees — the drift this check exists to catch. The reverse is
// harmless: an extra loader alias is extra search vocabulary for a page that already owns it,
// and several are deliberate (signal-meter keeps "signal-quality"/"signal-strength", translate
// keeps "language"/"i18n"/"contribute"). Demanding equality would make those permanently red.
//
// Usage: node scripts/check-doc-aliases.js [repo-root]

"use strict";

const fs = require("fs");
const path = require("path");
const { parseFrontmatter, parseListField, forEachDocPage } = require("./lib/frontmatter");

const REPO_ROOT = path.resolve(process.argv[2] || ".");
const DOCS_DIR = path.join(REPO_ROOT, "docs", "en");
const LOADER_REL = path.join(
    "feature", "docs", "src", "commonMain", "kotlin", "org", "meshtastic", "feature", "docs", "data",
    "DocBundleLoader.kt",
);
const LOADER_PATH = path.join(REPO_ROOT, LOADER_REL);

// A page entry in the loader is anchored on its resourcePath literal. User entries
// (UserPageDef) carry one list — the aliases; their keywords come from a string resource.
// Developer entries (KeywordIndexEntry) carry two — keywords first, then aliases.
const ALIAS_LIST_INDEX = { user: 0, developer: 1 };
const EXPECTED_LISTS = { user: 1, developer: 2 };

/** Extract the string literals of each listOf(...)/emptyList() group in a slice of Kotlin. */
function parseListGroups(source) {
    const groups = [];
    const openRe = /\b(listOf|emptyList)\s*\(/g;
    let match;

    while ((match = openRe.exec(source)) !== null) {
        if (match[1] === "emptyList") {
            groups.push([]);
            continue;
        }

        // Walk forward from the opening paren, string-aware, to find its partner.
        let depth = 1;
        let i = openRe.lastIndex;
        let inString = false;
        while (i < source.length && depth > 0) {
            const ch = source[i];
            if (inString) {
                if (ch === "\\") i++;
                else if (ch === '"') inString = false;
            } else if (ch === '"') {
                inString = true;
            } else if (ch === "(") {
                depth++;
            } else if (ch === ")") {
                depth--;
            }
            i++;
        }

        const inner = source.slice(openRe.lastIndex, i - 1);
        groups.push([...inner.matchAll(/"([^"\\]*)"/g)].map(m => m[1]));
        openRe.lastIndex = i;
    }

    return groups;
}

/** Slice out the constructor call that encloses `index`, parens balanced. */
function enclosingEntry(source, index) {
    const ctorRe = /\b(UserPageDef|KeywordIndexEntry)\s*\(/g;
    let open = -1;
    let match;
    while ((match = ctorRe.exec(source)) !== null && match.index < index) {
        open = ctorRe.lastIndex;
    }
    if (open < 0) return null;

    let depth = 1;
    let i = open;
    let inString = false;
    while (i < source.length && depth > 0) {
        const ch = source[i];
        if (inString) {
            if (ch === "\\") i++;
            else if (ch === '"') inString = false;
        } else if (ch === '"') {
            inString = true;
        } else if (ch === "(") {
            depth++;
        } else if (ch === ")") {
            depth--;
        }
        i++;
    }
    return i > index ? source.slice(open, i - 1) : null;
}

/** Map "user/nodes" -> { section, slug, lists } for every page registered in DocBundleLoader.kt. */
function parseLoaderEntries(source) {
    const anchorRe = /"en\/(user|developer)\/([a-z0-9-]+)\.html"/g;
    const entries = new Map();

    for (const anchor of source.matchAll(anchorRe)) {
        const [, section, slug] = anchor;
        const body = enclosingEntry(source, anchor.index);
        entries.set(`${section}/${slug}`, { section, slug, lists: body ? parseListGroups(body) : [] });
    }

    return entries;
}

if (!fs.existsSync(LOADER_PATH)) {
    console.log(`ERROR: ${LOADER_REL} not found under ${REPO_ROOT}.`);
    process.exit(1);
}

const loaderEntries = parseLoaderEntries(fs.readFileSync(LOADER_PATH, "utf-8"));

console.log(`Checking frontmatter aliases against ${loaderEntries.size} DocBundleLoader entries...`);
console.log("");

let errors = 0;
let extras = 0;
let checked = 0;

forEachDocPage(DOCS_DIR, (filePath, slug, section) => {
    const relPath = path.relative(REPO_ROOT, filePath);
    const { raw } = parseFrontmatter(fs.readFileSync(filePath, "utf-8"));
    const declared = parseListField(raw, "aliases");

    const entry = loaderEntries.get(`${section}/${slug}`);
    if (!entry) {
        console.log(
            `::error file=${relPath}::no DocBundleLoader.kt entry for '${section}/${slug}', ` +
            `so its ${declared.length} frontmatter alias(es) are not searchable in the app.`,
        );
        errors++;
        return;
    }

    // Guard the shape rather than silently comparing the wrong list: a UserPageDef that grew a
    // keyword list, or a KeywordIndexEntry that lost one, would otherwise pass while checking
    // keywords against aliases.
    if (entry.lists.length !== EXPECTED_LISTS[section]) {
        console.log(
            `::error file=${LOADER_REL}::entry '${section}/${slug}' has ${entry.lists.length} ` +
            `list(s); expected ${EXPECTED_LISTS[section]}. Update check-doc-aliases.js if the ` +
            `loader's entry shape changed.`,
        );
        errors++;
        return;
    }

    checked++;
    const registered = new Set(entry.lists[ALIAS_LIST_INDEX[section]].map(a => a.toLowerCase()));
    const missing = declared.filter(a => !registered.has(a.toLowerCase()));

    if (missing.length > 0) {
        console.log(
            `::error file=${relPath}::aliases ${missing.map(a => `'${a}'`).join(", ")} are declared ` +
            `in frontmatter but missing from the DocBundleLoader.kt entry for '${section}/${slug}', ` +
            `so in-app search will not match them.`,
        );
        errors++;
    }

    const declaredSet = new Set(declared.map(a => a.toLowerCase()));
    const loaderOnly = [...registered].filter(a => !declaredSet.has(a));
    if (loaderOnly.length > 0) {
        console.log(`  note: ${section}/${slug} — loader-only alias(es): ${loaderOnly.join(", ")}`);
        extras++;
    }
});

console.log("");
console.log(`Compared ${checked} page(s); ${extras} carry loader-only aliases (allowed).`);

if (errors > 0) {
    console.log(`\nFAILED: ${errors} page(s) whose frontmatter aliases are not registered in DocBundleLoader.kt.`);
    process.exit(1);
} else {
    console.log("PASSED: every frontmatter alias is registered in DocBundleLoader.kt.");
    process.exit(0);
}
