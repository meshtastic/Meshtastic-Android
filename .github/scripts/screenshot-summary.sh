#!/usr/bin/env bash
# Generates GITHUB_STEP_SUMMARY with thumbnail grid and failure details,
# plus a self-contained HTML gallery artifact for screenshot test results.
#
# Usage: screenshot-summary.sh <outcome> <rendered_dir> <reference_dir> <diffs_dir> <gallery_out>
#   outcome      - "success" or "failure"
#   rendered_dir - path to rendered images (build output)
#   reference_dir- path to committed reference images
#   diffs_dir    - path to diff images (only populated on failure)
#   gallery_out  - output path for the self-contained HTML gallery
#
# Requires: ImageMagick (convert), base64, bash 4+

set -euo pipefail

OUTCOME="${1:?Usage: screenshot-summary.sh <outcome> <rendered_dir> <reference_dir> <diffs_dir> <gallery_out>}"
RENDERED_DIR="${2:?}"
REF_DIR="${3:?}"
DIFFS_DIR="${4:?}"
GALLERY_OUT="${5:?}"

THUMB_DIR=$(mktemp -d)
trap 'rm -rf "$THUMB_DIR"' EXIT

SUMMARY="${GITHUB_STEP_SUMMARY:?GITHUB_STEP_SUMMARY not set}"

# ── Helpers ──────────────────────────────────────────────────────────────────

# Generate a JPEG thumbnail; args: input_png output_jpg
make_thumb() {
  if command -v convert &>/dev/null; then
    convert "$1" -resize 60x120 -quality 50 "$2" 2>/dev/null && return 0
  fi
  if command -v sips &>/dev/null; then
    # macOS: sips -z takes HEIGHT WIDTH
    sips -s format jpeg -s formatOptions 50 -z 120 60 "$1" --out "$2" &>/dev/null && return 0
  fi
  # Fallback: use full PNG as "thumbnail" (no resize)
  cp "$1" "$2" 2>/dev/null
}

# Base64-encode a file (works on both Linux and macOS)
b64() {
  if base64 -w0 "$1" 2>/dev/null; then
    return
  fi
  base64 -i "$1" | tr -d '\n'
}

# Extract a human-readable test name from a filename like:
#   buttonVariantsScreenshot_Light - Phone_8d010ec9_0.png
# → buttonVariantsScreenshot (Light)
pretty_name() {
  local fname
  fname="$(basename "$1" .png)"
  # Strip trailing hash: _xxxxxxxx_0
  fname="${fname%_[0-9]}"
  fname="${fname%_[a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9]}"
  echo "$fname"
}

# ── Collect reference images (canonical list) ────────────────────────────────

