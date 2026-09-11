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
package org.meshtastic.core.model

import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.NodeSecurityIndicator.Companion.resolve
import org.meshtastic.core.model.NodeSecurityIndicator.Companion.signsBroadcasts
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NodeSecurityIndicatorTest {

    @Test
    fun `2_8 and newer report signing support - older and unknown do not`() {
        // DeviceMetadata truncates to two components, so a stored "2.8" has to read as 2.8.
        assertTrue(signsBroadcasts("2.8"))
        assertTrue(signsBroadcasts("2.8.0"))
        assertTrue(signsBroadcasts("2.8.1"))
        assertTrue(signsBroadcasts("2.10.0"))
        assertFalse(signsBroadcasts("2.7.26"))
        assertFalse(signsBroadcasts("2.5.14"))
        assertFalse(signsBroadcasts(null))
        assertFalse(signsBroadcasts(""))
        assertFalse(signsBroadcasts("unknown"))
    }

    @Test
    fun `2_8 nodes show verification state instead of the locks`() {
        assertEquals(
            NodeSecurityIndicator.SIGNED_NODE,
            resolve(firmwareVersion = "2.8.0", hasPublicKey = true, mismatchKey = false),
        )
        assertEquals(
            NodeSecurityIndicator.SIGNED_NODE,
            resolve(firmwareVersion = "2.8.0", hasPublicKey = false, mismatchKey = false),
        )
    }

    @Test
    fun `a heard signature shows the signed glyph even when the version is unknown`() {
        assertEquals(
            NodeSecurityIndicator.SIGNED_NODE,
            resolve(firmwareVersion = null, hasPublicKey = true, mismatchKey = false, signed = true),
        )
        assertEquals(
            NodeSecurityIndicator.SIGNED_NODE,
            resolve(firmwareVersion = "2.7.26", hasPublicKey = true, mismatchKey = false, signed = true),
        )
        // Never heard signing and no 2.8 report: the locks still describe it honestly.
        assertEquals(
            NodeSecurityIndicator.PUBLIC_KEY,
            resolve(firmwareVersion = "2.7.26", hasPublicKey = true, mismatchKey = false, signed = false),
        )
    }

    @Test
    fun `older and unversioned nodes keep the locks`() {
        assertEquals(
            NodeSecurityIndicator.PUBLIC_KEY,
            resolve(firmwareVersion = "2.7.26", hasPublicKey = true, mismatchKey = false),
        )
        assertEquals(
            NodeSecurityIndicator.NO_PUBLIC_KEY,
            resolve(firmwareVersion = "2.7.26", hasPublicKey = false, mismatchKey = false),
        )
        assertEquals(
            NodeSecurityIndicator.PUBLIC_KEY,
            resolve(firmwareVersion = null, hasPublicKey = true, mismatchKey = false),
        )
        assertEquals(
            NodeSecurityIndicator.NO_PUBLIC_KEY,
            resolve(firmwareVersion = null, hasPublicKey = false, mismatchKey = false),
        )
    }

    @Test
    fun `a key mismatch is a warning at any version - even for a verified contact`() {
        assertEquals(
            NodeSecurityIndicator.KEY_MISMATCH,
            resolve(firmwareVersion = "2.8.0", hasPublicKey = true, mismatchKey = true),
        )
        assertEquals(
            NodeSecurityIndicator.KEY_MISMATCH,
            resolve(firmwareVersion = "2.7.26", hasPublicKey = true, mismatchKey = true),
        )
        assertEquals(
            NodeSecurityIndicator.KEY_MISMATCH,
            resolve(firmwareVersion = "2.8.0", hasPublicKey = true, mismatchKey = true, verified = true),
        )
        assertEquals(
            NodeSecurityIndicator.KEY_MISMATCH,
            resolve(firmwareVersion = "2.8.0", hasPublicKey = true, mismatchKey = true, signed = true),
        )
    }

    @Test
    fun `the connected radio reads as verified at any version`() {
        assertEquals(
            NodeSecurityIndicator.VERIFIED_CONTACT,
            resolve(firmwareVersion = "2.8.0", hasPublicKey = false, mismatchKey = false, isOwnNode = true),
        )
        // The user holds their own radio's key whatever it runs, so this does not depend on version.
        assertEquals(
            NodeSecurityIndicator.VERIFIED_CONTACT,
            resolve(firmwareVersion = "2.7.26", hasPublicKey = true, mismatchKey = false, isOwnNode = true),
        )
    }

    @Test
    fun `an in-person verified contact outranks signing at any version`() {
        assertEquals(
            NodeSecurityIndicator.VERIFIED_CONTACT,
            resolve(firmwareVersion = "2.8.0", hasPublicKey = true, mismatchKey = false, verified = true),
        )
        assertEquals(
            NodeSecurityIndicator.VERIFIED_CONTACT,
            resolve(
                firmwareVersion = "2.8.0",
                hasPublicKey = true,
                mismatchKey = false,
                verified = true,
                signed = true,
            ),
        )
        // Meeting someone in person is the same evidence whatever their radio runs.
        assertEquals(
            NodeSecurityIndicator.VERIFIED_CONTACT,
            resolve(firmwareVersion = "2.7.26", hasPublicKey = true, mismatchKey = false, verified = true),
        )
    }

    @Test
    fun `of reads the same decision off a Node`() {
        val key = ByteArray(32) { 1 }.toByteString()
        val signedOn28 =
            Node(num = 1, metadata = DeviceMetadata(firmware_version = "2.8.0"), user = User(public_key = key))
        assertEquals(NodeSecurityIndicator.SIGNED_NODE, NodeSecurityIndicator.of(signedOn28))
        assertEquals(NodeSecurityIndicator.VERIFIED_CONTACT, NodeSecurityIndicator.of(signedOn28, isOwnNode = true))
        assertEquals(
            NodeSecurityIndicator.VERIFIED_CONTACT,
            NodeSecurityIndicator.of(signedOn28.copy(manuallyVerified = true)),
        )

        val legacy = signedOn28.copy(metadata = DeviceMetadata(firmware_version = "2.7.26"))
        assertEquals(NodeSecurityIndicator.PUBLIC_KEY, NodeSecurityIndicator.of(legacy))
        assertEquals(NodeSecurityIndicator.SIGNED_NODE, NodeSecurityIndicator.of(legacy.copy(signsPackets = true)))
        assertEquals(NodeSecurityIndicator.NO_PUBLIC_KEY, NodeSecurityIndicator.of(legacy.copy(user = User())))

        val mismatched = legacy.copy(user = User(public_key = Node.ERROR_BYTE_STRING))
        assertEquals(NodeSecurityIndicator.KEY_MISMATCH, NodeSecurityIndicator.of(mismatched))
        assertEquals(
            NodeSecurityIndicator.KEY_MISMATCH,
            NodeSecurityIndicator.of(mismatched.copy(manuallyVerified = true)),
        )
    }
}
