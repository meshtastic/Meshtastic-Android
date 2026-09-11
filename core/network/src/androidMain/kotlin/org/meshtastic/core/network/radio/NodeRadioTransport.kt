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
package org.meshtastic.core.network.radio

import android.content.Context
import android.os.Build
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportCallback
import org.meshtastic.node.BroadcastPolicy
import org.meshtastic.node.MeshChannel
import org.meshtastic.node.MeshIdentity
import org.meshtastic.node.MeshNode
import org.meshtastic.node.NodeIdentityRecord
import org.meshtastic.node.NodeIdentityStore
import org.meshtastic.node.ReliableDelivery
import org.meshtastic.node.loadOrCreate
import org.meshtastic.node.phoneapi.AdminService
import org.meshtastic.node.phoneapi.LocalRadio
import org.meshtastic.node.phoneapi.NodeSettings
import org.meshtastic.node.phoneapi.NodeSettingsStore
import org.meshtastic.node.phoneapi.PhoneApiSession
import org.meshtastic.node.phoneapi.decodeNodeSettings
import org.meshtastic.node.transport.MeshTransport
import org.meshtastic.node.transport.ble.AndroidBleContext
import org.meshtastic.node.transport.ble.BleMeshTransport
import org.meshtastic.node.transport.ble.gatt.AndroidGattContext
import org.meshtastic.node.transport.ble.gatt.GattMeshTransport
import org.meshtastic.node.transport.ble.gatt.GattPhy
import org.meshtastic.node.transport.ble.gatt.GattRole
import org.meshtastic.node.transport.ble.gatt.gattLink
import org.meshtastic.node.transport.lora.AndroidLoraContext
import org.meshtastic.node.transport.lora.LoraConfig
import org.meshtastic.node.transport.lora.LoraModemPreset
import org.meshtastic.node.transport.lora.LoraRegion
import org.meshtastic.node.transport.lora.LoraTransport
import org.meshtastic.node.transport.lora.asSection
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.BackupPreferences
import org.meshtastic.proto.Config
import org.meshtastic.proto.ToRadio
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * This device as a Meshtastic node, served to the app over the firmware phone API with no wire under it.
 *
 * A `meshtastic-node-kmp` [MeshNode] holds its own identity and keys and meshes over the bearers a phone has: BLE
 * advertisements and mesh-peer GATT to other phones and to radios running the BLE-mesh firmware, and LoRa itself when a
 * CH341 stick (Meshtadpole, meshstick) is on the USB-C port. [PhoneApiSession] speaks `ToRadio`/`FromRadio` bare, which
 * is exactly what [RadioTransport] carries, so the whole app - channels, messages, node list, map, every config
 * screen - runs on top of the node unchanged. A config write from those screens lands in the node's own [AdminService]
 * and persists in this app's storage; a write to the `lora` section rebuilds the bearer, the way a radio reboots for
 * one, and shows as a short DeviceSleep followed by Connected.
 *
 * LoRa transmits only once a region is stored, set from the app's LoRa config screen like any radio, and never above
 * [MAX_TX_POWER_DBM]: the stick is bus-powered and 22 dBm browns out an OTG port. Demo-grade: no foreground service
 * keeps the node alive in the background, and Wi-Fi Aware and UDP are not yet wired in.
 */
