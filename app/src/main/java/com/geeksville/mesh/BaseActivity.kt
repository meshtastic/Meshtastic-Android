package com.geeksville.mesh

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import com.geeksville.android.Logging
import com.geeksville.mesh.model.UIViewModel
import java.util.*

open class BaseActivity: AppCompatActivity(), Logging {

    override fun attachBaseContext(newBase: Context) {
        val res = newBase.resources
        val config = res.configuration

        // get chosen language from preference
        val prefs = UIViewModel.getPreferences(newBase)
        val langCode: String = prefs.getString("lang","zz") ?: ""
        debug("langCode is $langCode")

        if (Build.VERSION.SDK_INT >= 17) {
            val locale = if (langCode == "zz")
                Locale.getDefault()
            else
                createLocale(langCode)
            config.setLocale(locale)

            if(Build.VERSION.SDK_INT > 24) {
                //Using createNewConfigurationContext will cause CompanionDeviceManager to crash
                applyOverrideConfiguration(config)
                super.attachBaseContext(newBase)
            }else {
                super.attachBaseContext(newBase.createConfigurationContext(config))
            }
        } else {
            super.attachBaseContext(newBase)
        }
    }

    private fun createLocale(language: String):Locale {
        var langArray = language.split("_")
        if (langArray.size == 2) {
            return Locale(langArray[0], langArray[1]);
        } else {
            return Locale(langArray[0]);
        }
    }

}
