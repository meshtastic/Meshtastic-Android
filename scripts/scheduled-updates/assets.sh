#!/usr/bin/env bash
# The API assets the scheduled-updates PR carries, one row each in asset().
#   assets.sh fetch <key>  refreshes androidApp/src/main/assets/<file>; writes status and detail to $GITHUB_OUTPUT
#   assets.sh body         writes the PR body to $GITHUB_OUTPUT from the <KEY>_STATUS and <KEY>_DETAIL environment
set -e

assets_dir=androidApp/src/main/assets
keys=(firmware hardware event_firmware device_links bootloader_quirks maintenance_uf2)

# `compare` is the jq filter both copies are diffed through and `write` the one the new copy is written through
# (empty copies it as served). Each `guards` line is a jq count that must not be 0, so a degraded response never
# replaces the bundled seed.
asset() {
  guards="" guard_warning="" guard_detail=""
  case "$1" in
    firmware)
      file=firmware_releases.json
      url=https://api.meshtastic.org/github/firmware/list
      fetching="firmware releases" api="firmware API" skipping="firmware update" subject="firmware list"
      # The API lists every open firmware PR, which turns over several times a day; the app never reads the field
      # (see NetworkFirmwareReleases).
      compare='del(.pullRequests)' write='del(.pullRequests)'
      ;;
    hardware)
      file=device_hardware.json
      url=https://api.meshtastic.org/resource/deviceHardware
      fetching="device hardware data" api="hardware API" skipping="hardware update" subject="hardware list"
      compare=. write=""
      ;;
    event_firmware)
      file=event_firmware.json
      url=https://api.meshtastic.org/resource/eventFirmware
      fetching="event firmware metadata" api="event firmware API" skipping="event firmware update"
      subject="event firmware metadata"
      compare=. write=""
      ;;
    device_links)
      file=device_links.json
      url=https://api.meshtastic.org/resource/deviceLinks
      fetching="device links" api="device links API" skipping="device links update" subject="device links"
      # The envelope carries a server-set generatedAt that changes on every response.
      compare=.links write=.
      guards='if (.links | type) == "array" then (.links | length) else 0 end'
      guard_warning="Device links API returned no links. Skipping to protect the bundled seed."
      guard_detail="empty links array from device links API"
      ;;
    bootloader_quirks)
      file=device_bootloader_ota_quirks.json
      url=https://api.meshtastic.org/resource/bootloaderOtaQuirks
      fetching="bootloader OTA quirks" api="bootloader quirks API" skipping="quirks update" subject="bootloader quirks"
      compare=. write=.
      # softDeviceVariants gates a destructive flash, so it fails closed.
      guards='if (.softDeviceVariants | type) == "array" then (.softDeviceVariants | length) else 0 end'
      guard_warning="Bootloader quirks API returned no softDeviceVariants. Skipping to protect the bundled seed."
      guard_detail="empty softDeviceVariants from bootloader quirks API"
      ;;
    maintenance_uf2)
      file=maintenance_uf2.json
      url=https://api.meshtastic.org/resource/maintenanceUf2
      fetching="maintenance UF2 manifest" api="maintenance UF2 API" skipping="manifest update"
      subject="maintenance UF2 manifest"
      compare=. write=.
      # Its digest-pinned images gate destructive maintenance flashes, and without erase.nrf52Bootloader every
      # offline install loses the bootloader-driven erase path.
      guards='if (.otafixByBoardId | type) == "object" then (.otafixByBoardId | length) else 0 end
if (.erase | type) == "object" and (.erase.nrf52Bootloader | type) == "object" then 1 else 0 end'
      guard_warning="Maintenance UF2 API response is missing the erase set (incl. erase.nrf52Bootloader) or board map. Skipping to protect the bundled seed."
      guard_detail="degraded maintenance UF2 manifest (no erase set, no erase.nrf52Bootloader, or empty board map)"
      ;;
    *)
      echo "unknown asset: $1" >&2
      exit 2
      ;;
  esac
}

output() {
  echo "status=$1" >> "$GITHUB_OUTPUT"
  if [ -n "$2" ]; then
    echo "detail=$2" >> "$GITHUB_OUTPUT"
  fi
}

