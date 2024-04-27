package com.geeksville.mesh.util

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.R
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

object LanguageUtils : Logging {

    const val SYSTEM_DEFAULT = "zz"
    const val SYSTEM_MANAGED = "appcompat"

    fun getLocale(): String {
        return AppCompatDelegate.getApplicationLocales().toLanguageTags().ifEmpty { SYSTEM_DEFAULT }
    }

    fun setLocale(lang: String) {
        AppCompatDelegate.setApplicationLocales(
            if (lang == SYSTEM_DEFAULT) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(lang)
        )
    }

    fun migrateLanguagePrefs(prefs: SharedPreferences) {
        val currentLang = prefs.getString("lang", SYSTEM_DEFAULT) ?: SYSTEM_DEFAULT
        debug("Migrating in-app language prefs: $currentLang")
        prefs.edit { putString("lang", SYSTEM_MANAGED) }
        setLocale(currentLang)
    }

    /**
     * Build a list from locales_config.xml
     * of native language names paired to its Locale tag (ex: "English", "en")
     */
    fun getLanguageTags(context: Context): Map<String, String> {
        val languageTags = mutableListOf(SYSTEM_DEFAULT)
        try {
            context.resources.getXml(R.xml.locales_config).use {
                while (it.eventType != XmlPullParser.END_DOCUMENT) {
                    if (it.eventType == XmlPullParser.START_TAG && it.name == "locale") {
                        languageTags += it.getAttributeValue(0)
                    }
                    it.next()
                }
            }
        } catch (e: Exception) {
            errormsg("Error parsing locale_config.xml ${e.message}")
        }
        return languageTags.associateBy { tag ->
            val loc = Locale(tag)
            when (tag) {
                SYSTEM_DEFAULT -> context.getString(R.string.preferences_system_default)
                "fr-HT" -> context.getString(R.string.fr_HT)
                "pt-BR" -> context.getString(R.string.pt_BR)
                "zh-CN" -> context.getString(R.string.zh_CN)
                "zh-TW" -> context.getString(R.string.zh_TW)
                else -> loc.getDisplayLanguage(loc)
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(loc) else it.toString() }
            }
        }
    }
}