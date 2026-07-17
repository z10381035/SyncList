package org.example.synclist

import kotlinx.browser.window

class JsSettingsRepository : SettingsRepository {
    override fun saveString(key: String, value: String) {
        window.localStorage.setItem(key, value)
    }

    override fun getString(key: String, defaultValue: String): String {
        return window.localStorage.getItem(key) ?: defaultValue
    }

    override fun saveBoolean(key: String, value: Boolean) {
        window.localStorage.setItem(key, value.toString())
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return window.localStorage.getItem(key)?.toBoolean() ?: defaultValue
    }

    override fun saveFloat(key: String, value: Float) {
        window.localStorage.setItem(key, value.toString())
    }

    override fun getFloat(key: String, defaultValue: Float): Float {
        return window.localStorage.getItem(key)?.toFloat() ?: defaultValue
    }

    override fun saveLong(key: String, value: Long) {
        window.localStorage.setItem(key, value.toString())
    }

    override fun getLong(key: String, defaultValue: Long): Long {
        return window.localStorage.getItem(key)?.toLong() ?: defaultValue
    }
}

actual fun createSettingsRepository(): SettingsRepository = JsSettingsRepository()
