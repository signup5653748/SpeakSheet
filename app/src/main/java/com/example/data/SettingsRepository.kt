package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class InteractionMode {
    SINGLE_TAP_SPEAK_DOUBLE_TAP_EDIT,
    SINGLE_TAP_SPEAK_DOUBLE_TAP_MENU,
    SINGLE_TAP_SPEAK_LONG_PRESS_MENU
}

data class AppSettings(
    val voiceName: String = "",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val interactionMode: InteractionMode = InteractionMode.SINGLE_TAP_SPEAK_LONG_PRESS_MENU,
    val speakRowNumber: Boolean = true,
    val speakColumnName: Boolean = true,
    val speakFormulas: Boolean = false,
    val speakFormatting: Boolean = false,
    val speakEmptyCells: Boolean = true,
    val speakAfterEditing: Boolean = true,
    val vibrateOnSelect: Boolean = true,
    val largeTouchMode: Boolean = true,
    val highContrastGrid: Boolean = false,
    val defaultRows: Int = 1000,
    val defaultCols: Int = 26
)

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val VOICE_NAME = stringPreferencesKey("voice_name")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val PITCH = floatPreferencesKey("pitch")
        val INTERACTION_MODE = intPreferencesKey("interaction_mode")
        val SPEAK_ROW_NUM = booleanPreferencesKey("speak_row_num")
        val SPEAK_COL_NAME = booleanPreferencesKey("speak_col_name")
        val SPEAK_FORMULAS = booleanPreferencesKey("speak_formulas")
        val SPEAK_FORMATTING = booleanPreferencesKey("speak_formatting")
        val SPEAK_EMPTY = booleanPreferencesKey("speak_empty")
        val SPEAK_AFTER_EDIT = booleanPreferencesKey("speak_after_edit")
        val VIBRATE_ON_SELECT = booleanPreferencesKey("vibrate_on_select")
        val LARGE_TOUCH = booleanPreferencesKey("large_touch")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val DEFAULT_ROWS = intPreferencesKey("default_rows")
        val DEFAULT_COLS = intPreferencesKey("default_cols")
    }

    val appSettingsFlow: Flow<AppSettings> = context.dataStore.data
        .map { preferences ->
            AppSettings(
                voiceName = preferences[PreferencesKeys.VOICE_NAME] ?: "",
                speechRate = preferences[PreferencesKeys.SPEECH_RATE] ?: 1.0f,
                pitch = preferences[PreferencesKeys.PITCH] ?: 1.0f,
                interactionMode = InteractionMode.values()[preferences[PreferencesKeys.INTERACTION_MODE] ?: InteractionMode.SINGLE_TAP_SPEAK_LONG_PRESS_MENU.ordinal],
                speakRowNumber = preferences[PreferencesKeys.SPEAK_ROW_NUM] ?: true,
                speakColumnName = preferences[PreferencesKeys.SPEAK_COL_NAME] ?: true,
                speakFormulas = preferences[PreferencesKeys.SPEAK_FORMULAS] ?: false,
                speakFormatting = preferences[PreferencesKeys.SPEAK_FORMATTING] ?: false,
                speakEmptyCells = preferences[PreferencesKeys.SPEAK_EMPTY] ?: true,
                speakAfterEditing = preferences[PreferencesKeys.SPEAK_AFTER_EDIT] ?: true,
                vibrateOnSelect = preferences[PreferencesKeys.VIBRATE_ON_SELECT] ?: true,
                largeTouchMode = preferences[PreferencesKeys.LARGE_TOUCH] ?: true,
                highContrastGrid = preferences[PreferencesKeys.HIGH_CONTRAST] ?: false,
                defaultRows = preferences[PreferencesKeys.DEFAULT_ROWS] ?: 1000,
                defaultCols = preferences[PreferencesKeys.DEFAULT_COLS] ?: 26
            )
        }

    suspend fun updateSettings(settings: AppSettings) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.VOICE_NAME] = settings.voiceName
            preferences[PreferencesKeys.SPEECH_RATE] = settings.speechRate
            preferences[PreferencesKeys.PITCH] = settings.pitch
            preferences[PreferencesKeys.INTERACTION_MODE] = settings.interactionMode.ordinal
            preferences[PreferencesKeys.SPEAK_ROW_NUM] = settings.speakRowNumber
            preferences[PreferencesKeys.SPEAK_COL_NAME] = settings.speakColumnName
            preferences[PreferencesKeys.SPEAK_FORMULAS] = settings.speakFormulas
            preferences[PreferencesKeys.SPEAK_FORMATTING] = settings.speakFormatting
            preferences[PreferencesKeys.SPEAK_EMPTY] = settings.speakEmptyCells
            preferences[PreferencesKeys.SPEAK_AFTER_EDIT] = settings.speakAfterEditing
            preferences[PreferencesKeys.VIBRATE_ON_SELECT] = settings.vibrateOnSelect
            preferences[PreferencesKeys.LARGE_TOUCH] = settings.largeTouchMode
            preferences[PreferencesKeys.HIGH_CONTRAST] = settings.highContrastGrid
            preferences[PreferencesKeys.DEFAULT_ROWS] = settings.defaultRows
            preferences[PreferencesKeys.DEFAULT_COLS] = settings.defaultCols
        }
    }
}
