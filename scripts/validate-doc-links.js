#!/usr/bin/env node
// scripts/validate-doc-links.js
// Validates internal cross-references and image paths in in-app documentation.
// Exit 0 = all valid, Exit 1 = broken links found.
//
// Usage: node scripts/validate-doc-links.js [docs-dir]

"use strict";

const fs = require("fs");
const path = require("path");

const DOCS_DIR = path.resolve(process.argv[2] || "docs");
const IMAGE_EXTS = new Set([".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp"]);

// Collect known page slugs
const knownPages = new Set();
for (const section of ["user", "developer"]) {
    const dir = path.join(DOCS_DIR, section);
    if (!fs.existsSync(dir)) continue;
    for (const f of fs.readdirSync(dir).filter(f => f.endsWith(".md"))) {
        knownPages.add(f.replace(/\.md$/, ""));
    }
}

console.log(`Validating links across ${knownPages.size} doc pages in ${DOCS_DIR}...`);

let errors = 0;

// Regex patterns
const mdLinkRe = /\[([^\]]*)\]\(([^)]+)\)/g;
const imgLinkRe = /!\[([^\]]*)\]\(([^)]+)\)/g;

for (const section of ["user", "developer"]) {
    const dir = path.join(DOCS_DIR, section);
    if (!fs.existsSync(dir)) continue;

    for (const file of fs.readdirSync(dir).filter(f => f.endsWith(".md"))) {
        const filePath = path.join(dir, file);
        const lines = fs.readFileSync(filePath, "utf-8").split("\n");

        lines.forEach((line, idx) => {
            const lineNum = idx + 1;

            // Check markdown links (non-image)
            let match;
            const linkRe = /(?<!!)\[([^\]]*)\]\(([^)]+)\)/g;
            while ((match = linkRe.exec(line)) !== null) {
                const target = match[2];

                // Skip external URLs, anchors, mailto
                if (/^(https?:|mailto:|#)/.test(target)) continue;

                // Skip image file references
                const ext = path.extname(target).toLowerCase();
                if (IMAGE_EXTS.has(ext)) continue;

                // Normalize target to a slug
                let slug = target
                    .replace(/\.md$/, "")  // strip .md
                    .replace(/^\.\//, "")  // strip ./
                    .replace(/#.*$/, "");  // strip anchor

                // Handle cross-section links (../developer/testing)
                const crossMatch = slug.match(/^\.\.\/\w+\/(.+)/);
                if (crossMatch) {
                    slug = crossMatch[1];
                }

                // Strip any remaining path components
                slug = slug.split("/").pop();

                if (slug && !knownPages.has(slug)) {
                    console.log(`  ERROR: ${section}/${file}:${lineNum} — broken link to '${slug}'`);
                    errors++;
                }
            }

            // Check image references
            let imgMatch;
            const imgRe = /!\[([^\]]*)\]\(([^)]+)\)/g;
            while ((imgMatch = imgRe.exec(line)) !== null) {
                const imgPath = imgMatch[2];

                // Skip external URLs
                if (/^https?:/.test(imgPath)) continue;

                // Resolve: root-relative paths (starting with /) resolve from DOCS_DIR,
                // relative paths resolve from the file's directory
                const resolved = imgPath.startsWith("/")
                    ? path.join(DOCS_DIR, imgPath)
                    : path.resolve(path.dirname(filePath), imgPath);
                if (!fs.existsSync(resolved)) {
                    console.log(`  ERROR: ${section}/${file}:${lineNum} — missing image '${imgPath}'`);
                    errors++;
                }
            }
        });
    }
}

if (errors > 0) {
    console.log(`\nFAILED: ${errors} broken link(s) found.`);
    process.exit(1);
} else {
    console.log("PASSED: All internal links and images are valid.");
    process.exit(0);
}