class NodeRadioTransport(
    private val callback: RadioTransportCallback,
    private val scope: CoroutineScope,
    context: Context,
) : RadioTransport {
    private val app: Context = context.applicationContext
    private val lifecycle = TransportLifecycleGate("Node")
    private val transportJob = SupervisorJob(scope.coroutineContext[Job])
    private val transportScope = CoroutineScope(scope.coroutineContext + transportJob)

    // One writer into the session, as its contract asks. It reads the live node on every frame, so it survives a
    // rebuild without being restarted.
    private val toRadio = Channel<ByteArray>(capacity = TO_RADIO_BUFFER)

    private val identityStore = NodeIdentityFileStore(app)
    private val settingsStore = NodeSettingsFileStore(app)

    private val startedNanos = System.nanoTime()
    private val monotonicMs: () -> Long = { (System.nanoTime() - startedNanos) / NANOS_PER_MILLI }
    private val wallMs: () -> Long = { System.currentTimeMillis() }

    private class LiveNode(val job: Job, val session: PhoneApiSession)

    @Volatile private var live: LiveNode? = null

    override fun start() {
        lifecycle.runIfOpen {
            AndroidGattContext.applicationContext = app
            AndroidBleContext.applicationContext = app
            AndroidLoraContext.applicationContext = app
            transportScope.launch {
                launch {
                    for (bytes in toRadio) {
                        val session = live?.session
                        Logger.d {
                            "ToRadio ${bytes.size} B: ${describe(
                                bytes,
                            )} -> ${if (session == null) "no node yet" else "node"}"
                        }
                        session?.toRadio(bytes)
                    }
                }
                bringUp(readSettings())
            }
        }
    }

    override fun handleSendToRadio(p: ByteArray): Boolean =
        lifecycle.runIfOpen { toRadio.trySend(p).isSuccess } ?: false

    override suspend fun close() {
        val completed =
            lifecycle.close(
                beforeDrain = { transportJob.cancelAndJoin() },
                teardown = {
                    live?.session?.close()
                    live = null
                    Logger.i { "Closing the node transport" }
                },
            )
        if (!completed) Logger.w { "Node transport teardown did not complete within its lifecycle bounds" }
    }

    private suspend fun readSettings(): BackupPreferences? =
        runCatching { settingsStore.load()?.let(::decodeNodeSettings) }
            .getOrElse {
                Logger.w(it) { "Node settings unreadable, running at defaults" }
                null
            }

    /** Build the node from what is stored, wire the session to the app, and report Connected. */
    private suspend fun bringUp(stored: BackupPreferences?) {
        val record = identityStore.loadOrCreate()
        val lora = loraTransport(stored?.config?.lora)
        // A stick already on the port needs the system's USB permission; the request is a no-op without one.
        lora?.requestPermission()
        val bearers =
            buildList<MeshTransport> {
                add(GattMeshTransport(gattLink(GattRole.DUAL, GattPhy.LE_2M), clock = monotonicMs))
                add(BleMeshTransport())
                lora?.let(::add)
            }
        val job = SupervisorJob(transportJob)
        val nodeScope = CoroutineScope(transportScope.coroutineContext + job)
        val node =
            MeshNode(nodeScope) {
                identity = MeshIdentity.fromPublicKey(record.publicKey, defaultLongName(), defaultShortName())
                privateKey = record.privateKey
                publicKey = record.publicKey
                channels += MeshChannel(DEFAULT_CHANNEL, byteArrayOf(DEFAULT_PSK_INDEX))
                // The library's default is 0, which nothing relays; the firmware's is 3.
                hopLimit = DEFAULT_HOP_LIMIT
                transports += bearers
                clock = monotonicMs
                epochSeconds = { System.currentTimeMillis() / MILLIS_PER_SECOND }
                // Announce on the firmware's schedule, so peers list this node and learn its public key.
                beacon = BroadcastPolicy()
                reliableDelivery = ReliableDelivery()
            }
        val overlay = NodeSettings()
        val built = lora?.config?.asSection()
        val radio =
            LocalRadio(
                node,
                region = built?.region ?: Config.LoRaConfig.RegionCode.UNSET,
                loraSection = built,
                clock = wallMs,
                settings = overlay,
            )
        val admin = AdminService(node, radio, overlay, clock = wallMs, store = settingsStore)
        stored?.let { admin.restore(it) }
        // Persist the lora section through this node's own radio view. LocalRadio reports the bearer's
        // region over any write, so the write that armed the bearer was stored as UNSET by the node
        // that received it; only the node built from it can store it back as what it is.
        stored?.config?.lora?.let { admin.setConfig(Config(lora = it)) }
        val session = PhoneApiSession(node, radio, nodeScope, admin)
        live = LiveNode(job, session)

        // Collecting events is what opens every bearer; a node nobody collects never touches a radio.
        // Every event names its bearer, which is the only place that attribution exists.
        nodeScope.launch { node.events.collect { event -> Logger.d { "node event: $event" } } }
        nodeScope.launch {
            session.fromRadio.collect { bytes -> lifecycle.runIfOpen { callback.handleFromRadio(bytes) } }
        }
        // drop(1): the replayed first emission is the overlay this node was just built from. The
        // phone's own write is read from the overlay: LocalRadio reports the bearer's region over it,
        // so preferences() can never show a region arriving.
        nodeScope.launch {
            overlay.state.drop(1).collect {
                val written = overlay.config(AdminMessage.ConfigType.LORA_CONFIG)?.lora
                Logger.d { "Node settings written; lora region=${written?.region} preset=${written?.modem_preset}" }
                if (written.retunes(built)) {
                    Logger.i { "Node lora section changed, rebuilding the bearer" }
                    rebuild(admin.preferences().copy(config = admin.preferences().config?.copy(lora = written)))
                }
            }
        }
        Logger.i { "Node !${node.identity.nodeNum.toString(HEX)} up on ${bearers.joinToString { it.name }}" }
        callback.onConnect()
    }

    /** Off the node's own scope, because tearing that scope down would cancel the collector asking for this. */
    private fun rebuild(current: BackupPreferences) {
        transportScope.launch {
            callback.onDisconnect(isPermanent = false)
            live?.let {
                it.session.close()
                it.job.cancelAndJoin()
            }
            live = null
            bringUp(current)
        }
    }

    /**
     * The LoRa bearer, built only once a region is stored - UNSET listens and refuses every transmit, so building it
     * early would only pin the USB port. The app's LoRa screen writes the region like it does on a radio.
     */
    private fun loraTransport(stored: Config.LoRaConfig?): LoraTransport? {
        val regionName = stored?.region?.name?.takeIf { it != UNSET_REGION }
        val band = regionName?.let(LoraRegion::byName)
        if (stored == null || band == null) {
            Logger.i {
                if (regionName == null) {
                    "Node has no LoRa region stored; set one in LoRa config to transmit"
                } else {
                    "Node region $regionName names no band this build knows"
                }
            }
            return null
        }
        val preset =
            LoraModemPreset.entries.firstOrNull { it.name == stored.modem_preset.name } ?: LoraModemPreset.LONG_FAST
        val config =
            LoraConfig(
                region = band,
                preset = preset,
                channelNum = stored.channel_num,
                overrideFrequencyMHz = stored.override_frequency.toDouble(),
                txPowerDbm = stored.tx_power.takeIf { it > 0 }?.coerceAtMost(MAX_TX_POWER_DBM) ?: MAX_TX_POWER_DBM,
                rxBoostedGain = stored.sx126x_rx_boosted_gain,
            )
        return LoraTransport(config, clock = monotonicMs, log = { line -> Logger.d { "lora: $line" } })
    }

    /** One line about a phone's frame, for the log: which packet, to whom, on which port. */
    private fun describe(bytes: ByteArray): String {
        val msg = runCatching { ToRadio.ADAPTER.decode(bytes) }.getOrNull() ?: return "undecodable"
        val packet = msg.packet
        return when {
            packet != null ->
                "packet id=${packet.id} to=!${(packet.to.toLong() and MASK32).toString(
                    HEX,
                )} port=${packet.decoded?.portnum}"

            msg.want_config_id != null -> "want_config ${msg.want_config_id}"

            msg.heartbeat != null -> "heartbeat"

            else -> "other"
        }
    }

    private fun defaultLongName(): String = "${Build.MODEL} node".take(MAX_LONG_NAME)

    private fun defaultShortName(): String =
        Build.MODEL.filter { it.isLetterOrDigit() }.uppercase().take(MAX_SHORT_NAME).ifEmpty { "NODE" }

    private companion object {
        const val TO_RADIO_BUFFER = 64
        const val NANOS_PER_MILLI = 1_000_000L
        const val MILLIS_PER_SECOND = 1_000L
        const val DEFAULT_CHANNEL = "LongFast"
        const val DEFAULT_PSK_INDEX: Byte = 1
        const val DEFAULT_HOP_LIMIT = 3
        const val UNSET_REGION = "UNSET"
        const val MAX_TX_POWER_DBM = 10
        const val MAX_LONG_NAME = 39
        const val MAX_SHORT_NAME = 4
        const val HEX = 16
        const val MASK32 = 0xFFFF_FFFFL
    }
}