mapfile -t REF_FILES < <(find "$REF_DIR" -name "*.png" -type f 2>/dev/null | sort)
TOTAL=${#REF_FILES[@]}

if [ "$TOTAL" -eq 0 ]; then
  echo "No reference images found in $REF_DIR" >&2
  echo "### Screenshot Tests" >> "$SUMMARY"
  echo "" >> "$SUMMARY"
  echo "_No reference images found. Check the build logs._" >> "$SUMMARY"
  exit 0
fi

# ── Collect failures ─────────────────────────────────────────────────────────

mapfile -t DIFF_FILES < <(find "$DIFFS_DIR" -name "*.png" -type f 2>/dev/null | sort)
FAIL_COUNT=${#DIFF_FILES[@]}
PASS_COUNT=$((TOTAL - FAIL_COUNT))

# ── Step Summary: Header ─────────────────────────────────────────────────────

{
  if [ "$OUTCOME" = "failure" ]; then
    echo "## :x: Screenshot Tests — ${FAIL_COUNT} failed, ${PASS_COUNT} passed"
  else
    echo "## :white_check_mark: Screenshot Tests — ${TOTAL} passed"
  fi
  echo ""
  echo "_Download the **screenshot-gallery** artifact for a full-size interactive gallery._"
  echo ""
} >> "$SUMMARY"

# ── Step Summary: Failure details (before / after / diff) ────────────────────

MAX_INLINE_FAILURES=5

if [ "$FAIL_COUNT" -gt 0 ]; then
  SHOWN=0
  {
    echo "### Failed Screenshots"
    echo ""
  } >> "$SUMMARY"

  for diff_file in "${DIFF_FILES[@]}"; do
    if [ "$SHOWN" -ge "$MAX_INLINE_FAILURES" ]; then
      REMAINING=$((FAIL_COUNT - SHOWN))
      echo "" >> "$SUMMARY"
      echo "_…and ${REMAINING} more failure(s). See the artifact for all diffs._" >> "$SUMMARY"
      break
    fi

    rel_path="${diff_file#"$DIFFS_DIR"/}"
    name="$(pretty_name "$diff_file")"
    test_class="$(basename "$(dirname "$rel_path")")"
    ref_file="${REF_DIR}/${rel_path}"
    rendered_file="${RENDERED_DIR}/${rel_path}"

    {
      echo "<details>"
      echo "<summary><b>${test_class}</b> / <code>${name}</code></summary>"
      echo ""
      echo "| Expected | Actual | Diff |"
      echo "|:---:|:---:|:---:|"
    } >> "$SUMMARY"

    # Reference (expected)
    if [ -f "$ref_file" ]; then
      ref_img="<img src=\"data:image/png;base64,$(b64 "$ref_file")\" width=\"280\" />"
    else
      ref_img="_no reference_"
    fi

    # Rendered (actual)
    if [ -f "$rendered_file" ]; then
      rendered_img="<img src=\"data:image/png;base64,$(b64 "$rendered_file")\" width=\"280\" />"
    else
      rendered_img="_no render_"
    fi

    # Diff
    diff_img="<img src=\"data:image/png;base64,$(b64 "$diff_file")\" width=\"280\" />"

    {
      echo "| ${ref_img} | ${rendered_img} | ${diff_img} |"
      echo ""
      echo "</details>"
      echo ""
    } >> "$SUMMARY"

    SHOWN=$((SHOWN + 1))
  done
fi

# ── Step Summary: Thumbnail gallery ─────────────────────────────────────────

{
  echo "### All Screenshots"
  echo ""
} >> "$SUMMARY"

# Build thumbnails and group by test class
declare -A CLASS_THUMBS
SUMMARY_SIZE=$(wc -c < "$SUMMARY" 2>/dev/null || echo 0)
BUDGET=$((950000 - SUMMARY_SIZE))  # Leave headroom within 1 MiB
THUMB_TOTAL_BYTES=0
THUMB_COUNT=0

for ref_file in "${REF_FILES[@]}"; do
  rel_path="${ref_file#"$REF_DIR"/}"
  test_class="$(basename "$(dirname "$rel_path")")"
  fname="$(basename "$ref_file")"

  # Use rendered image if available, fall back to reference
  rendered_file="${RENDERED_DIR}/${rel_path}"
  if [ -f "$rendered_file" ]; then
    src_file="$rendered_file"
  else
    src_file="$ref_file"
  fi

  thumb_file="${THUMB_DIR}/${fname%.png}.jpg"

  make_thumb "$src_file" "$thumb_file"

  if [ ! -f "$thumb_file" ]; then
    continue  # Skip if thumbnail generation failed
  fi

  thumb_size=$(wc -c < "$thumb_file" | tr -d ' ')
  thumb_b64_size=$(( (thumb_size * 4 + 2) / 3 ))  # base64 expansion
  THUMB_TOTAL_BYTES=$((THUMB_TOTAL_BYTES + thumb_b64_size + 100))  # +100 for markup

  if [ "$THUMB_TOTAL_BYTES" -gt "$BUDGET" ]; then
    # Stop adding thumbnails to summary — rest are in the gallery artifact
    break
  fi

  thumb_b64="$(b64 "$thumb_file")"
  name="$(pretty_name "$ref_file")"

  # Check if this image failed
  diff_check="${DIFFS_DIR}/${rel_path}"
  if [ -f "$diff_check" ]; then
    border="border:3px solid red;"
    title="FAILED: ${name}"
  else
    border=""
    title="${name}"
  fi

  img_tag="<img src=\"data:image/jpeg;base64,${thumb_b64}\" title=\"${title}\" alt=\"${name}\" style=\"${border}margin:2px;\" />"

  CLASS_THUMBS["$test_class"]+="${img_tag}"
  THUMB_COUNT=$((THUMB_COUNT + 1))
done

# Write grouped thumbnails
for class in $(echo "${!CLASS_THUMBS[@]}" | tr ' ' '\n' | sort); do
  {
    echo "<details open>"
    echo "<summary><b>${class}</b></summary>"
    echo ""
    echo "${CLASS_THUMBS[$class]}"
    echo ""
    echo "</details>"
    echo ""
  } >> "$SUMMARY"
done

if [ "$THUMB_COUNT" -lt "$TOTAL" ]; then
  REMAINING=$((TOTAL - THUMB_COUNT))
  echo "_${REMAINING} more thumbnail(s) omitted from summary (size limit). See the gallery artifact._" >> "$SUMMARY"
fi

# ── Step Summary: Fix instructions ──────────────────────────────────────────

if [ "$FAIL_COUNT" -gt 0 ]; then
  {
    echo ""
    echo "### How to fix"
    echo '```bash'
    echo "./gradlew updateGoogleDebugScreenshotTest"
    echo '```'
    echo 'Commit the updated `.png` files under `app/src/screenshotTestGoogleDebug/reference/`.'
  } >> "$SUMMARY"
fi

# ── HTML Gallery Artifact ────────────────────────────────────────────────────

mkdir -p "$(dirname "$GALLERY_OUT")"

cat > "$GALLERY_OUT" <<'GALLERY_HEAD'
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>Screenshot Gallery</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: system-ui, sans-serif; background: #1a1a2e; color: #e0e0e0; padding: 20px; }
  h1 { margin-bottom: 8px; color: #fff; }
  .meta { color: #888; margin-bottom: 20px; }
  .filters { margin-bottom: 16px; display: flex; gap: 8px; flex-wrap: wrap; }
  .filters button { padding: 6px 14px; border: 1px solid #444; border-radius: 6px;
    background: #2a2a3e; color: #ccc; cursor: pointer; font-size: 13px; }
  .filters button.active { background: #4a6cf7; color: #fff; border-color: #4a6cf7; }
  .filters input { padding: 6px 12px; border: 1px solid #444; border-radius: 6px;
    background: #2a2a3e; color: #ccc; font-size: 13px; flex: 1; min-width: 200px; }
  .group { margin-bottom: 24px; }
  .group h2 { font-size: 16px; color: #aaa; margin-bottom: 8px; padding-bottom: 4px;
    border-bottom: 1px solid #333; cursor: pointer; }
  .group h2:hover { color: #fff; }
  .grid { display: flex; flex-wrap: wrap; gap: 8px; }
  .card { background: #2a2a3e; border-radius: 8px; overflow: hidden; cursor: pointer;
    transition: transform 0.15s; position: relative; }
  .card:hover { transform: scale(1.03); }
  .card.failed { border: 2px solid #ef4444; }
  .card.passed { border: 1px solid #333; }
  .card img { display: block; height: 200px; width: auto; }
  .card .label { font-size: 11px; padding: 4px 8px; color: #aaa; white-space: nowrap;
    overflow: hidden; text-overflow: ellipsis; max-width: 160px; }
  .badge { position: absolute; top: 4px; right: 4px; font-size: 10px; padding: 2px 6px;
    border-radius: 4px; font-weight: 600; }
  .badge.pass { background: #22c55e; color: #000; }
  .badge.fail { background: #ef4444; color: #fff; }
  /* Lightbox */
  .overlay { display: none; position: fixed; inset: 0; background: rgba(0,0,0,0.9);
    z-index: 100; justify-content: center; align-items: center; flex-direction: column; }
  .overlay.open { display: flex; }
  .overlay img { max-height: 85vh; max-width: 95vw; }
  .overlay .caption { color: #ccc; margin-top: 12px; font-size: 14px; }
  .overlay .close { position: absolute; top: 16px; right: 24px; font-size: 32px;
    color: #fff; cursor: pointer; }
  .overlay .nav { display: flex; gap: 16px; margin-top: 12px; }
  .overlay .nav button { padding: 8px 20px; border: 1px solid #555; border-radius: 6px;
    background: #333; color: #fff; cursor: pointer; font-size: 14px; }
  .overlay .nav button.active-tab { background: #4a6cf7; border-color: #4a6cf7; }
  /* Diff view */
  .diff-row { display: flex; gap: 12px; align-items: flex-start; }
  .diff-row img { max-height: 80vh; max-width: 30vw; }
  .diff-row .diff-label { text-align: center; font-size: 12px; color: #aaa; margin-top: 4px; }
</style>
</head>
<body>
<h1>Screenshot Gallery</h1>
<div class="meta" id="meta"></div>
<div class="filters">
  <button class="active" onclick="filterAll()">All</button>
  <button onclick="filterPassed()">Passed</button>
  <button onclick="filterFailed()">Failed</button>
  <input type="text" id="search" placeholder="Search test names…" oninput="filterSearch()" />
</div>
<div id="gallery"></div>
<div class="overlay" id="lightbox" onclick="closeLightbox(event)">
  <span class="close" onclick="closeLightbox(event)">&times;</span>
  <div id="lb-content"></div>
  <div class="caption" id="lb-caption"></div>
</div>
<script>
const DATA = [];
GALLERY_HEAD

# Inject image data as JSON entries
echo "// Gallery data injected by CI" >> "$GALLERY_OUT"

for ref_file in "${REF_FILES[@]}"; do
  rel_path="${ref_file#"$REF_DIR"/}"
  test_class="$(basename "$(dirname "$rel_path")")"
  name="$(pretty_name "$ref_file")"

  # Use rendered image if available, otherwise reference
  rendered_file="${RENDERED_DIR}/${rel_path}"
  if [ -f "$rendered_file" ]; then
    rendered_b64="$(b64 "$rendered_file")"
  else
    rendered_b64="$(b64 "$ref_file")"
  fi

  ref_b64="$(b64 "$ref_file")"
  diff_file="${DIFFS_DIR}/${rel_path}"

  diff_b64=""
  status="passed"

  if [ -f "$diff_file" ]; then
    status="failed"
    diff_b64="$(b64 "$diff_file")"
  fi

  # Escape for JS string (name shouldn't have quotes but be safe)
  js_name="${name//\\/\\\\}"
  js_name="${js_name//\"/\\\"}"
  js_class="${test_class//\\/\\\\}"
  js_class="${js_class//\"/\\\"}"

  echo "DATA.push({c:\"${js_class}\",n:\"${js_name}\",s:\"${status}\",r:\"${rendered_b64}\",e:\"${ref_b64}\",d:\"${diff_b64}\"});" >> "$GALLERY_OUT"
done

cat >> "$GALLERY_OUT" <<'GALLERY_TAIL'

// ── Render gallery ──────────────────────────────────────────────────────────
const gallery = document.getElementById('gallery');
const meta = document.getElementById('meta');
const passed = DATA.filter(d => d.s === 'passed').length;
const failed = DATA.filter(d => d.s === 'failed').length;
meta.textContent = `${DATA.length} screenshots — ${passed} passed, ${failed} failed`;

let currentFilter = 'all';
let searchTerm = '';

function render() {
  const groups = {};
  DATA.forEach((d, i) => {
    if (currentFilter === 'passed' && d.s !== 'passed') return;
    if (currentFilter === 'failed' && d.s !== 'failed') return;
    if (searchTerm && !d.n.toLowerCase().includes(searchTerm) && !d.c.toLowerCase().includes(searchTerm)) return;
    if (!groups[d.c]) groups[d.c] = [];
    groups[d.c].push({...d, idx: i});
  });
  let html = '';
  Object.keys(groups).sort().forEach(cls => {
    html += `<div class="group"><h2>${cls} (${groups[cls].length})</h2><div class="grid">`;
    groups[cls].forEach(d => {
      const badge = d.s === 'failed'
        ? '<span class="badge fail">FAIL</span>'
        : '<span class="badge pass">PASS</span>';
      html += `<div class="card ${d.s}" onclick="openLightbox(${d.idx})">
        ${badge}
        <img src="data:image/png;base64,${d.r}" loading="lazy" alt="${d.n}" />
        <div class="label" title="${d.n}">${d.n}</div>
      </div>`;
    });
    html += '</div></div>';
  });
  if (!html) html = '<p style="color:#888;padding:20px;">No screenshots match the current filter.</p>';
  gallery.innerHTML = html;
}

function setFilter(f) {
  currentFilter = f;
  document.querySelectorAll('.filters button').forEach(b => b.classList.remove('active'));
  render();
}
function filterAll() { setFilter('all'); event.target.classList.add('active'); }
function filterPassed() { setFilter('passed'); event.target.classList.add('active'); }
function filterFailed() { setFilter('failed'); event.target.classList.add('active'); }
function filterSearch() { searchTerm = document.getElementById('search').value.toLowerCase(); render(); }

// ── Lightbox ────────────────────────────────────────────────────────────────
function openLightbox(idx) {
  const d = DATA[idx];
  const lb = document.getElementById('lightbox');
  const content = document.getElementById('lb-content');
  const caption = document.getElementById('lb-caption');
  lb.classList.add('open');
  caption.textContent = `${d.c} / ${d.n}`;

  if (d.s === 'failed' && d.d) {
    content.innerHTML = `<div class="diff-row">
      <div><img src="data:image/png;base64,${d.e}" /><div class="diff-label">Expected</div></div>
      <div><img src="data:image/png;base64,${d.r}" /><div class="diff-label">Actual</div></div>
      <div><img src="data:image/png;base64,${d.d}" /><div class="diff-label">Diff</div></div>
    </div>`;
  } else {
    content.innerHTML = `<img src="data:image/png;base64,${d.r}" />`;
  }
}
function closeLightbox(e) {
  if (e.target.classList.contains('overlay') || e.target.classList.contains('close')) {
    document.getElementById('lightbox').classList.remove('open');
  }
}
document.addEventListener('keydown', e => {
  if (e.key === 'Escape') document.getElementById('lightbox').classList.remove('open');
});

render();
</script>
</body>
</html>
GALLERY_TAIL

echo "Gallery written to ${GALLERY_OUT}" >&2
echo "Step summary size: $(wc -c < "$SUMMARY" | tr -d ' ') bytes" >&2
echo "Gallery size: $(wc -c < "$GALLERY_OUT" | tr -d ' ') bytes" >&2
