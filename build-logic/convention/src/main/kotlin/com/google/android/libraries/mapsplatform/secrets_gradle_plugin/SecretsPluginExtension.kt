// Copyright 2021 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// Vendored from google/secrets-gradle-plugin v2.0.1 — see SecretsPlugin.kt for why.
// Deviation from upstream: ignoreList copies defaultIgnoreList so projects don't share one mutable list.

package com.google.android.libraries.mapsplatform.secrets_gradle_plugin

/**
 * Configuration object for [SecretsPlugin].
 */
open class SecretsPluginExtension {
    /**
     * The name of the properties file containing secrets. Defaults to "$defaultPropertiesFile"
     */
    var propertiesFileName: String = defaultPropertiesFile

    /**
     * A list of keys this plugin should ignore and not inject. Defaults to $defaultIgnoreList
     */
    var ignoreList: MutableList<String> = defaultIgnoreList.toMutableList()

    /**
     * The name of the properties file containing secrets' default values.
     */
    var defaultPropertiesFileName: String? = null

    companion object {
        const val defaultPropertiesFile = "local.properties"
        val defaultIgnoreList = mutableListOf("sdk.dir")
    }
}
