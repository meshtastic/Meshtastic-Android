#!/usr/bin/env node
// scripts/sync-android-docs.js
// Transforms Android in-app docs for publishing on the meshtastic.org Docusaurus site.
//
// Usage:  node scripts/sync-android-docs.js <android-repo-path> [--convert-webp] [--dry-run]
//
// <android-repo-path>  Path to a clone of meshtastic/Meshtastic-Android (or omit to
//                      auto-detect from this script's location in the repo).
// --convert-webp       Convert PNG/JPG/JPEG/GIF images to WebP via cwebp and rewrite
//                      all image references in Markdown to use .webp. Requires cwebp on PATH.
// --dry-run            Print what would be written without actually writing files.
//
// Output structure (relative to CWD, typically the meshtastic/meshtastic repo root):
//   docs/software/android/user/*.md
//   docs/software/android/developer/*.md
//   docs/software/android/index.md
//   static/img/android/docs/*.webp (or .png/.svg if not converting)

"use strict";

const fs = require("fs");
const path = require("path");
const { execSync } = require("child_process");
const { discoverSlugs } = require("./lib/frontmatter");

// ── Configuration ────────────────────────────────────────────────────────────

const args = process.argv.slice(2);
const CONVERT_WEBP = args.includes("--convert-webp");
const DRY_RUN = args.includes("--dry-run");
const positionalArgs = args.filter(a => !a.startsWith("--"));

const WEBP_CONVERTIBLE = new Set([".png", ".jpg", ".jpeg", ".gif"]);
const IMAGE_EXTENSIONS = new Set([".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp"]);

// Resolve source: explicit path argument, or auto-detect from script location
const ANDROID_REPO_ROOT = positionalArgs.length > 0
    ? path.resolve(positionalArgs[0])
    : path.resolve(__dirname, "..");
const SRC_DOCS_DIR = path.join(ANDROID_REPO_ROOT, "docs", "en");
const SRC_SCREENSHOTS_DIR = path.join(ANDROID_REPO_ROOT, "docs", "assets", "screenshots");

if (!fs.existsSync(SRC_DOCS_DIR)) {
    console.error(`Error: docs directory not found at ${SRC_DOCS_DIR}`);
    console.error("Usage: node sync-android-docs.js <android-repo-path> [--convert-webp] [--dry-run]");
    process.exit(1);
}

// Output directories (relative to CWD, which should be the meshtastic/meshtastic repo)
const DEST_DOCS_DIR = path.join("docs", "software", "android");
const DEST_IMAGES_DIR = path.join("static", "img", "android", "docs");

// Derive sibling page slugs from the filesystem (no manual sync needed)
const KNOWN_USER_SLUGS = discoverSlugs(SRC_DOCS_DIR, "user");
const KNOWN_DEV_SLUGS = discoverSlugs(SRC_DOCS_DIR, "developer");

// ── Helpers ──────────────────────────────────────────────────────────────────

function ensureDir(dir) {
    if (!DRY_RUN) {
        fs.mkdirSync(dir, { recursive: true });
    }
}

function writeFile(filePath, content) {
    if (DRY_RUN) {
        console.log(`[dry-run] Would write: ${filePath} (${Buffer.byteLength(content)} bytes)`);
    } else {
        ensureDir(path.dirname(filePath));
        fs.writeFileSync(filePath, content, "utf-8");
        console.log(`Wrote: ${filePath}`);
    }
}

function copyFile(src, dest) {
    if (DRY_RUN) {
        console.log(`[dry-run] Would copy: ${src} → ${dest}`);
    } else {
        ensureDir(path.dirname(dest));
        fs.copyFileSync(src, dest);
        console.log(`Copied: ${dest}`);
    }
}

/**
 * Rewrite image references in markdown to point to /img/android/docs/<file>.
 * When --convert-webp, convertible extensions become .webp.
 */
