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
package org.meshtastic.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.unsupported_device
import org.meshtastic.core.ui.theme.AppTheme

/** The JNI library `androidx.sqlite`'s bundled driver loads, by this name, before its first database open. */
internal const val BUNDLED_SQLITE_LIBRARY = "sqliteJni"

private const val TAG = "BundledSqliteGuard"

/** Whether [load] succeeds. Only a failed link counts: anything else is a bug and still crashes. */
internal fun bundledSqliteLoads(load: () -> Unit): Boolean = try {
    load()
    true
} catch (e: UnsatisfiedLinkError) {
    Logger.withTag(TAG).e(e) { "Bundled SQLite can't load on ${Build.SUPPORTED_ABIS.joinToString()}" }
    false
}

/** Receivers and services this app declares, rather than one merged in from a library. */
internal fun isAppEntryPoint(className: String): Boolean = className.startsWith(APP_NAMESPACE)

private const val APP_NAMESPACE = "org.meshtastic."

/**
 * Every receiver and service the app declares, each of which can start the process without the user opening the app.
 * Providers and library components are left alone: none of them opens the database.
 */
internal fun Context.appEntryPoints(): List<ComponentName> {
    val flags = PackageManager.GET_RECEIVERS or PackageManager.GET_SERVICES or PackageManager.MATCH_DISABLED_COMPONENTS
    val info: PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, flags)
        }
    return (info.receivers.orEmpty().asList<ComponentInfo>() + info.services.orEmpty().asList())
        .map { it.name }
        .filter(::isAppEntryPoint)
        .map { ComponentName(packageName, it) }
}

/**
 * Stops the system starting [appEntryPoints]. Synchronous, because the component that started this process runs next.
 */
internal fun Context.disableAppEntryPoints() {
    appEntryPoints().forEach {
        packageManager.setComponentEnabledSetting(
            it,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}

/**
 * Undoes [disableAppEntryPoints] once the library loads, as after an update that ships it. Component state survives an
 * update, so nothing else would. Back to the manifest's own default, not forced on.
 */
internal fun Context.restoreAppEntryPoints() {
    appEntryPoints()
        .filter { packageManager.getComponentEnabledSetting(it) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED }
        .forEach {
            Logger.withTag(TAG).i { "Re-enabling ${it.className}" }
            packageManager.setComponentEnabledSetting(
                it,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP,
            )
        }
}

/** All MainActivity shows when the bundled SQLite can't load. Nothing here may touch Koin, which never started. */
@Composable
internal fun UnsupportedDeviceScreen() {
    AppTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.unsupported_device),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
