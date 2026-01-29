/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import java.io.FileInputStream
import java.util.Properties

project.pluginManager.apply("maven-publish")

val configProperties = Properties()
val configFile = rootProject.file("config.properties")
if (configFile.exists()) {
    FileInputStream(configFile).use { configProperties.load(it) }
}

val versionBase = configProperties.getProperty("VERSION_NAME_BASE") ?: "0.0.0-SNAPSHOT"
val appVersion = System.getenv("VERSION_NAME") ?: versionBase

project.version = appVersion
project.group = "org.meshtastic"
