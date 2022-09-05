package com.geeksville.mesh.android

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID
import kotlin.reflect.KProperty

/**
 * Created by kevinh on 1/4/15.
 */


/**
 * A delegate for "foo by FloatPref"
 */
class FloatPref {
    fun get(thisRef: AppPrefs, prop: KProperty<Float>): Float = thisRef.getPrefs().getFloat(thisRef.makeName(prop.name), java.lang.Float.MIN_VALUE)

    fun set(thisRef: AppPrefs, prop: KProperty<Float>, value: Float) {
        thisRef.setPrefs { e -> e.putFloat(thisRef.makeName(prop.name), value)}
    }
}

/**
 * A delegate for "foo by StringPref"
 */
class StringPref(val default: String) {
    fun get(thisRef: AppPrefs, prop: KProperty<String>): String = thisRef.getPrefs().getString(thisRef.makeName(prop.name), default)!!

    fun set(thisRef: AppPrefs, prop: KProperty<String>, value: String) {
        thisRef.setPrefs { e ->
            e.putString(thisRef.makeName(prop.name), value)
        }
    }
}

/**
 * A mixin for accessing android prefs for the app
 */
public open class AppPrefs(val context: Context) {

    companion object {
        private val baseName = "appPrefs_"
    }

    fun makeName(s: String) = baseName + s

    fun getPrefs() = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    fun setPrefs(body: (SharedPreferences.Editor) -> Unit) {
        val e = getPrefs().edit()
        body(e)
        e.commit()
    }

    fun incPref(name: String) {
        setPrefs { e ->
            e.putInt(name, 1 + getPrefs().getInt(name, 0))
        }
    }

    fun removePref(name: String) {
        setPrefs { e ->
            e.remove(name)
        }
    }

    fun putPref(name: String, b: Boolean) {
        setPrefs { e ->
            e.putBoolean(name, b)
        }
    }

    fun putPref(name: String, b: Float) {
        setPrefs { e ->
            e.putFloat(name, b)
        }
    }

    fun putPref(name: String, b: Int) {
        setPrefs { e ->
            e.putInt(name, b)
        }
    }

    fun putPref(name: String, b: Set<String>) {
        setPrefs { e ->
            e.putStringSet(name, b)
        }
    }

    fun putPref(name: String, b: String) {
        setPrefs { e ->
            e.putString(name, b)
        }
    }

    /**
     * Return a persistent installation ID
     */
    fun getInstallId(): String {
        var r = getPrefs().getString(makeName("installId"), "")!!
        if(r == "") {
            r = UUID.randomUUID().toString()
            putPref(makeName("installId"), r)
        }

        return r
    }
}