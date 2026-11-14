package com.example.data.local

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LiterataFontFamily
import com.example.ui.theme.LoraFontFamily
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerDataStore: DataStore<Preferences> by preferencesDataStore(name = "reader_prefs")

enum class ReaderThemeMode(
  val title: String,
  val background: Color,
  val surface: Color,
  val text: Color,
  val mutedText: Color,
  val accent: Color,
  val isDark: Boolean,
) {
  PAPER(
    title = "Бумага",
    background = Color(0xFFFAF7F2),
    surface = Color(0xFFF0EAE0),
    text = Color(0xFF1C1B19),
    mutedText = Color(0xFF6B6359),
    accent = Color(0xFF9C422E),
    isDark = false,
  ),
  SEPIA(
    title = "Сепия",
    background = Color(0xFFF3E7D0),
    surface = Color(0xFFE7D6B8),
    text = Color(0xFF332619),
    mutedText = Color(0xFF705A43),
    accent = Color(0xFF8B3E1F),
    isDark = false,
  ),
  NIGHT(
    title = "Ночь",
    background = Color(0xFF1B1C20),
    surface = Color(0xFF26282E),
    text = Color(0xFFE3DFD6),
    mutedText = Color(0xFF9E998E),
    accent = Color(0xFFE69A88),
    isDark = true,
  ),
  OLED(
    title = "OLED",
    background = Color(0xFF000000),
    surface = Color(0xFF141414),
    text = Color(0xFFD9D5CC),
    mutedText = Color(0xFF8C877E),
    accent = Color(0xFFE69A88),
    isDark = true,
  ),
}

enum class ReaderFontOption(val title: String, val fontFamily: FontFamily) {
  LITERATA("Literata", LiterataFontFamily),
  LORA("Lora", LoraFontFamily),
  INTER("Inter", InterFontFamily),
  JETBRAINS_MONO("Mono", JetBrainsMonoFontFamily),
}

data class ReaderPreferences(
  val readerTheme: ReaderThemeMode = ReaderThemeMode.PAPER,
  val fontOption: ReaderFontOption = ReaderFontOption.LITERATA,
  val fontSizeSp: Float = 19f,
  val lineHeightMultiplier: Float = 1.55f,
  val horizontalMarginDp: Int = 20,
  val justifyText: Boolean = true,
  val useDynamicColor: Boolean = true,
  val volumeKeyNavigation: Boolean = true,
  val keepScreenOn: Boolean = true,
  val ttsSpeed: Float = 1.0f,
)

class PreferencesRepository(private val context: Context) {
  private object Keys {
    val THEME = stringPreferencesKey("reader_theme")
    val FONT = stringPreferencesKey("reader_font")
    val FONT_SIZE = floatPreferencesKey("font_size_sp")
    val LINE_HEIGHT = floatPreferencesKey("line_height_mult")
    val MARGIN_DP = intPreferencesKey("margin_dp")
    val JUSTIFY = booleanPreferencesKey("justify_text")
    val DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
    val VOLUME_NAV = booleanPreferencesKey("volume_key_nav")
    val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    val TTS_SPEED = floatPreferencesKey("tts_speed")
  }

  val preferencesFlow: Flow<ReaderPreferences> = context.readerDataStore.data.map { prefs ->
    ReaderPreferences(
      readerTheme = prefs[Keys.THEME]?.let { name ->
        ReaderThemeMode.entries.firstOrNull { it.name == name }
      } ?: ReaderThemeMode.PAPER,
      fontOption = prefs[Keys.FONT]?.let { name ->
        ReaderFontOption.entries.firstOrNull { it.name == name }
      } ?: ReaderFontOption.LITERATA,
      fontSizeSp = (prefs[Keys.FONT_SIZE] ?: 19f).coerceIn(14f, 28f),
      lineHeightMultiplier = (prefs[Keys.LINE_HEIGHT] ?: 1.55f).coerceIn(1.2f, 2.0f),
      horizontalMarginDp = (prefs[Keys.MARGIN_DP] ?: 20).coerceIn(12, 36),
      justifyText = prefs[Keys.JUSTIFY] ?: true,
      useDynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
      volumeKeyNavigation = prefs[Keys.VOLUME_NAV] ?: true,
      keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
      ttsSpeed = (prefs[Keys.TTS_SPEED] ?: 1.0f).coerceIn(0.7f, 2.0f),
    )
  }

  suspend fun updateTheme(mode: ReaderThemeMode) {
    context.readerDataStore.edit { it[Keys.THEME] = mode.name }
  }

  suspend fun updateFont(option: ReaderFontOption) {
    context.readerDataStore.edit { it[Keys.FONT] = option.name }
  }

  suspend fun updateFontSize(sizeSp: Float) {
    context.readerDataStore.edit { it[Keys.FONT_SIZE] = sizeSp.coerceIn(14f, 28f) }
  }

  suspend fun updateLineHeight(multiplier: Float) {
    context.readerDataStore.edit { it[Keys.LINE_HEIGHT] = multiplier.coerceIn(1.2f, 2.0f) }
  }

  suspend fun updateHorizontalMargin(marginDp: Int) {
    context.readerDataStore.edit { it[Keys.MARGIN_DP] = marginDp.coerceIn(12, 36) }
  }

  suspend fun updateJustifyText(justify: Boolean) {
    context.readerDataStore.edit { it[Keys.JUSTIFY] = justify }
  }

  suspend fun updateDynamicColor(enabled: Boolean) {
    context.readerDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
  }

  suspend fun updateVolumeKeyNavigation(enabled: Boolean) {
    context.readerDataStore.edit { it[Keys.VOLUME_NAV] = enabled }
  }

  suspend fun updateKeepScreenOn(enabled: Boolean) {
    context.readerDataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
  }

  suspend fun updateTtsSpeed(speed: Float) {
    context.readerDataStore.edit { it[Keys.TTS_SPEED] = speed.coerceIn(0.7f, 2.0f) }
  }
}
