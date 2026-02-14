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
package org.meshtastic.feature.settings.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.core.os.LocaleListCompat
import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.fr_HT
import org.meshtastic.core.strings.preferences_system_default
import org.meshtastic.core.strings.pt_BR
import org.meshtastic.core.strings.zh_CN
import org.meshtastic.core.strings.zh_TW
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

object LanguageUtils {

    const val SYSTEM_DEFAULT = "zz"

    /**
     * Sets the application locale using AppCompatDelegate. Note: This is the modern standard for per-app language
     * support, providing a backport for API levels below 33.
     */
    fun setAppLocale(languageTag: String) {
        AppCompatDelegate.setApplicationLocales(
            if (languageTag == SYSTEM_DEFAULT) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(languageTag)
            },
        )
    }

    /** Using locales_config.xml, maps language tags to their localized language names (e.g.: "en" -> "English") */
    @Suppress("CyclomaticComplexMethod")
    @Composable
    fun languageMap(): Map<String, String> {
        val resources = LocalResources.current
        val languageTags =
            remember(resources) {
                buildList {
                    add(SYSTEM_DEFAULT)

                    try {
                        resources.getXml(org.meshtastic.feature.settings.R.xml.locales_config).use { parser ->
                            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                                    val languageTag =
                                        parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
                                    languageTag?.let { add(it) }
                                }
                                parser.next()
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e { "Error parsing locale_config.xml: ${e.message}" }
                    }
                }
            }

        return languageTags.associateWith { languageTag ->
            when (languageTag) {
                SYSTEM_DEFAULT -> stringResource(Res.string.preferences_system_default)
                "fr-HT" -> stringResource(Res.string.fr_HT)
                "pt-BR" -> stringResource(Res.string.pt_BR)
                "zh-CN" -> stringResource(Res.string.zh_CN)
                "zh-TW" -> stringResource(Res.string.zh_TW)
                else -> {
                    Locale.forLanguageTag(languageTag).let { locale ->
                        locale.getDisplayLanguage(locale).replaceFirstChar { char ->
                            if (char.isLowerCase()) char.titlecase(locale) else char.toString()
                        }
                    }
                }
            }
        }
    }
}
