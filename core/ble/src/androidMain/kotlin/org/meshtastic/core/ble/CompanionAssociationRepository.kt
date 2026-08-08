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
package org.meshtastic.core.ble

import android.bluetooth.BluetoothManager
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.model.util.anonymize

/**
 * Manages Companion Device Manager (CDM) associations for Meshtastic radios.
 *
 * An association is the OS-level record that this app "companions" a specific Bluetooth device. Holding one (together
 * with `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND`) exempts the app from the Android 12+ background
 * foreground-service-start restriction, which is what lets a `DeviceAddressChanged` start succeed after the app has
 * been backgrounded. Associations are strictly additive: every flow that works without one keeps working without one.
 *
 * The CDM chooser is a system activity: [associate] only *requests* an association and emits the chooser's
 * [IntentSender] on [chooserRequests]; the current activity must launch it and the user must confirm. The result is
 * never trusted directly — [hasAssociationFor] always re-queries the platform.
 *
 * API fork: CDM exists since API 26, but the association API was replaced in API 33 — `getMyAssociations()` returning
 * `AssociationInfo` (with integer ids for `disassociate(int)`) supersedes `getAssociations(): List<String>` and
 * `disassociate(String)`. Every entry point is additionally guarded on [PackageManager.FEATURE_COMPANION_DEVICE_SETUP],
 * which not all devices declare.
 */
@Single
class CompanionAssociationRepository(private val context: Context) {

    private val companionDeviceManager: CompanionDeviceManager?
        get() =
            if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) {
                context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager
            } else {
                null
            }

    private val _chooserRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)

    /**
     * CDM chooser dialogs waiting to be launched. Collected by the foreground activity, which launches each sender via
     * `StartIntentSenderForResult` and calls [notifyAssociationsChanged] once the chooser finishes.
     */
    val chooserRequests: SharedFlow<IntentSender> = _chooserRequests.asSharedFlow()

    private val _associationsRevision = MutableStateFlow(0)

    /**
     * Bumped whenever the association set may have changed (chooser finished, [disassociate] ran). Combine with it to
     * re-run [hasAssociationFor] reactively.
     */
    val associationsRevision: StateFlow<Int> = _associationsRevision.asStateFlow()

    /** Signals that the association set may have changed; recomputes everything derived from [hasAssociationFor]. */
    fun notifyAssociationsChanged() {
        _associationsRevision.value += 1
    }

    /**
     * Whether an association currently exists for the radio at [mac].
     *
     * Also the disassociation point for removed radios: radios are "forgotten" by unpairing (in-app or in system
     * Bluetooth settings), so an association whose device is no longer bonded is stale — it is dropped here and the
     * method returns false. When the bonded set cannot be read (missing `BLUETOOTH_CONNECT`), the association is kept:
     * only positive knowledge of an unpair may revoke it.
     */
    fun hasAssociationFor(mac: String): Boolean {
        val associated = associatedMacs().any { it.equals(mac, ignoreCase = true) }
        val unpaired = associated && isBonded(mac) == false
        if (unpaired) {
            Logger.i { "Dropping stale companion association for unpaired device ${mac.anonymize}" }
            disassociate(mac)
        }
        return associated && !unpaired
    }

    /**
     * Requests a CDM association for the radio at [mac], emitting the system chooser's [IntentSender] on
     * [chooserRequests]. No-op when CDM is unavailable or an association already exists. Failure or user cancellation
     * leaves everything as it was.
     */
    fun associate(mac: String) {
        val manager = companionDeviceManager ?: return
        if (hasAssociationFor(mac)) return

        val request =
            AssociationRequest.Builder()
                .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(mac).build())
                .setSingleDevice(true)
                .build()

        Logger.i { "Requesting companion association for ${mac.anonymize}" }
        manager.associate(
            request,
            object : CompanionDeviceManager.Callback() {
                // API 33+ entry point; the deprecated override below covers API 26-32.
                override fun onAssociationPending(intentSender: IntentSender) {
                    onChooserReady(intentSender)
                }

                @Deprecated("Called by the platform on API 26-32 only")
                override fun onDeviceFound(intentSender: IntentSender) {
                    onChooserReady(intentSender)
                }

                override fun onFailure(error: CharSequence?) {
                    Logger.w { "Companion association request failed for ${mac.anonymize}: $error" }
                }
            },
            null,
        )
    }

    private fun onChooserReady(intentSender: IntentSender) {
        if (!_chooserRequests.tryEmit(intentSender)) {
            Logger.w { "Dropped companion association chooser: no capacity" }
        }
    }

    /** Removes any association held for the radio at [mac]. Safe to call when none exists. */
    fun disassociate(mac: String) {
        val manager = companionDeviceManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.myAssociations
                    .filter { it.deviceMacAddress?.toString().equals(mac, ignoreCase = true) }
                    .forEach { manager.disassociate(it.id) }
            } else {
                @Suppress("DEPRECATION")
                if (manager.associations.any { it.equals(mac, ignoreCase = true) }) {
                    @Suppress("DEPRECATION")
                    manager.disassociate(mac)
                }
            }
            Logger.i { "Disassociated companion device ${mac.anonymize}" }
        } catch (ex: IllegalArgumentException) {
            // The platform throws when the association vanished between the lookup and the call; nothing to undo.
            Logger.w(ex) { "Disassociate failed for ${mac.anonymize}" }
        }
        notifyAssociationsChanged()
    }

    private fun associatedMacs(): List<String> {
        val manager = companionDeviceManager ?: return emptyList()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.myAssociations.mapNotNull { it.deviceMacAddress?.toString() }
        } else {
            @Suppress("DEPRECATION")
            manager.associations
        }
    }

    /**
     * True/false when the bonded set is readable; null when it is not. The adapter must be ON: a disabled adapter
     * reports an EMPTY bonded set, which must not be mistaken for "the user unpaired this radio".
     */
    private fun isBonded(mac: String): Boolean? {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        return try {
            if (adapter?.isEnabled != true) return null
            adapter.bondedDevices?.any { it.address.equals(mac, ignoreCase = true) }
        } catch (ex: SecurityException) {
            Logger.d(ex) { "Cannot read bonded devices; keeping companion association" }
            null
        }
    }

    companion object {
        /**
         * Extracts the bare Bluetooth MAC from an app-level full address ("x" + MAC, see [InterfaceId.BLUETOOTH]).
         * Returns null for non-BLE addresses.
         */
        fun bleMacFromFullAddress(fullAddress: String?): String? =
            fullAddress?.takeIf { it.length > 1 && it.first() == InterfaceId.BLUETOOTH.id }?.substring(1)
    }
}
