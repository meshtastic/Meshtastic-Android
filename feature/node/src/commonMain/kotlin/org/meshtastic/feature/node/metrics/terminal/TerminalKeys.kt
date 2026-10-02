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
package org.meshtastic.feature.node.metrics.terminal

/** Keys a soft keyboard lacks and a shell needs. */
enum class TerminalKey {
    ESCAPE,
    TAB,
    UP,
    DOWN,
    LEFT,
    RIGHT,
    HOME,
    END,
    PAGE_UP,
    PAGE_DOWN,
    DELETE,
}

/** Sticky modifiers from the extra-keys row: each applies to the next key, then releases unless locked. */
data class Modifiers(val ctrl: ModifierState = ModifierState.OFF, val alt: ModifierState = ModifierState.OFF) {
    val isEmpty: Boolean
        get() = ctrl == ModifierState.OFF && alt == ModifierState.OFF

    /** What remains after a key has used them: one-shot modifiers release, locked ones stay. */
    fun consumed(): Modifiers = Modifiers(ctrl.afterUse(), alt.afterUse())
}

enum class ModifierState {
    OFF,
    ONCE,
    LOCKED,
    ;

    /** A tap cycles off -> once -> locked -> off, as Termux and Blink do. */
    fun next(): ModifierState = when (this) {
        OFF -> ONCE
        ONCE -> LOCKED
        LOCKED -> OFF
    }

    fun afterUse(): ModifierState = if (this == ONCE) OFF else this
}

private const val ESC = "\u001b"
private const val CTRL_MASK = 0x1f
private const val DEL = '\u007f'

/** What one edit to the keyboard sink typed: [deleted] backspaces, then [inserted] text. */
internal data class SinkEdit(val deleted: Int, val inserted: String)

/** The edit that turned [before] into [after]: everything past their common prefix is deleted, then inserted. */
internal fun sinkEdit(before: CharSequence, after: CharSequence): SinkEdit {
    val shared = minOf(before.length, after.length)
    var prefix = 0
    while (prefix < shared && before[prefix] == after[prefix]) prefix++
    return SinkEdit(deleted = before.length - prefix, inserted = after.subSequence(prefix, after.length).toString())
}

/** The bytes a VT100-family terminal sends for a key, honouring DECCKM for the cursor keys. */
internal fun TerminalKey.sequence(applicationCursorKeys: Boolean): String {
    val cursorPrefix = if (applicationCursorKeys) "${ESC}O" else "$ESC["
    return when (this) {
        TerminalKey.ESCAPE -> ESC
        TerminalKey.TAB -> "\t"
        TerminalKey.UP -> "${cursorPrefix}A"
        TerminalKey.DOWN -> "${cursorPrefix}B"
        TerminalKey.RIGHT -> "${cursorPrefix}C"
        TerminalKey.LEFT -> "${cursorPrefix}D"
        TerminalKey.HOME -> "${cursorPrefix}H"
        TerminalKey.END -> "${cursorPrefix}F"
        TerminalKey.PAGE_UP -> "$ESC[5~"
        TerminalKey.PAGE_DOWN -> "$ESC[6~"
        TerminalKey.DELETE -> "$ESC[3~"
    }
}

/**
 * A typed character with [modifiers] applied: Ctrl maps letters and `@[\]^_?` to their C0 codes (`?` to DEL), and Alt
 * prefixes ESC, as xterm's default `metaSendsEscape` does.
 */
internal fun Char.withModifiers(modifiers: Modifiers): String {
    var c = this
    if (modifiers.ctrl != ModifierState.OFF) {
        c =
            when (val upper = c.uppercaseChar()) {
                in 'A'..'Z',
                '@',
                '[',
                '\\',
                ']',
                '^',
                '_',
                -> (upper.code and CTRL_MASK).toChar()

                '?' -> DEL

                ' ' -> '\u0000'

                else -> c
            }
    }
    return if (modifiers.alt != ModifierState.OFF) "$ESC$c" else c.toString()
}
