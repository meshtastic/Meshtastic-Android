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
import java.util.Properties

project.pluginManager.apply("maven-publish")

project.group = "org.meshtastic"

if (project.version == "unspecified") {
    val props = Properties().apply {
        rootProject.file("config.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    
    project.version = System.getenv("VERSION")
        ?: System.getenv("VERSION_NAME")
        ?: props.getProperty("VERSION_NAME_BASE")
        ?: "0.0.0-SNAPSHOT"

    println("Configured publication version for project ${project.name}: ${project.version}")
}
