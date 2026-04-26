package org.meshtastic.app.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.meshtastic.feature.map.navigation.mapGraph

fun EntryProviderScope<NavKey>.registerMapGraph(backStack: NavBackStack<NavKey>) {
    mapGraph(backStack)
}
