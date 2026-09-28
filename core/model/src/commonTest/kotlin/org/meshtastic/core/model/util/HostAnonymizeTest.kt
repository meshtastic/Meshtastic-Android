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
package org.meshtastic.core.model.util

import kotlin.test.Test
import kotlin.test.assertEquals

class HostAnonymizeTest {

    private fun assertKept(vararg hosts: String) {
        for (host in hosts) assertEquals(host, host.anonymizePublicHost(), "expected $host to stay readable")
    }

    private fun assertAnonymized(vararg hosts: String) {
        for (host in hosts) assertEquals(host.anonymize(), host.anonymizePublicHost(), "expected $host anonymized")
    }

    @Test
    fun `loopback hosts stay readable`() {
        assertKept("127.0.0.1", "127.0.0.1:4403", "localhost", "::1", "[::1]:4403")
    }

    @Test
    fun `RFC 1918 private IPv4 hosts stay readable`() {
        assertKept("10.0.0.5", "172.16.0.1", "172.31.255.254:4403", "192.168.1.50", "192.168.1.50:4403")
    }

    @Test
    fun `addresses just outside the RFC 1918 blocks are anonymized`() {
        assertAnonymized("172.15.0.1", "172.32.0.1", "11.0.0.1", "192.169.1.1")
    }

    @Test
    fun `IPv4 and IPv6 link-local hosts stay readable`() {
        assertKept("169.254.10.20", "fe80::1", "febf::1", "[fe80::1%en0]:4403")
    }

    @Test
    fun `IPv6 unique local hosts stay readable`() {
        assertKept("fc00::1", "fd12:3456:789a::1", "[fdab::2]:4403")
    }

    @Test
    fun `IPv6 addresses outside the local blocks are anonymized`() {
        assertAnonymized("fec0::1", "2001:db8::1", "[2606:4700::1111]:4403")
    }

    @Test
    fun `mDNS local names stay readable`() {
        assertKept("meshtastic.local", "Meshtastic.local:4403", "node.local.")
    }

    @Test
    fun `public IPv4 addresses are anonymized`() {
        assertAnonymized("8.8.8.8", "8.8.8.8:4403", "203.0.113.7")
    }

    @Test
    fun `DDNS and MagicDNS names are anonymized`() {
        assertAnonymized("mynode.duckdns.org", "node.tail1234.ts.net:4403", "meshnode")
    }

    @Test
    fun `malformed IPv4 literals are anonymized`() {
        assertAnonymized("256.1.1.1", "192.168.1", "10.0.0.1.5")
    }
}
