/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.core.takserver

/**
 * Removes bloat elements from the `<detail>` content of a CoT event before it is
 * stuffed into a [org.meshtastic.proto.TAKPacketV2] `raw_detail` field for mesh
 * transmission.
 *
 * # Why this exists
 *
 * A LoRa mesh packet has a hard payload limit of
 * [org.meshtastic.proto.Constants.DATA_PAYLOAD_LEN] = 233 bytes for the entire encoded
 * `Data` proto (portnum + payload + reply_id + emoji). Subtracting the wrapper
 * overhead leaves roughly **~225 bytes** for the TAK wire payload, and the wire payload
 * itself is `[1 byte dict-id flag][zstd-compressed TAKPacketV2 protobuf]`.
 *
 * ATAK emits CoT events with rich visual metadata that is **never useful over a mesh**:
 * icon set paths, ARGB colors, shape geometry, archive flags, file references, etc.
 * A typical `u-d-c-c` (user-drawn circle) event from ATAK is **800+ bytes of XML**, of
 * which maybe 80 bytes are actually meaningful to a receiving node. Even with
 * dictionary compression, the full payload overflows the MTU.
 *
 * This stripper deletes elements the receiving node can synthesize or ignore, leaving
 * only the minimum needed to rebuild a usable `<event>` on the other side: who sent
 * it, where they are, what team/role they're on, battery status, chat content, and
 * the high-level CoT type (which rides separately on [TAKPacketV2.cot_type_id] /
 * [TAKPacketV2.cot_type_str]).
 *
 * # What gets dropped
 *
 * **Cosmetic / rendering-only** (pure visual, no situational awareness value):
 * - `<color .../>` — ARGB stroke/fill colors
 * - `<strokeColor .../>`, `<strokeWeight .../>`, `<fillColor .../>` — shape styling
 * - `<labels_on .../>` — label visibility toggle
 * - `<usericon .../>` — icon set path (`COT_MAPPING_2525B/...`)
 * - `<model .../>` — 3D model reference
 *
 * **Geometric detail** (we keep lat/lon on the event; shape primitives are too big):
 * - `<shape>...</shape>` — ellipse/polyline/polygon geometry
 * - `<height .../>`, `<height_unit .../>` — rendering hints
 *
 * **Resource references** (useless without the resource being reachable):
 * - `<fileshare .../>` — file transfer references
 * - `<__video .../>` — video stream URL
 *
 * **Flags and redundant metadata**:
 * - `<archive/>` — "save to archive" flag
 * - `<precisionlocation .../>` — redundant with the event's `<point>` attributes
 * - `<tog .../>` — rectangle "toggle" UI state flag
 * - `<_flow-tags_ .../>` — TAK Server routing metadata (server-to-server, not needed on mesh)
 *
 * # What gets preserved
 *
 * Anything the stripper doesn't explicitly match is passed through untouched. That
 * includes all of the structured elements that the regular [CoTXmlParser] understands
 * (contact, __group, status, track, remarks, __chat, chatgrp, link, uid,
 * __serverdestination) plus any unknown extensions — better to over-preserve than
 * silently drop something the receiving ATAK actually needs.
 *
 * # Whitespace
 *
 * All inter-element whitespace and indentation is collapsed. Whitespace inside text
 * nodes (e.g. `<remarks>hello world</remarks>`) is preserved.
 *
 * # Not a real XML parser
 *
 * This is intentionally string/regex based, not DOM. The input is a small, well-formed
 * fragment produced by ATAK's serializer, so a full parser is overkill — and we want
 * this to be dependency-free so it can run on every KMP target without pulling in
 * xmlutil for a one-off job. If ATAK starts emitting namespaced elements or embedded
 * CDATA that tangles with these patterns, the stripper will leave them alone rather
 * than corrupt the output, which is the safer failure mode.
 */
internal object CoTDetailStripper {

    /**
     * Element names whose entire subtree (or self-closing tag) is removed.
     *
     * Order matters only for documentation. Each entry is tried against both the
     * self-closing form `<name .../>` and the paired form `<name ...>...</name>`.
     */
    private val STRIPPED_ELEMENTS = listOf(
        // Cosmetic / rendering
        "color",
        "strokeColor",
        "strokeWeight",
        "fillColor",
        "labels_on",
        "usericon",
        "model",
        // Geometric
        "shape",
        "height",
        "height_unit",
        // Resource refs
        "fileshare",
        "__video",
        // Flags / redundant
        "archive",
        "precisionlocation",
        // Rectangle/polyline "toggle" UI flag, and TAK Server routing metadata.
        // The underscore-prefixed element names are legal XML identifiers ATAK uses
        // for internal state that receiving meshtastic nodes have no use for.
        "tog",
        "_flow-tags_",
    )

    /**
     * Pre-compiled regex list: for each stripped element, one pattern that matches
     * either a self-closing tag or a paired open/close tag (non-greedy content).
     *
     * `[^>]*?` inside the open tag tolerates attribute quoting with both single and
     * double quotes but bails if it encounters a `>` (so it won't accidentally swallow
     * unrelated content).
     *
     * The leading `(?s)` inline flag is the KMP-portable equivalent of
     * `RegexOption.DOT_MATCHES_ALL` — it lets `.` match newlines so a multi-line
     * `<shape>...</shape>` subtree is captured in one pass. `RegexOption.DOT_MATCHES_ALL`
     * itself is JVM-only and breaks the Kotlin/Native build.
     */
    private val STRIPPED_ELEMENT_PATTERNS: List<Regex> =
        STRIPPED_ELEMENTS.map { name ->
            // Escape the name in case it contains regex metacharacters (e.g. __video).
            val escaped = Regex.escape(name)
            // Matches:
            //   <name/>
            //   <name attr="..."/>
            //   <name attr='...'>...content...</name>
            Regex("""(?s)<$escaped(?:\s[^>]*?)?/>|<$escaped(?:\s[^>]*?)?>.*?</$escaped>""")
        }

    /** Matches whitespace between tags: `>   \n   <` → `><`. */
    private val INTER_TAG_WHITESPACE = Regex(""">\s+<""")

    /** Collapse leading / trailing whitespace across the whole fragment. */
    private val EDGE_WHITESPACE = Regex("""^\s+|\s+$""")

    /**
     * Strip bloat elements and normalize whitespace on an inner `<detail>` fragment.
     *
     * The input is assumed to be the concatenated children of `<detail>` — i.e., what
     * [CoTXmlParser.extractDetailInnerXml] returns. It is NOT the full `<event>` or
     * the `<detail>` wrapper itself.
     *
     * Returns an empty string if every element was stripped (so callers can treat
     * "empty" and "nothing worth sending" uniformly).
     */
    fun strip(detailInnerXml: String): String {
        if (detailInnerXml.isEmpty()) return ""
        var result = detailInnerXml
        for (pattern in STRIPPED_ELEMENT_PATTERNS) {
            result = pattern.replace(result, "")
        }
        // Collapse whitespace between remaining tags. Preserves whitespace inside
        // text nodes (e.g. <remarks>hello world</remarks>) because that whitespace
        // isn't bracketed by '>' and '<'.
        result = INTER_TAG_WHITESPACE.replace(result, "><")
        result = EDGE_WHITESPACE.replace(result, "")
        return result
    }
}
