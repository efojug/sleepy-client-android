package com.efojug.sleepy.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

object PreferencesManager {
    private val KEY_URL = stringPreferencesKey("key_url")
    private val KEY_SECRET = stringPreferencesKey("key_secret")
    private val KEY_DEVICE = intPreferencesKey("key_device")

    suspend fun saveConfig(context: Context, url: String, secret: String, deviceId: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_URL] = url
            prefs[KEY_SECRET] = secret
            prefs[KEY_DEVICE] = deviceId
        }
    }

    fun urlFlow(context: Context): Flow<String> =
        context.dataStore.data.map { it[KEY_URL] ?: "" }

    fun secretFlow(context: Context): Flow<String> =
        context.dataStore.data.map { it[KEY_SECRET] ?: "" }

    fun deviceFlow(context: Context): Flow<Int> =
        context.dataStore.data.map { it[KEY_DEVICE] ?: 0 }
}