/**
 * Whether the app's write to the `lora` section needs the bearer rebuilt: everything a [LoraConfig] is built from. With
 * no bearer built yet, a region arriving is what arms one.
 */
private fun Config.LoRaConfig?.retunes(built: Config.LoRaConfig?): Boolean = when {
    this == null -> false

    built == null -> region != Config.LoRaConfig.RegionCode.UNSET

    else ->
        region != built.region ||
            modem_preset != built.modem_preset ||
            use_preset != built.use_preset ||
            channel_num != built.channel_num ||
            override_frequency != built.override_frequency ||
            tx_power != built.tx_power ||
            sx126x_rx_boosted_gain != built.sx126x_rx_boosted_gain
}

/** Writes a file atomically into the app's no-backup storage: temp file, fsync, rename. */
private suspend fun writeAtomically(dir: File, name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
    val file = File(dir, name)
    val tmp = File(dir, "$name.tmp")
    try {
        FileOutputStream(tmp).use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        if (!tmp.renameTo(file)) throw IOException("could not rename ${tmp.path} to ${file.path}")
    } catch (e: IOException) {
        tmp.delete()
        throw e
    }
}

/**
 * The node's X25519 keypair, which is also its address (crc32 of the public key since firmware 2.8). In
 * `noBackupFilesDir`: key material must not ride along in a device backup.
 */
private class NodeIdentityFileStore(private val context: Context) : NodeIdentityStore {
    override suspend fun load(): NodeIdentityRecord? = withContext(Dispatchers.IO) {
        val file = File(context.noBackupFilesDir, FILE_NAME)
        if (!file.exists()) return@withContext null
        val bytes = file.readBytes()
        NodeIdentityRecord.decode(bytes)
            ?: throw IOException("${file.path}: ${bytes.size} bytes are not a node identity")
    }

    override suspend fun save(record: NodeIdentityRecord) =
        writeAtomically(context.noBackupFilesDir, FILE_NAME, record.encode())

    private companion object {
        const val FILE_NAME = "mesh-node-identity.bin"
    }
}

/** The node's `BackupPreferences`: config, channels with their keys, owner. Same storage class as the identity. */
private class NodeSettingsFileStore(private val context: Context) : NodeSettingsStore {
    override suspend fun load(): ByteArray? =
        withContext(Dispatchers.IO) { File(context.noBackupFilesDir, FILE_NAME).takeIf { it.exists() }?.readBytes() }

    override suspend fun save(bytes: ByteArray) = writeAtomically(context.noBackupFilesDir, FILE_NAME, bytes)

    override suspend fun clear() = withContext(Dispatchers.IO) {
        File(context.noBackupFilesDir, FILE_NAME).delete()
        Unit
    }

    private companion object {
        const val FILE_NAME = "mesh-node-settings.bin"
    }
}
