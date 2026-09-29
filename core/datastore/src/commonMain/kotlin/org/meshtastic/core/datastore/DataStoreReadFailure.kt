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
package org.meshtastic.core.datastore

/**
 * True for the failures a DataStore read reports: okio's `IOException` from the file, and DataStore's own `IOException`
 * (the base of its `CorruptionException`) from the contents. Both alias `java.io.IOException` on the JVM, but on Native
 * they are separate classes, so a check for one alone misses the other.
 */
internal fun Throwable.isDataStoreReadFailure(): Boolean =
    this is okio.IOException || this is androidx.datastore.core.IOException
