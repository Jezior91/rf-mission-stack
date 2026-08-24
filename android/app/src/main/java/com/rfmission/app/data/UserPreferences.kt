package com.rfmission.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("rfmission_prefs")

class UserPreferences(private val context: Context) {
    val allPrefs: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        prefs.asMap().entries.associate { (k, v) -> k.name to v.toString() }
    }
    suspend fun saveAll(map: Map<String, String>) {
        context.dataStore.edit { prefs ->
            map.forEach { (k, v) -> prefs[stringPreferencesKey(k)] = v }
        }
    }
}
