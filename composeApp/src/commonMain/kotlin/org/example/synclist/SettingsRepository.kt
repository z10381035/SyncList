package org.example.synclist

import androidx.compose.runtime.Composable

interface SettingsRepository {
    fun saveString(key: String, value: String)
    fun getString(key: String, defaultValue: String): String
    fun saveBoolean(key: String, value: Boolean)
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun saveFloat(key: String, value: Float)
    fun getFloat(key: String, defaultValue: Float): Float
    fun saveLong(key: String, value: Long)
    fun getLong(key: String, defaultValue: Long): Long
}

expect fun createSettingsRepository(): SettingsRepository

object SettingsProvider {
    private var instance: SettingsRepository? = null

    fun initialize(settings: SettingsRepository) {
        instance = settings
    }

    fun get(): SettingsRepository {
        return instance ?: throw IllegalStateException("SettingsProvider not initialized")
    }
}
