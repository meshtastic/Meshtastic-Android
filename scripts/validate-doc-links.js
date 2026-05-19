#!/usr/bin/env node
// scripts/validate-doc-links.js
// Validates internal cross-references and image paths in in-app documentation.
// Exit 0 = all valid, Exit 1 = broken links found.
//
// Usage: node scripts/validate-doc-links.js [docs-dir]

"use strict";

const fs = require("fs");
const path = require("path");
const { discoverSlugs, forEachDocPage } = require("./lib/frontmatter");

const DOCS_DIR = path.resolve(process.argv[2] || path.join("docs", "en"));
// Assets (screenshots) live at docs/ root, not inside the en/ locale folder
const DOCS_ROOT = path.resolve(DOCS_DIR, "..");
const IMAGE_EXTS = new Set([".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp"]);

// Collect known page slugs from both sections
const knownPages = new Set([
    ...discoverSlugs(DOCS_DIR, "user"),
    ...discoverSlugs(DOCS_DIR, "developer"),
]);

console.log(`Validating links across ${knownPages.size} doc pages in ${DOCS_DIR}...`);

let errors = 0;

forEachDocPage(DOCS_DIR, (filePath, slug, section) => {
    const lines = fs.readFileSync(filePath, "utf-8").split("\n");

    lines.forEach((line, idx) => {
        const lineNum = idx + 1;

        // Check markdown links (non-image)
        let match;
        const linkRe = /(?<!!)\[([^\]]*)\]\(([^)]+)\)/g;
        while ((match = linkRe.exec(line)) !== null) {
            const target = match[2];

            if (/^(https?:|mailto:|#)/.test(target)) continue;

            const ext = path.extname(target).toLowerCase();
            if (IMAGE_EXTS.has(ext)) continue;

            let targetSlug = target
                .replace(/\.md$/, "")
                .replace(/^\.\//, "")
                .replace(/#.*$/, "");

            const crossMatch = targetSlug.match(/^\.\.\/\w+\/(.+)/);
            if (crossMatch) targetSlug = crossMatch[1];
            targetSlug = targetSlug.split("/").pop();

            if (targetSlug && !knownPages.has(targetSlug)) {
                console.log(`  ERROR: ${section}/${slug}.md:${lineNum} — broken link to '${targetSlug}'`);
                errors++;
            }
        }

        // Check image references
        let imgMatch;
        const imgRe = /!\[([^\]]*)\]\(([^)]+)\)/g;
        while ((imgMatch = imgRe.exec(line)) !== null) {
            const imgPath = imgMatch[2];
            if (/^https?:/.test(imgPath)) continue;

            const resolved = imgPath.startsWith("/")
                ? path.join(DOCS_ROOT, imgPath)
                : path.resolve(path.dirname(filePath), imgPath);
            if (!fs.existsSync(resolved)) {
                console.log(`  ERROR: ${section}/${slug}.md:${lineNum} — missing image '${imgPath}'`);
                errors++;
            }
        }
    });
});

if (errors > 0) {
    console.log(`\nFAILED: ${errors} broken link(s) found.`);
    process.exit(1);
} else {
    console.log("PASSED: All internal links and images are valid.");
    process.exit(0);
}
