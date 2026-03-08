# Local display names for nodes

Users can set a **local display name** for any node. That name is shown in the app instead of the device’s long/short name, and is stored only on the device (not sent over the mesh).

---

## Summary

<table>
<tr><td><strong>Storage</strong></td><td><code>NodeDisplayNamePrefs</code> (DataStore), keyed by <strong>node number</strong></td></tr>
<tr><td><strong>Scope</strong></td><td>Node list, node detail, detail screen title</td></tr>
<tr><td><strong>Edit entry points</strong></td><td>Long-press node → “Set display name”; Node detail → “Display name” row</td></tr>
</table>

---

## UI

<ul>
<li><strong>Node list</strong> – Each row shows the custom display name when set, otherwise the device long name.</li>
<li><strong>Long-press menu</strong> – “Set display name” (Edit icon) opens a dialog to set or clear the name.</li>
<li><strong>Node detail</strong> – Title shows the display name; a “Display name” row in the Details card is tappable to edit.</li>
<li><strong>Edit dialog</strong> – Text field (“Local name for this node”), <strong>Save</strong>, <strong>Cancel</strong>, and <strong>Clear display name</strong>.</li>
</ul>

---

## Technical details

- **Key:** Display names are stored and looked up by <strong>node number</strong> (<code>num</code>), the unique node ID.
- **Persistence:** Single DataStore preference; map encoded as <code>num\u0001name\u0002…</code> with escaping for names containing delimiters.
- **New strings:</strong> <code>set_display_name</code>, <code>display_name</code>, <code>display_name_hint</code>, <code>clear_display_name</code> (default locale only in this PR).

---

## Testing

- Unit tests: <code>core/prefs/…/NodeDisplayNamePrefsTest.kt</code> (defaults, set/get, clear, multiple nodes, trim).
- Run: <code>./gradlew :core:prefs:testDebugUnitTest</code>

---

## Checklist

- [x] Display name keyed by node number only
- [x] Shown in list and detail; editable from list context menu and detail row
- [x] Clear display name restores device name
- [x] No change to device identity or protocol; local-only
