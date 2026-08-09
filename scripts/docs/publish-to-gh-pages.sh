#!/usr/bin/env bash
# Publish one or more docs channels to the persistent gh-pages branch.
#
# Unlike the artifact-based deploy-pages model (which replaces the whole site
# on every deploy), this overlays ONLY the channels being rebuilt, so
# previously published /vX.Y.Z/ folders survive main-branch snapshot deploys
# and vice versa.
#
# Usage: publish-to-gh-pages.sh <staging-dir> <channel> [<channel>...]
#
#   <staging-dir>  directory containing one subdirectory per channel:
#                    staging/root/   -> site root (latest release docs)
#                    staging/main/   -> /main/ (snapshot of main branch)
#                    staging/api/    -> /api/ (Dokka)
#                    staging/vX.Y.Z/ -> /vX.Y.Z/ (versioned release docs)
#   <channel>      subdirectory names to publish (root|main|api|vX.Y.Z)
#
# Must run from inside the repo checkout (uses a git worktree so the
# credentials persisted by actions/checkout apply to the push).
# Requires: rsync, python3.

set -euo pipefail

STAGING=$(cd "$1" && pwd)
shift
CHANNELS=("$@")

if [ ${#CHANNELS[@]} -eq 0 ]; then
    echo "No channels given" >&2
    exit 1
fi

SITE_DIR=$(mktemp -d)
cleanup() {
    git worktree remove --force "$SITE_DIR" 2> /dev/null || rm -rf "$SITE_DIR"
}
trap cleanup EXIT

# Check out existing gh-pages content, or start an orphan history.
rmdir "$SITE_DIR"
if git fetch origin gh-pages 2> /dev/null; then
    git worktree add -B gh-pages "$SITE_DIR" FETCH_HEAD
else
    git worktree add --detach "$SITE_DIR"
    git -C "$SITE_DIR" checkout --orphan gh-pages
    git -C "$SITE_DIR" rm -rf --quiet . || true
fi

for channel in "${CHANNELS[@]}"; do
    src="$STAGING/$channel"
    if [ ! -d "$src" ]; then
        echo "Missing staging dir for channel '$channel': $src" >&2
        exit 1
    fi
    if [ "$channel" = "root" ]; then
        # Replace root files but preserve the other channels living beside them.
        # --checksum: freshly built files and the just-checked-out worktree can
        # share size + same-second mtime, which defeats rsync's quick check.
        rsync -a --checksum --delete \
            --exclude='.git' \
            --exclude='main/' \
            --exclude='api/' \
            --exclude='v[0-9]*/' \
            --exclude='versions.json' \
            "$src/" "$SITE_DIR/"
    else
        rsync -a --checksum --delete --exclude='.git' "$src/" "$SITE_DIR/$channel/"
    fi
    echo "Published channel: $channel"
done

# Regenerate the version manifest consumed by the site's version switcher, and
# (re)write the root placeholder when no production release owns the root yet.
# Shared with post-release-cleanup.yml so the two can never derive it differently.
python3 "$(dirname "$0")/regenerate-versions.py" "$SITE_DIR"

# Pages must serve this branch as-is; the site is already built.
touch "$SITE_DIR/.nojekyll"

cd "$SITE_DIR"
git add -A
if git diff --cached --quiet; then
    echo "No changes to publish"
    exit 0
fi
git -c user.name='github-actions[bot]' \
    -c user.email='41898282+github-actions[bot]@users.noreply.github.com' \
    commit -m "docs: publish channels: ${CHANNELS[*]}"
git push origin HEAD:gh-pages
