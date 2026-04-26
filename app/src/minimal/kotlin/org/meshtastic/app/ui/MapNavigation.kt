package org.meshtastic.app.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

fun EntryProviderScope<NavKey>.registerMapGraph(backStack: NavBackStack<NavKey>) {
    // Maps are disabled in this flavor
}
