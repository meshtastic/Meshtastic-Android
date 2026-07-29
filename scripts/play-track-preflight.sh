#!/usr/bin/env bash
# Preflight for promote.yml: check whether VERSION_CODE is already live on the
# target Play track, so a re-dispatched promotion can no-op instead of creating
# a redundant Play submission (each submission cancels and restarts any review
# already in flight — see the Jul 2026 v2.8.0 submission-churn incident).
#
# Usage: play-track-preflight.sh <service-account.json> <package> <track> <version_code>
# Writes already_on_track=true|false to $GITHUB_OUTPUT (or stdout when unset).
#
# Fail-open by design: on any API/auth error it reports already_on_track=false
# and exits 0, so a preflight hiccup never blocks a legitimate promotion —
# `fastlane supply` uses the same credentials and will surface real failures.

set -u

KEY="${1:?usage: play-track-preflight.sh <service-account.json> <package> <track> <version_code>}"
PKG="${2:?missing package}"
TRACK="${3:?missing track}"
VERSION_CODE="${4:?missing version_code}"
SCOPE="https://www.googleapis.com/auth/androidpublisher"
OUT="${GITHUB_OUTPUT:-/dev/stdout}"

emit() {
  echo "already_on_track=$1" >> "$OUT"
  exit 0
}

fail_open() {
  echo "::warning::Play preflight failed ($1) — proceeding with promotion (fail-open)."
  emit false
}

TMP="$(mktemp -d)" || fail_open "mktemp"
trap 'rm -rf "$TMP"' EXIT

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

CLIENT_EMAIL=$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["client_email"])' "$KEY" 2>/dev/null) \
  || fail_open "unreadable service-account key"
python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["private_key"])' "$KEY" > "$TMP/key.pem" 2>/dev/null \
  || fail_open "key missing private_key"

NOW=$(date +%s)
HEADER=$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)
CLAIM=$(printf '{"iss":"%s","scope":"%s","aud":"https://oauth2.googleapis.com/token","iat":%s,"exp":%s}' \
  "$CLIENT_EMAIL" "$SCOPE" "$NOW" "$((NOW + 600))" | b64url)
SIG=$(printf '%s.%s' "$HEADER" "$CLAIM" | openssl dgst -sha256 -sign "$TMP/key.pem" 2>/dev/null | b64url) \
  || fail_open "JWT signing"

TOKEN=$(curl -sf -X POST https://oauth2.googleapis.com/token \
  --data-urlencode 'grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer' \
  --data-urlencode "assertion=${HEADER}.${CLAIM}.${SIG}" 2>/dev/null \
  | python3 -c 'import json,sys;print(json.load(sys.stdin).get("access_token",""))' 2>/dev/null)
[ -n "$TOKEN" ] || fail_open "token exchange"

API="https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PKG"
EDIT=$(curl -sf -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Length: 0' "$API/edits" 2>/dev/null \
  | python3 -c 'import json,sys;print(json.load(sys.stdin).get("id",""))' 2>/dev/null)
[ -n "$EDIT" ] || fail_open "edit insert"

# Read-only: GET the track, then discard the edit without committing.
TRACK_JSON=$(curl -sf -H "Authorization: Bearer $TOKEN" \
  "$API/edits/$EDIT/tracks/$TRACK" 2>/dev/null)
curl -s -X DELETE -H "Authorization: Bearer $TOKEN" "$API/edits/$EDIT" >/dev/null 2>&1

[ -n "$TRACK_JSON" ] || fail_open "track read"

RESULT=$(printf '%s' "$TRACK_JSON" | python3 -c '
import json, sys
target = int(sys.argv[1])
track = json.load(sys.stdin)
for r in track.get("releases", []):
    # Only live release states count — a draft or halted release still needs
    # the promotion to run.
    if r.get("status") in ("completed", "inProgress") \
            and target in [int(c) for c in r.get("versionCodes", [])]:
        print("true")
        break
else:
    print("false")
' "$VERSION_CODE" 2>/dev/null) || fail_open "track parse"

if [ "$RESULT" = "true" ]; then
  echo "versionCode $VERSION_CODE is already live on track '$TRACK' — skipping promotion (no new Play submission)."
else
  echo "versionCode $VERSION_CODE not live on track '$TRACK' — promotion will proceed."
fi
emit "$RESULT"