function rewriteImagePaths(content) {
    function destBasename(imgPath) {
        const base = path.basename(imgPath);
        const ext = path.extname(base).toLowerCase();
        if (CONVERT_WEBP && WEBP_CONVERTIBLE.has(ext)) {
            return base.slice(0, -ext.length) + ".webp";
        }
        return base;
    }

    return content
        .replace(
            /!\[([^\]]*)\]\((?!https?:\/\/)(?!\/img\/)([^)]+)\)/g,
            (match, alt, imgPath) => {
                const ext = path.extname(path.basename(imgPath)).toLowerCase();
                if (!IMAGE_EXTENSIONS.has(ext)) return match;
                return `![${alt}](/img/android/docs/${destBasename(imgPath)})`;
            },
        )
        .replace(
            /<img\s+([^>]*?)src=["'](?!https?:\/\/)(?!\/img\/)([^"']+)["']([^>]*)>/gi,
            (match, before, imgPath, after) => {
                const ext = path.extname(path.basename(imgPath)).toLowerCase();
                if (!IMAGE_EXTENSIONS.has(ext)) return match;
                return `<img ${before}src="/img/android/docs/${destBasename(imgPath)}"${after}>`;
            },
        );
}

/**
 * Rewrite relative markdown links between sibling pages.
 * e.g., `[text](connections)` → `[text](connections.md)`
 * e.g., `[text](../developer/testing)` → `[text](../developer/testing.md)`
 */
