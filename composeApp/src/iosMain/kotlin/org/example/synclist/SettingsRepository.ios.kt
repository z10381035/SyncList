package org.example.synclist

import platform.Foundation.NSUserDefaults

class IosSettingsRepository : SettingsRepository {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun saveString(key: String, value: String) {
        defaults.setObject(value, key)
    }

    override fun getString(key: String, defaultValue: String): String {
        return defaults.stringForKey(key) ?: defaultValue
    }

    override fun saveBoolean(key: String, value: Boolean) {
        defaults.setBool(value, key)
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        // NSUserDefaults.boolForKey returns false if key doesn't exist. 
        // To handle default value properly, we check if object exists.
        return if (defaults.objectForKey(key) != null) defaults.boolForKey(key) else defaultValue
    }

    override fun saveFloat(key: String, value: Float) {
        defaults.setFloat(value, key)
    }

    override fun getFloat(key: String, defaultValue: Float): Float {
        return if (defaults.objectForKey(key) != null) defaults.floatForKey(key) else defaultValue
    }

    override fun saveLong(key: String, value: Long) {
        defaults.setInteger(value, key)
    }

    override fun getLong(key: String, defaultValue: Long): Long {
        return if (defaults.objectForKey(key) != null) defaults.integerForKey(key) else defaultValue
    }
}

actual fun createSettingsRepository(): SettingsRepository = IosSettingsRepository()
