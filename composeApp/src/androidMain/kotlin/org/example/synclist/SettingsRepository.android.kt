package org.example.synclist

import android.content.Context
import android.content.SharedPreferences

class AndroidSettingsRepository(private val context: Context) : SettingsRepository {
    private val prefs: SharedPreferences = context.getSharedPreferences("synclist_prefs", Context.MODE_PRIVATE)

    override fun saveString(key: String, value: String) {
        prefs.edit().putString(key, value).commit()
    }

    override fun getString(key: String, defaultValue: String): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    override fun saveBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).commit()
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    override fun saveFloat(key: String, value: Float) {
        prefs.edit().putFloat(key, value).commit()
    }

    override fun getFloat(key: String, defaultValue: Float): Float {
        return prefs.getFloat(key, defaultValue)
    }

    override fun saveLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).commit()
    }

    override fun getLong(key: String, defaultValue: Long): Long {
        return prefs.getLong(key, defaultValue)
    }
}

private var appContext: Context? = null

fun initAndroidSettings(context: Context) {
    appContext = context.applicationContext
}

actual fun createSettingsRepository(): SettingsRepository {
    val context = appContext ?: throw IllegalStateException("Settings must be initialized with context")
    return AndroidSettingsRepository(context)
}
