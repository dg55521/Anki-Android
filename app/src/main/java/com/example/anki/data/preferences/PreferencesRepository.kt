package com.example.anki.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.anki.data.model.SchedulerMode
import com.example.anki.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ankiDataStore: DataStore<Preferences> by preferencesDataStore(name = "ankidroid_prefs")

data class UserPreferences(
    val schedulerMode: SchedulerMode = SchedulerMode.FSRS,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showNextTimes: Boolean = true,
    val showStudyTimer: Boolean = true,
    val timeboxMinutes: Int = 0,
    val ankiWebAccount: String = "",
    val lastSyncAtMillis: Long? = null,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
)

class PreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.ankiDataStore

    private object Keys {
        val SCHEDULER_MODE = stringPreferencesKey("scheduler_mode")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SHOW_NEXT_TIMES = booleanPreferencesKey("show_next_times")
        val SHOW_STUDY_TIMER = booleanPreferencesKey("show_study_timer")
        val TIMEBOX_MINUTES = intPreferencesKey("timebox_minutes")
        val ANKIWEB_ACCOUNT = stringPreferencesKey("ankiweb_account")
        val LAST_SYNC_MILLIS = longPreferencesKey("last_sync_millis")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
    }

    val userPreferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            schedulerMode = prefs[Keys.SCHEDULER_MODE]?.let {
                runCatching { SchedulerMode.valueOf(it) }.getOrNull()
            } ?: SchedulerMode.FSRS,
            themeMode = prefs[Keys.THEME_MODE]?.let {
                runCatching { ThemeMode.valueOf(it) }.getOrNull()
            } ?: ThemeMode.SYSTEM,
            showNextTimes = prefs[Keys.SHOW_NEXT_TIMES] ?: true,
            showStudyTimer = prefs[Keys.SHOW_STUDY_TIMER] ?: true,
            timeboxMinutes = prefs[Keys.TIMEBOX_MINUTES] ?: 0,
            ankiWebAccount = prefs[Keys.ANKIWEB_ACCOUNT] ?: "",
            lastSyncAtMillis = prefs[Keys.LAST_SYNC_MILLIS],
            reminderEnabled = prefs[Keys.REMINDER_ENABLED] ?: false,
            reminderHour = prefs[Keys.REMINDER_HOUR] ?: 20,
            reminderMinute = prefs[Keys.REMINDER_MINUTE] ?: 0,
        )
    }

    suspend fun setSchedulerMode(mode: SchedulerMode) {
        dataStore.edit { it[Keys.SCHEDULER_MODE] = mode.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setShowNextTimes(show: Boolean) {
        dataStore.edit { it[Keys.SHOW_NEXT_TIMES] = show }
    }

    suspend fun setShowStudyTimer(show: Boolean) {
        dataStore.edit { it[Keys.SHOW_STUDY_TIMER] = show }
    }

    suspend fun setTimeboxMinutes(minutes: Int) {
        dataStore.edit { it[Keys.TIMEBOX_MINUTES] = minutes.coerceIn(0, 120) }
    }

    suspend fun setAnkiWebAccount(email: String) {
        dataStore.edit {
            it[Keys.ANKIWEB_ACCOUNT] = email.trim()
            if (email.isNotBlank()) {
                it[Keys.LAST_SYNC_MILLIS] = System.currentTimeMillis()
            }
        }
    }

    suspend fun recordSyncNow() {
        dataStore.edit { it[Keys.LAST_SYNC_MILLIS] = System.currentTimeMillis() }
    }

    suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        dataStore.edit {
            it[Keys.REMINDER_ENABLED] = enabled
            it[Keys.REMINDER_HOUR] = hour
            it[Keys.REMINDER_MINUTE] = minute
        }
    }
}
