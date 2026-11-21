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
package org.meshtastic.core.data.manager

import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.BluetoothConnectionStatus
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Config
import org.meshtastic.proto.Data
import org.meshtastic.proto.DeviceConnectionStatus
import org.meshtastic.proto.DeviceUIConfig
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.KeyVerificationAdmin
import org.meshtastic.proto.KeyVerificationNumberInform
import org.meshtastic.proto.LockdownAuth
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.PortNum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogRedactionTest {

    private val secret: ByteString = ByteArray(32) { (it + 1).toByte() }.toByteString()
    private val publicKey: ByteString = ByteArray(32) { (it + 101).toByte() }.toByteString()

    private fun configFrame(config: Config) = FromRadio.Builder().also { it.config = config }.build()

    private fun adminPacket(admin: AdminMessage) = MeshPacket.Builder()
        .also { p ->
            p.id = 77
            p.from = 5
            p.decoded =
                Data.Builder()
                    .also {
                        it.portnum = PortNum.ADMIN_APP
                        it.payload = AdminMessage.ADAPTER.encodeByteString(admin)
                        it.want_response = true
                    }
                    .build()
        }
        .build()

    private fun loggedAdmin(admin: AdminMessage): AdminMessage =
        AdminMessage.ADAPTER.decode(adminPacket(admin).redactedForLog().decoded!!.payload)

    @Test
    fun `security private key and admin keys are cleared while the public key stays`() {
        val frame =
            configFrame(
                Config.Builder()
                    .also {
                        it.security =
                            Config.SecurityConfig.Builder()
                                .also { s ->
                                    s.public_key = publicKey
                                    s.private_key = secret
                                    s.admin_key = listOf(secret, secret)
                                    s.serial_enabled = true
                                }
                                .build()
                    }
                    .build(),
            )

        val security = frame.redactedForLog().config!!.security!!

        assertEquals(ByteString.EMPTY, security.private_key)
        assertTrue(security.admin_key.isEmpty())
        assertEquals(publicKey, security.public_key)
        assertTrue(security.serial_enabled)
    }

    @Test
    fun `wifi psk is cleared while ssid and neighbors stay`() {
        val config =
            Config.Builder()
                .also {
                    it.network =
                        Config.NetworkConfig.Builder()
                            .also { n ->
                                n.wifi_ssid = "home"
                                n.wifi_psk = "secret-psk"
                                n.ntp_server = "pool.example"
                            }
                            .build()
                }
                .build()

        val network = configFrame(config).redactedForLog().config!!.network!!

        assertEquals("", network.wifi_psk)
        assertEquals("home", network.wifi_ssid)
        assertEquals("pool.example", network.ntp_server)
    }

    @Test
    fun `bluetooth fixed pin is cleared while the pairing mode stays`() {
        val config =
            Config.Builder()
                .also {
                    it.bluetooth =
                        Config.BluetoothConfig.Builder()
                            .also { b ->
                                b.mode = Config.BluetoothConfig.PairingMode.FIXED_PIN
                                b.fixed_pin = 123456
                            }
                            .build()
                }
                .build()

        val bluetooth = configFrame(config).redactedForLog().config!!.bluetooth!!

        assertEquals(0, bluetooth.fixed_pin)
        assertEquals(Config.BluetoothConfig.PairingMode.FIXED_PIN, bluetooth.mode)
    }

    @Test
    fun `a config frame without secrets is returned equal`() {
        val frame =
            configFrame(
                Config.Builder()
                    .also { it.lora = Config.LoRaConfig.Builder().also { l -> l.hop_limit = 5 }.build() }
                    .build(),
            )

        assertEquals(frame, frame.redactedForLog())
    }

    @Test
    fun `mqtt username and password are cleared while the address stays`() {
        val frame =
            FromRadio.Builder()
                .also {
                    it.moduleConfig =
                        ModuleConfig.Builder()
                            .also { m ->
                                m.mqtt =
                                    ModuleConfig.MQTTConfig.Builder()
                                        .also { q ->
                                            q.address = "mqtt.example"
                                            q.username = "user"
                                            q.password = "pass"
                                        }
                                        .build()
                            }
                            .build()
                }
                .build()

        val mqtt = frame.redactedForLog().moduleConfig!!.mqtt!!

        assertEquals("", mqtt.username)
        assertEquals("", mqtt.password)
        assertEquals("mqtt.example", mqtt.address)
    }

    @Test
    fun `channel psk is cleared while the channel name and index stay`() {
        val frame =
            FromRadio.Builder()
                .also {
                    it.channel =
                        Channel.Builder()
                            .also { c ->
                                c.index = 2
                                c.settings =
                                    ChannelSettings.Builder()
                                        .also { s ->
                                            s.psk = secret
                                            s.name = "Ops"
                                        }
                                        .build()
                            }
                            .build()
                }
                .build()

        val channel = frame.redactedForLog().channel!!

        assertEquals(ByteString.EMPTY, channel.settings!!.psk)
        assertEquals("Ops", channel.settings!!.name)
        assertEquals(2, channel.index)
    }

    @Test
    fun `device ui pin code is cleared`() {
        val frame =
            FromRadio.Builder()
                .also {
                    it.deviceuiConfig =
                        DeviceUIConfig.Builder()
                            .also { u ->
                                u.pin_code = 123456
                                u.screen_brightness = 80
                            }
                            .build()
                }
                .build()

        val ui = frame.redactedForLog().deviceuiConfig!!

        assertEquals(0, ui.pin_code)
        assertEquals(80, ui.screen_brightness)
    }

    @Test
    fun `key verification number in a client notification is cleared while the peer name stays`() {
        val frame =
            FromRadio.Builder()
                .also {
                    it.clientNotification =
                        ClientNotification.Builder()
                            .also { n ->
                                n.key_verification_number_inform =
                                    KeyVerificationNumberInform.Builder()
                                        .also { k ->
                                            k.remote_longname = "Peer"
                                            k.security_number = 123456
                                        }
                                        .build()
                            }
                            .build()
                }
                .build()

        val inform = frame.redactedForLog().clientNotification!!.key_verification_number_inform!!

        assertEquals(0, inform.security_number)
        assertEquals("Peer", inform.remote_longname)
    }

    @Test
    fun `admin session passkey is cleared from a logged admin packet while the packet stays intact`() {
        val packet =
            adminPacket(
                AdminMessage.Builder()
                    .also {
                        it.session_passkey = secret
                        it.get_owner_request = true
                    }
                    .build(),
            )

        val logged = packet.redactedForLog()
        val admin = AdminMessage.ADAPTER.decode(logged.decoded!!.payload)

        assertEquals(ByteString.EMPTY, admin.session_passkey)
        assertEquals(true, admin.get_owner_request)
        assertEquals(77, logged.id)
        assertEquals(PortNum.ADMIN_APP, logged.decoded!!.portnum)
        assertTrue(logged.decoded!!.want_response == true)
    }

    @Test
    fun `admin set config response carries cleared keys in the logged payload`() {
        val config =
            Config.Builder()
                .also {
                    it.security =
                        Config.SecurityConfig.Builder()
                            .also { s ->
                                s.private_key = secret
                                s.admin_key = listOf(secret)
                                s.public_key = publicKey
                            }
                            .build()
                }
                .build()

        val set = loggedAdmin(AdminMessage.Builder().also { it.set_config = config }.build())
        val response = loggedAdmin(AdminMessage.Builder().also { it.get_config_response = config }.build())

        listOf(set.set_config!!, response.get_config_response!!).forEach {
            assertEquals(ByteString.EMPTY, it.security!!.private_key)
            assertTrue(it.security!!.admin_key.isEmpty())
            assertEquals(publicKey, it.security!!.public_key)
        }
    }

    @Test
    fun `admin set module config and channel carry cleared credentials in the logged payload`() {
        val mqtt =
            ModuleConfig.Builder()
                .also { m ->
                    m.mqtt =
                        ModuleConfig.MQTTConfig.Builder()
                            .also { q ->
                                q.username = "user"
                                q.password = "pass"
                            }
                            .build()
                }
                .build()
        val channel =
            Channel.Builder()
                .also { c -> c.settings = ChannelSettings.Builder().also { s -> s.psk = secret }.build() }
                .build()

        val loggedModule = loggedAdmin(AdminMessage.Builder().also { it.set_module_config = mqtt }.build())
        val loggedChannel = loggedAdmin(AdminMessage.Builder().also { it.set_channel = channel }.build())
        val loggedModuleResponse =
            loggedAdmin(AdminMessage.Builder().also { it.get_module_config_response = mqtt }.build())
        val loggedChannelResponse =
            loggedAdmin(AdminMessage.Builder().also { it.get_channel_response = channel }.build())

        assertEquals("", loggedModule.set_module_config?.mqtt?.password)
        assertEquals("", loggedModule.set_module_config?.mqtt?.username)
        assertEquals(ByteString.EMPTY, loggedChannel.set_channel?.settings?.psk)
        assertEquals("", loggedModuleResponse.get_module_config_response?.mqtt?.password)
        assertEquals("", loggedModuleResponse.get_module_config_response?.mqtt?.username)
        assertEquals(ByteString.EMPTY, loggedChannelResponse.get_channel_response?.settings?.psk)
    }

    @Test
    fun `admin lockdown passphrase ui pin and key verification number are cleared in the logged payload`() {
        val logged =
            loggedAdmin(
                AdminMessage.Builder()
                    .also {
                        it.lockdown_auth =
                            LockdownAuth.Builder()
                                .also { l ->
                                    l.passphrase = "passphrase".encodeUtf8()
                                    l.boots_remaining = 3
                                }
                                .build()
                    }
                    .build(),
            )
        val ui =
            loggedAdmin(
                AdminMessage.Builder()
                    .also { it.store_ui_config = DeviceUIConfig.Builder().also { u -> u.pin_code = 1234 }.build() }
                    .build(),
            )
        val verification =
            loggedAdmin(
                AdminMessage.Builder()
                    .also {
                        it.key_verification =
                            KeyVerificationAdmin.Builder()
                                .also { k ->
                                    k.remote_nodenum = 9
                                    k.security_number = 4321
                                }
                                .build()
                    }
                    .build(),
            )

        assertEquals(ByteString.EMPTY, logged.lockdown_auth!!.passphrase)
        assertEquals(3, logged.lockdown_auth!!.boots_remaining)
        assertEquals(0, ui.store_ui_config!!.pin_code)
        assertNull(verification.key_verification!!.security_number)
        assertEquals(9, verification.key_verification!!.remote_nodenum)
    }

    @Test
    fun `an admin payload that does not decode is logged with an empty payload`() {
        val packet =
            MeshPacket.Builder()
                .also { p ->
                    p.id = 5
                    p.decoded =
                        Data.Builder()
                            .also {
                                it.portnum = PortNum.ADMIN_APP
                                it.payload = ByteArray(8) { 0xFF.toByte() }.toByteString()
                            }
                            .build()
                }
                .build()

        val logged = packet.redactedForLog()

        assertEquals(ByteString.EMPTY, logged.decoded!!.payload)
        assertEquals(5, logged.id)
    }

    @Test
    fun `a text message packet is stored unchanged`() {
        val packet =
            MeshPacket.Builder()
                .also { p ->
                    p.id = 9
                    p.decoded =
                        Data.Builder()
                            .also {
                                it.portnum = PortNum.TEXT_MESSAGE_APP
                                it.payload = "hello".encodeUtf8()
                            }
                            .build()
                }
                .build()
        val frame = FromRadio.Builder().also { it.packet = packet }.build()

        assertEquals(packet, packet.redactedForLog())
        assertEquals(frame, frame.redactedForLog())
    }

    @Test
    fun `admin connection status response is logged without the bluetooth pin and keeps the rest`() {
        val admin =
            AdminMessage.Builder()
                .also {
                    it.get_device_connection_status_response =
                        DeviceConnectionStatus.Builder()
                            .also { c ->
                                c.bluetooth =
                                    BluetoothConnectionStatus.Builder()
                                        .also { b ->
                                            b.pin = 135791
                                            b.rssi = -60
                                        }
                                        .build()
                            }
                            .build()
                }
                .build()

        val logged = loggedAdmin(admin).get_device_connection_status_response!!.bluetooth!!

        assertEquals(0, logged.pin)
        assertEquals(-60, logged.rssi)
        val original = AdminMessage.ADAPTER.decode(adminPacket(admin).decoded!!.payload)
        assertEquals(135791, original.get_device_connection_status_response!!.bluetooth!!.pin)
    }

    @Test
    fun `device ui inside a config frame has its pin cleared and the original keeps it`() {
        val config =
            Config.Builder()
                .also {
                    it.device_ui =
                        DeviceUIConfig.Builder()
                            .also { u ->
                                u.pin_code = 246801
                                u.screen_brightness = 70
                            }
                            .build()
                }
                .build()
        val frame = configFrame(config)

        val logged = frame.redactedForLog().config!!.device_ui!!

        assertEquals(0, logged.pin_code)
        assertEquals(70, logged.screen_brightness)
        assertEquals(246801, frame.config!!.device_ui!!.pin_code)
    }

    @Test
    fun `device ui inside admin set and get config payloads has its pin cleared`() {
        val config =
            Config.Builder()
                .also { it.device_ui = DeviceUIConfig.Builder().also { u -> u.pin_code = 246801 }.build() }
                .build()

        val set = loggedAdmin(AdminMessage.Builder().also { it.set_config = config }.build())
        val response = loggedAdmin(AdminMessage.Builder().also { it.get_config_response = config }.build())

        assertEquals(0, set.set_config!!.device_ui!!.pin_code)
        assertEquals(0, response.get_config_response!!.device_ui!!.pin_code)
    }

    @Test
    fun `mesh beacon offer channel psk is cleared while its name stays`() {
        val moduleConfig =
            ModuleConfig.Builder()
                .also { m ->
                    m.mesh_beacon =
                        ModuleConfig.MeshBeaconConfig.Builder()
                            .also { b ->
                                b.broadcast_message = "hello"
                                b.broadcast_offer_channel =
                                    ChannelSettings.Builder()
                                        .also { s ->
                                            s.psk = secret
                                            s.name = "Offer"
                                        }
                                        .build()
                            }
                            .build()
                }
                .build()
        val frame = FromRadio.Builder().also { it.moduleConfig = moduleConfig }.build()

        val beacon = frame.redactedForLog().moduleConfig!!.mesh_beacon!!

        assertEquals(ByteString.EMPTY, beacon.broadcast_offer_channel!!.psk)
        assertEquals("Offer", beacon.broadcast_offer_channel!!.name)
        assertEquals("hello", beacon.broadcast_message)
    }
}
