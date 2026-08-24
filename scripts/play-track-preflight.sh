#!/usr/bin/env bash
# Play track probe for promote.yml, two modes sharing one read path:
#
# preflight (default): is VERSION_CODE already live on the target track? Lets a
# re-dispatched promotion no-op instead of creating a redundant Play submission
# (each submission cancels and restarts any review already in flight — see the
# Jul 2026 v2.8.0 submission-churn incident). Writes already_on_track=true|false
# to $GITHUB_OUTPUT (or stdout when unset). Fail-open: on any API/auth error it
# reports false and exits 0 — `fastlane supply` uses the same credentials and
# will surface real failures.
#
# verify: after supply, is VERSION_CODE actually live on the destination track?
# supply can exit 0 without promoting it (see promote.yml), so this mode is
# fail-closed: it retries transient errors, then exits 1 unless the version
# code is confirmed on the track.
#
# Usage: play-track-preflight.sh <service-account.json> <package> <track> <version_code> [preflight|verify]

set -u

KEY="${1:?usage: play-track-preflight.sh <service-account.json> <package> <track> <version_code> [preflight|verify]}"
PKG="${2:?missing package}"
TRACK="${3:?missing track}"
VERSION_CODE="${4:?missing version_code}"
MODE="${5:-preflight}"
SCOPE="https://www.googleapis.com/auth/androidpublisher"
OUT="${GITHUB_OUTPUT:-/dev/stdout}"

case "$MODE" in
  preflight|verify) ;;
  *) echo "::error::unknown mode '$MODE' (expected preflight or verify)"; exit 2 ;;
esac

emit() {
  echo "already_on_track=$1" >> "$OUT"
  exit 0
}

fail_open() {
  echo "::warning::Play preflight failed ($1) — proceeding with promotion (fail-open)."
  emit false
}

TMP="$(mktemp -d)" || { [ "$MODE" = "verify" ] && { echo "::error::mktemp failed"; exit 1; }; fail_open "mktemp"; }
trap 'rm -rf "$TMP"' EXIT

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

# Reads the track and sets RESULT=true|false (is VERSION_CODE live on it, in a
# completed or inProgress release). Returns non-zero with PROBE_ERR set on any
# auth/API failure, deciding nothing.
probe() {
  PROBE_ERR=""
  local client_email now header claim sig token edit track_json

  client_email=$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["client_email"])' "$KEY" 2>/dev/null) \
    || { PROBE_ERR="unreadable service-account key"; return 1; }
  python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["private_key"])' "$KEY" > "$TMP/key.pem" 2>/dev/null \
    || { PROBE_ERR="key missing private_key"; return 1; }

  now=$(date +%s)
  header=$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)
  claim=$(printf '{"iss":"%s","scope":"%s","aud":"https://oauth2.googleapis.com/token","iat":%s,"exp":%s}' \
    "$client_email" "$SCOPE" "$now" "$((now + 600))" | b64url)
  sig=$(printf '%s.%s' "$header" "$claim" | openssl dgst -sha256 -sign "$TMP/key.pem" 2>/dev/null | b64url) \
    || { PROBE_ERR="JWT signing"; return 1; }

  token=$(curl -sf -X POST https://oauth2.googleapis.com/token \
    --data-urlencode 'grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer' \
    --data-urlencode "assertion=${header}.${claim}.${sig}" 2>/dev/null \
    | python3 -c 'import json,sys;print(json.load(sys.stdin).get("access_token",""))' 2>/dev/null)
  [ -n "$token" ] || { PROBE_ERR="token exchange"; return 1; }

  local api="https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PKG"
  edit=$(curl -sf -X POST -H "Authorization: Bearer $token" -H 'Content-Length: 0' "$api/edits" 2>/dev/null \
    | python3 -c 'import json,sys;print(json.load(sys.stdin).get("id",""))' 2>/dev/null)
  [ -n "$edit" ] || { PROBE_ERR="edit insert"; return 1; }

  # Read-only: GET the track, then discard the edit without committing.
  track_json=$(curl -sf -H "Authorization: Bearer $token" \
    "$api/edits/$edit/tracks/$TRACK" 2>/dev/null)
  curl -s -X DELETE -H "Authorization: Bearer $token" "$api/edits/$edit" >/dev/null 2>&1
  [ -n "$track_json" ] || { PROBE_ERR="track read"; return 1; }

  RESULT=$(printf '%s' "$track_json" | python3 -c '
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
' "$VERSION_CODE" 2>/dev/null) || { PROBE_ERR="track parse"; return 1; }
}

if [ "$MODE" = "preflight" ]; then
  probe || fail_open "$PROBE_ERR"
  if [ "$RESULT" = "true" ]; then
    echo "versionCode $VERSION_CODE is already live on track '$TRACK' — skipping promotion (no new Play submission)."
  else
    echo "versionCode $VERSION_CODE not live on track '$TRACK' — promotion will proceed."
  fi
  emit "$RESULT"
fi

# verify mode: fail-closed. Retries cover transient API errors and any lag
# between supply's edit commit and the track read reflecting it.
ATTEMPTS=4
for i in $(seq 1 "$ATTEMPTS"); do
  if probe; then
    if [ "$RESULT" = "true" ]; then
      echo "versionCode $VERSION_CODE confirmed live on track '$TRACK'."
      exit 0
    fi
    PROBE_ERR="track '$TRACK' does not contain versionCode $VERSION_CODE"
  fi
  if [ "$i" -lt "$ATTEMPTS" ]; then
    echo "Verify attempt $i/$ATTEMPTS failed ($PROBE_ERR) — retrying in 15s."
    sleep 15
  fi
done
echo "::error::Post-promotion verify failed: $PROBE_ERR. fastlane supply exited 0 but the promotion did not land versionCode $VERSION_CODE on track '$TRACK'."
exit 1
