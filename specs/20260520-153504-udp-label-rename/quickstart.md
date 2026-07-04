# Quickstart: UDP Label Rename

**Feature**: UDP Label Rename  
**Date**: 2025-05-20

## Prerequisites

- JDK 21 installed
- `ANDROID_HOME` set
- Proto submodule initialized (`git submodule update --init`)
- Python 3 available (for sort-strings script)

## Implementation Steps

### Step 1: Edit the string resource

**File**: `core/resources/src/commonMain/composeResources/values/strings.xml`  
**Line**: 1304

Change:
```xml
<string name="udp_enabled">Enabled</string>
```

To:
```xml
<string name="udp_enabled">UDP broadcasting</string>
```

### Step 2: Run the string sort script

```bash
python3 scripts/sort-strings.py
```

This re-sorts all string entries alphabetically and regenerates `strings-index.txt`.

### Step 3: Verify the build

```bash
./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests
```

### Step 4: Confirm CI (after push)

```bash
gh pr checks <PR_NUMBER>
# or
gh run list --branch jamesarich/issue-5546-alignment-add-missing-network-config-fi-fbde6f --limit 5
```

## Verification Checklist

- [ ] `udp_enabled` value reads "UDP broadcasting" in strings.xml
- [ ] `strings-index.txt` is regenerated (check git diff)
- [ ] `spotlessCheck` passes
- [ ] `detekt` passes
- [ ] `assembleDebug` succeeds
- [ ] `test` and `allTests` pass
- [ ] No unintended changes in git diff (only strings.xml and strings-index.txt modified)