guard_tripped() {
  local expr count
  while IFS= read -r expr; do
    [ -n "$expr" ] || continue
    count=$(jq -r "$expr" "$tmp" 2>/dev/null || echo 0)
    if [ "$count" -eq 0 ]; then
      return 0
    fi
  done <<< "$guards"
  return 1
}

fetch() {
  asset "$1"
  path=$assets_dir/$file
  tmp=/tmp/new_$file

  echo "Fetching latest $fetching..."
  http_code=$(curl -s --max-time 90 -o "$tmp" -w '%{http_code}' "$url" || true)
  http_code="${http_code:-0}"

  if [ "$http_code" -lt 200 ] || [ "$http_code" -ge 300 ]; then
    echo "::warning::${api^} returned HTTP $http_code. Skipping $skipping."
    output error "HTTP $http_code from $api"
  elif ! jq empty "$tmp" 2>/dev/null; then
    echo "::warning::${api^} returned invalid JSON data. Skipping $skipping."
    output error "Invalid JSON response from $api"
  elif guard_tripped; then
    echo "::warning::$guard_warning"
    output error "$guard_detail"
  elif [ ! -f "$path" ] || ! jq --sort-keys "$compare" "$tmp" | diff -q - <(jq --sort-keys "$compare" "$path"); then
    echo "Changes detected in $subject or local file missing. Updating $path."
    if [ -n "$write" ]; then
      jq "$write" "$tmp" > "$path"
    else
      cp "$tmp" "$path"
    fi
    output updated
  else
    echo "No changes detected in $subject."
    output unchanged
  fi
}

body() {
  local key status_var detail_var status detail body
  body="This PR includes automated updates from the scheduled workflow:"
  body+=$'\n'

  for key in "${keys[@]}"; do
    asset "$key"
    status_var=${key^^}_STATUS detail_var=${key^^}_DETAIL
    status=${!status_var} detail=${!detail_var}
    case "$status" in
      updated)   body+=$'\n'"- ✅ \`$file\` updated from the Meshtastic API." ;;
      unchanged) body+=$'\n'"- ✔️ \`$file\` checked and unchanged." ;;
      error)     body+=$'\n'"- ⚠️ \`$file\` skipped ($detail)." ;;
      *)         body+=$'\n'"- ❓ \`$file\` status unknown." ;;
    esac
  done

  case "$OBTAINIUM_STATUS" in
    updated)   body+=$'\n'"- ✅ Obtainium deep links and import files regenerated (channel status changed)." ;;
    unchanged) body+=$'\n'"- ✔️ Obtainium configs checked against the live releases and unchanged." ;;
    error)     body+=$'\n'"- ⚠️ Obtainium configs skipped ($OBTAINIUM_DETAIL)." ;;
    *)         body+=$'\n'"- ❓ Obtainium configs status unknown." ;;
  esac

  if [[ "$SCHEMA_PIN_CHANGED" == "true" ]]; then
    case "$SCHEMA_STRINGS_STATUS" in
      updated) body+=$'\n'"- ✅ \`schema_strings.xml\` regenerated for protobufs \`$SCHEMA_PIN_CATALOG\` ($SCHEMA_STRINGS_DETAIL)." ;;
      error)   body+=$'\n'"- ⚠️ \`schema_strings.xml\` skipped ($SCHEMA_STRINGS_DETAIL)." ;;
      *)       body+=$'\n'"- ❓ \`schema_strings.xml\` status unknown." ;;
    esac
  else
    body+=$'\n'"- ✔️ \`schema_strings.xml\` already reflects protobufs \`$SCHEMA_PIN_CATALOG\`."
  fi

  body+=$'\n'"- Source strings were uploaded to Crowdin."
  body+=$'\n'"- Latest translations were downloaded from Crowdin (if available)."
  body+=$'\n'
  body+=$'\n'"Please review the changes."

  {
    echo "content<<PREOF"
    echo "$body"
    echo "PREOF"
  } >> "$GITHUB_OUTPUT"
}

case "$1" in
  fetch) fetch "$2" ;;
  body) body ;;
  *)
    echo "usage: $0 fetch <${keys[*]}> | body" >&2
    exit 2
    ;;
esac
