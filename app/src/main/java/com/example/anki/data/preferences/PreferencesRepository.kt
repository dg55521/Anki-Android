package com.example.anki.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.anki.data.model.PressureSensitivity
import com.example.anki.data.model.SchedulerMode
import com.example.anki.data.model.ThemeMode
import com.example.anki.data.model.WhiteboardBrushType
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
    val whiteboardStylusOnly: Boolean = false,
    val whiteboardBrushType: WhiteboardBrushType = WhiteboardBrushType.FOUNTAIN_PEN,
    val whiteboardStrokeWidthDp: Float = 5.0f,
    val whiteboardOpacity: Float = 1.0f,
    val whiteboardPressureEnabled: Boolean = true,
    val whiteboardPressureSensitivity: PressureSensitivity = PressureSensitivity.STANDARD,
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
        val WB_STYLUS_ONLY = booleanPreferencesKey("wb_stylus_only")
        val WB_BRUSH_TYPE = stringPreferencesKey("wb_brush_type")
        val WB_STROKE_WIDTH_DP = floatPreferencesKey("wb_stroke_width_dp")
        val WB_OPACITY = floatPreferencesKey("wb_opacity")
        val WB_PRESSURE_ENABLED = booleanPreferencesKey("wb_pressure_enabled")
        val WB_PRESSURE_SENSITIVITY = stringPreferencesKey("wb_pressure_sensitivity")
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
            whiteboardStylusOnly = prefs[Keys.WB_STYLUS_ONLY] ?: false,
            whiteboardBrushType = prefs[Keys.WB_BRUSH_TYPE]?.let {
                runCatching { WhiteboardBrushType.valueOf(it) }.getOrNull()
            } ?: WhiteboardBrushType.FOUNTAIN_PEN,
            whiteboardStrokeWidthDp = prefs[Keys.WB_STROKE_WIDTH_DP] ?: 5.0f,
            whiteboardOpacity = prefs[Keys.WB_OPACITY] ?: 1.0f,
            whiteboardPressureEnabled = prefs[Keys.WB_PRESSURE_ENABLED] ?: true,
            whiteboardPressureSensitivity = prefs[Keys.WB_PRESSURE_SENSITIVITY]?.let {
                runCatching { PressureSensitivity.valueOf(it) }.getOrNull()
            } ?: PressureSensitivity.STANDARD,
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

    suspend fun setWhiteboardStylusOnly(stylusOnly: Boolean) {
        dataStore.edit { it[Keys.WB_STYLUS_ONLY] = stylusOnly }
    }

    suspend fun setWhiteboardBrushType(brushType: WhiteboardBrushType) {
        dataStore.edit { it[Keys.WB_BRUSH_TYPE] = brushType.name }
    }

    suspend fun setWhiteboardStrokeWidthDp(widthDp: Float) {
        dataStore.edit { it[Keys.WB_STROKE_WIDTH_DP] = widthDp.coerceIn(1.5f, 24f) }
    }

    suspend fun setWhiteboardOpacity(opacity: Float) {
        dataStore.edit { it[Keys.WB_OPACITY] = opacity.coerceIn(0.15f, 1.0f) }
    }

    suspend fun setWhiteboardPressureEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.WB_PRESSURE_ENABLED] = enabled }
    }

    suspend fun setWhiteboardPressureSensitivity(sensitivity: PressureSensitivity) {
        dataStore.edit { it[Keys.WB_PRESSURE_SENSITIVITY] = sensitivity.name }
    }
}