function rewriteSiblingLinks(content, section, isIndex = false) {
    const slugs = section === "user" ? KNOWN_USER_SLUGS : KNOWN_DEV_SLUGS;

    // Match [text](link) where link is NOT an absolute URL, NOT an anchor, NOT already .md
    return content.replace(
        /\[([^\]]*)\]\((?!https?:\/\/)(?!#)([^)]+)\)/g,
        (match, text, link) => {
            // Skip if already has .md extension or is an image
            if (link.endsWith(".md") || IMAGE_EXTENSIONS.has(path.extname(link).toLowerCase())) {
                return match;
            }

            // Section landing page (user/index.md, developer/index.md): the source
            // user.md/developer.md sit beside the section dir, so they link to children
            // as "user/onboarding". From index.md inside that dir, strip the prefix.
            if (isIndex) {
                const sameSection = link.match(new RegExp(`^${section}/(.+)`));
                if (sameSection && slugs.has(sameSection[1])) {
                    return `[${text}](${sameSection[1]}.md)`;
                }
            }

            // Check for cross-section links like ../developer/testing
            const crossMatch = link.match(/^\.\.\/(\w+)\/(.+)/);
            if (crossMatch) {
                const [, targetSection, slug] = crossMatch;
                const targetSlugs = targetSection === "user" ? KNOWN_USER_SLUGS : KNOWN_DEV_SLUGS;
                if (targetSlugs.has(slug)) {
                    return `[${text}](../${targetSection}/${slug}.md)`;
                }
            }

            // Check for sibling links
            const bare = link.replace(/^\.\//, "");
            if (slugs.has(bare)) {
                return `[${text}](${bare}.md)`;
            }

            return match;
        },
    );
}

/**
 * Transform Jekyll/kramdown frontmatter to Docusaurus-compatible format.
 * Strips `parent`, `aliases`, and remaps `nav_order` to `sidebar_position`.
 */
function transformFrontmatter(content, section) {
    const fmMatch = content.match(/^---\n([\s\S]*?)\n---\n/);
    if (!fmMatch) return content;

    const fmBlock = fmMatch[1];
    const body = content.slice(fmMatch[0].length);

    const lines = fmBlock.split("\n");
    const newLines = [];
    let sidebarPosition = null;

    for (const line of lines) {
        const trimmed = line.trim();

        // Skip Jekyll-specific fields
        if (trimmed.startsWith("parent:")) continue;
        if (trimmed.startsWith("aliases:")) continue;
        if (trimmed.startsWith("- ")) continue; // alias list items

        // Remap nav_order → sidebar_position
        const navMatch = trimmed.match(/^nav_order:\s*(\d+)/);
        if (navMatch) {
            sidebarPosition = navMatch[1];
            newLines.push(`sidebar_position: ${sidebarPosition}`);
            continue;
        }

        if (trimmed) newLines.push(line);
    }

    // Add parent reference for Docusaurus category
    const parentTitle = section === "user" ? "User Guide" : "Developer Guide";
    newLines.push(`parent: ${parentTitle}`);

    return `---\n${newLines.join("\n")}\n---\n${body}`;
}

/**
 * Convert Jekyll-style callouts to Docusaurus admonitions.
 * > **Tip — text** → :::tip\ntext\n:::
 */
function convertCallouts(content) {
    // Match blockquotes starting with **Tip/Note/Warning —
    return content.replace(
        /^(> \*\*(Tip|Note|Warning)\s*[—–-]\s*)([^*]*)\*\*\s*([\s\S]*?)(?=\n(?!>)|$)/gm,
        (match, prefix, type, title, body) => {
            const admonitionType = type.toLowerCase();
            const cleanBody = body.replace(/^>\s?/gm, "").trim();
            const fullContent = title.trim() ? `${title.trim()} ${cleanBody}` : cleanBody;
            return `:::${admonitionType}\n${fullContent}\n:::`;
        },
    );
}

// ── Main ─────────────────────────────────────────────────────────────────────

function processMarkdown(srcPath, destPath, section, isIndex = false) {
    let content = fs.readFileSync(srcPath, "utf-8");
    content = transformFrontmatter(content, section);
    content = rewriteImagePaths(content);
    content = rewriteSiblingLinks(content, section, isIndex);
    content = convertCallouts(content);
    writeFile(destPath, content);
}

/** Sync screenshots, returning the set of destination basenames written. */
function processImages() {
    const written = new Set();
    if (!fs.existsSync(SRC_SCREENSHOTS_DIR)) {
        console.log("No screenshots directory found, skipping image sync.");
        return written;
    }

    const images = fs.readdirSync(SRC_SCREENSHOTS_DIR)
        .filter(f => IMAGE_EXTENSIONS.has(path.extname(f).toLowerCase()));

    for (const img of images) {
        const srcPath = path.join(SRC_SCREENSHOTS_DIR, img);
        const ext = path.extname(img).toLowerCase();

        if (CONVERT_WEBP && WEBP_CONVERTIBLE.has(ext)) {
            const destName = img.slice(0, -ext.length) + ".webp";
            const destPath = path.join(DEST_IMAGES_DIR, destName);

            if (DRY_RUN) {
                console.log(`[dry-run] Would convert: ${srcPath} → ${destPath}`);
                written.add(destName);
            } else {
                ensureDir(path.dirname(destPath));
                try {
                    execSync(`cwebp -q 80 "${srcPath}" -o "${destPath}"`, { stdio: "pipe" });
                    console.log(`Converted: ${destPath}`);
                    written.add(destName);
                } catch (err) {
                    console.error(`Failed to convert ${img}: ${err.message}`);
                    // Fall back to copying the original
                    copyFile(srcPath, path.join(DEST_IMAGES_DIR, img));
                    written.add(img);
                }
            }
        } else {
            copyFile(srcPath, path.join(DEST_IMAGES_DIR, img));
            written.add(img);
        }
    }
    return written;
}

/** Recursively collect files under a directory, as paths relative to it. */
function collectFiles(dir) {
    const results = [];
    if (!fs.existsSync(dir)) return results;
    (function walk(current) {
        for (const entry of fs.readdirSync(current)) {
            const full = path.join(current, entry);
            if (fs.statSync(full).isDirectory()) walk(full);
            else results.push(path.relative(dir, full));
        }
    })(dir);
    return results;
}

/**
 * Remove destination files this run did not produce, so renamed or deleted
 * source pages (and the screenshots they referenced) don't linger on the site.
 * Mirrors the Apple sync's cleanup pass. Scoped to docs pages (.md/.mdx) and
 * images under the directories this script owns.
 */
function pruneStale(expectedDocPaths, expectedImageNames) {
    for (const file of collectFiles(DEST_DOCS_DIR)) {
        const ext = path.extname(file).toLowerCase();
        if (ext !== ".md" && ext !== ".mdx") continue;
        if (expectedDocPaths.has(file.split(path.sep).join("/"))) continue;
        const target = path.join(DEST_DOCS_DIR, file);
        if (DRY_RUN) {
            console.log(`[dry-run] Would remove stale page: ${target}`);
        } else {
            fs.unlinkSync(target);
            console.log(`Removed stale page: ${target}`);
        }
    }
    for (const file of collectFiles(DEST_IMAGES_DIR)) {
        if (!IMAGE_EXTENSIONS.has(path.extname(file).toLowerCase())) continue;
        if (expectedImageNames.has(path.basename(file))) continue;
        const target = path.join(DEST_IMAGES_DIR, file);
        if (DRY_RUN) {
            console.log(`[dry-run] Would remove stale image: ${target}`);
        } else {
            fs.unlinkSync(target);
            console.log(`Removed stale image: ${target}`);
        }
    }
}

function createIndexPage() {
    const content = `---
title: Android App
sidebar_position: 1
---

# Meshtastic Android & Desktop App

Documentation for the [Meshtastic Android](https://github.com/meshtastic/Meshtastic-Android) application, also available as a Desktop (JVM) app for Linux, macOS, and Windows.

## Guides

- **[User Guide](user/)** — Setup, messaging, nodes, maps, settings, and more
- **[Developer Guide](developer/)** — Architecture, KMP conventions, testing, and contributing
`;
    writeFile(path.join(DEST_DOCS_DIR, "index.md"), content);
}

function createCategoryFiles() {
    // Top-level section category. Uses the synced index.md as the landing page
    // (matching the Apple sync), replacing any hand-written generated-index that
    // would otherwise collide with index.md.
    // Unique `key` per category: the "User Guide" / "Developer Guide" labels also
    // exist in the Apple section, and Docusaurus derives sidebar translation keys
    // from the label, so without a key the two sections collide at build time.
    const sectionCategory = `key: androidApp
label: Android App
collapsible: true
position: 1
link:
  type: doc
  id: software/android/index
`;
    const userCategory = `key: androidUserGuide
label: User Guide
collapsible: true
position: 1
link:
  type: doc
  id: software/android/user/index
`;
    const devCategory = `key: androidDeveloperGuide
label: Developer Guide
collapsible: true
position: 2
link:
  type: doc
  id: software/android/developer/index
`;
    writeFile(path.join(DEST_DOCS_DIR, "_category_.yml"), sectionCategory);
    writeFile(path.join(DEST_DOCS_DIR, "user", "_category_.yml"), userCategory);
    writeFile(path.join(DEST_DOCS_DIR, "developer", "_category_.yml"), devCategory);
}

function main() {
    console.log(`Source: ${SRC_DOCS_DIR}`);
    console.log(`Destination: ${DEST_DOCS_DIR}`);
    console.log(`WebP conversion: ${CONVERT_WEBP ? "enabled" : "disabled"}`);
    console.log(`Dry run: ${DRY_RUN}`);
    console.log("");

    // Track everything this run produces so stale files can be pruned afterwards.
    const expectedDocPaths = new Set([
        "index.md",
        "_category_.yml",
        "user/_category_.yml",
        "developer/_category_.yml",
    ]);

    // Section overview pages: docs/en/user.md → user/index.md and
    // docs/en/developer.md → developer/index.md. Docusaurus uses <dir>/index.md
    // as the category landing page, so the index.md "User Guide" / "Developer
    // Guide" links resolve (matching the Apple sync).
    const sections = [
        { src: "user.md", dest: ["user", "index.md"], section: "user", key: "user/index.md" },
        { src: "developer.md", dest: ["developer", "index.md"], section: "developer", key: "developer/index.md" },
    ];
    for (const { src, dest, section, key } of sections) {
        const overview = path.join(SRC_DOCS_DIR, src);
        if (fs.existsSync(overview)) {
            processMarkdown(overview, path.join(DEST_DOCS_DIR, ...dest), section, true);
            expectedDocPaths.add(key);
        }
    }

    // Process user guide
    const userDir = path.join(SRC_DOCS_DIR, "user");
    if (fs.existsSync(userDir)) {
        for (const file of fs.readdirSync(userDir).filter(f => f.endsWith(".md"))) {
            processMarkdown(
                path.join(userDir, file),
                path.join(DEST_DOCS_DIR, "user", file),
                "user",
            );
            expectedDocPaths.add(`user/${file}`);
        }
    }

    // Process developer guide
    const devDir = path.join(SRC_DOCS_DIR, "developer");
    if (fs.existsSync(devDir)) {
        for (const file of fs.readdirSync(devDir).filter(f => f.endsWith(".md"))) {
            processMarkdown(
                path.join(devDir, file),
                path.join(DEST_DOCS_DIR, "developer", file),
                "developer",
            );
            expectedDocPaths.add(`developer/${file}`);
        }
    }

    // Create index and category files
    createIndexPage();
    createCategoryFiles();

    // Process images
    const expectedImageNames = processImages();

    // Remove anything left over from a previous sync or the old hand-written docs.
    pruneStale(expectedDocPaths, expectedImageNames);

    console.log("\nSync complete.");
}

main();
