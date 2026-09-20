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
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Writes core/resources/.../values/schema_strings.xml: every label and description in the protobufs field-metadata
// registry, keyed by schema path (`schema_lora_hop_limit`). A settings control that edits a schema field names that
// key and nothing else has to be written. `test` fails when the generated file is stale, so the schema stays the
// only place this copy lives.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.meshtastic.detekt)
    alias(libs.plugins.meshtastic.spotless)
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.JETBRAINS)
    }
    compilerOptions { jvmTarget.set(JvmTarget.JVM_25) }
}

dependencies {
    implementation(libs.meshtastic.protobufs)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

val repositoryRoot = isolated.rootProject.projectDirectory

tasks.test {
    useJUnitPlatform()
    systemProperty("schemaStrings.rootDir", repositoryRoot.asFile.absolutePath)
    inputs.dir(repositoryRoot.dir("core/resources/src/commonMain/composeResources"))
}

tasks.register<JavaExec>("sync") {
    description = "Regenerates core/resources/.../values/schema_strings.xml from the protobufs field-metadata registry."
    group = "verification"
    val root = repositoryRoot
    mainClass.set("org.meshtastic.schemastrings.MainKt")
    classpath = sourceSets.main.get().runtimeClasspath
    argumentProviders.add(CommandLineArgumentProvider { listOf(root.asFile.absolutePath) })
    outputs.upToDateWhen { false }
}
