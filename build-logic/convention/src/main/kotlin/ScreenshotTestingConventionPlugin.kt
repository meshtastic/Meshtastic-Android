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

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.meshtastic.buildlogic.configureScreenshotTesting

/**
 * Convention plugin that configures Compose Preview Screenshot Testing.
 *
 * Requires the `com.android.compose.screenshot` plugin to be applied first
 * via `alias(libs.plugins.screenshot)` in the consumer's `plugins {}` block,
 * because the screenshot plugin is not on the build-logic classpath.
 */
class ScreenshotTestingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            check(plugins.hasPlugin("com.android.compose.screenshot")) {
                "The 'com.android.compose.screenshot' plugin must be applied before 'meshtastic.screenshot.testing'."
            }

            extensions.configure<ApplicationExtension> {
                configureScreenshotTesting(this)
            }
        }
    }
}
