package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ReaderPreferences
import com.example.data.local.ReaderReadingMode
import com.example.data.local.ReaderThemeMode
import com.example.reader.tts.AndroidTtsVoiceGateway
import com.example.reader.tts.TtsPlaybackManager
import com.example.reader.tts.TtsVoiceGateway
import com.example.reader.tts.TtsVoiceOption
import com.example.ui.components.ThemedThinSlider
import com.example.ui.components.inactiveTrackColor
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TTS_SAMPLE_PHRASE =
  "Листай читает эту фразу выбранным голосом. Между предложениями звучит естественная пауза."

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  preferences: ReaderPreferences,
  bookCount: Int,
  quoteCount: Int,
  onSelectTheme: (ReaderThemeMode) -> Unit,
  onSelectReadingMode: (ReaderReadingMode) -> Unit,
  onToggleDynamicColor: (Boolean) -> Unit,
  onToggleVolumePaging: (Boolean) -> Unit,
  onToggleKeepScreenOn: (Boolean) -> Unit,
  onTtsSpeedChanged: (Float) -> Unit = {},
  onTtsPitchChanged: (Float) -> Unit = {},
  onTtsVoiceSelected: (String?) -> Unit = {},
  voiceGateway: TtsVoiceGateway? = null,
  onOpenSystemTtsSettings: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val ownsGateway = voiceGateway == null
  val resolvedGateway = remember(voiceGateway, context) {
    voiceGateway ?: AndroidTtsVoiceGateway(context)
  }
  DisposableEffect(resolvedGateway, ownsGateway) {
    onDispose {
      if (ownsGateway) {
        resolvedGateway.release()
      } else {
        resolvedGateway.stopSample()
      }
    }
  }

  var showVoiceDialog by remember { mutableStateOf(false) }
  var isLoadingVoices by remember { mutableStateOf(false) }
  var availableVoices by remember { mutableStateOf<List<TtsVoiceOption>>(emptyList()) }
  var voiceLoadError by remember { mutableStateOf<String?>(null) }
  var onlyOfflineVoices by remember { mutableStateOf(true) }

  var isTestingVoice by remember { mutableStateOf(false) }
  var ttsErrorBanner by remember { mutableStateOf<String?>(null) }

  val loadVoicesAction: () -> Unit = {
    isLoadingVoices = true
    voiceLoadError = null
    resolvedGateway.loadRussianVoices { result ->
      isLoadingVoices = false
      result
        .onSuccess { voices ->
          availableVoices = voices
          voiceLoadError = null
        }
        .onFailure { err ->
          voiceLoadError = err.message ?: AndroidTtsVoiceGateway.MSG_ENGINE_UNAVAILABLE
          ttsErrorBanner = voiceLoadError
        }
    }
  }

  val openSystemTtsSettingsAction: () -> Unit = {
    if (onOpenSystemTtsSettings != null) {
      onOpenSystemTtsSettings()
    } else {
      val ttsIntent = Intent("com.android.settings.TTS_SETTINGS").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      val openedTts = runCatching { context.startActivity(ttsIntent) }.isSuccess
      if (!openedTts) {
        val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val openedFallback = runCatching { context.startActivity(fallbackIntent) }.isSuccess
        if (!openedFallback) {
          ttsErrorBanner = "Не удалось открыть системные настройки синтеза речи на этом устройстве."
        }
      }
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 760.dp)
        .verticalScroll(rememberScrollState())
        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Column {
        Text(
          text = "Настройки",
          style = MaterialTheme.typography.headlineMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "Палитра интерфейса и параметры чтения",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          StatBlock(
            value = bookCount.toString(),
            label = "Книг",
            modifier = Modifier.weight(1f),
          )
          StatBlock(
            value = quoteCount.toString(),
            label = "Цитат",
            modifier = Modifier.weight(1f),
          )
          StatBlock(
            value = preferences.readerTheme.title,
            label = "Тема",
            modifier = Modifier.weight(1f),
          )
        }
      }

      Text(
        text = "Тема приложения и чтения",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        ReaderThemeMode.entries.forEach { mode ->
          val selected = preferences.readerTheme == mode
          Surface(
            modifier = Modifier
              .weight(1f)
              .testTag("settings_theme_${mode.name.lowercase()}")
              .clickable { onSelectTheme(mode) },
            shape = RoundedCornerShape(14.dp),
            color = mode.background,
            border = BorderStroke(
              width = if (selected) 2.dp else 1.dp,
              color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
          ) {
            Column(
              modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                text = "Аа",
                style = MaterialTheme.typography.titleMedium,
                color = mode.text,
                maxLines = 1,
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = mode.title,
                style = MaterialTheme.typography.labelSmall,
                color = mode.mutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }

      Text(
        text = "Режим чтения",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        ReaderReadingMode.entries.forEach { mode ->
          val selected = preferences.readingMode == mode
          Surface(
            modifier = Modifier
              .weight(1f)
              .testTag("settings_reading_mode_${mode.name.lowercase()}")
              .clickable { onSelectReadingMode(mode) },
            shape = RoundedCornerShape(16.dp),
            color = if (selected) {
              MaterialTheme.colorScheme.primaryContainer
            } else {
              MaterialTheme.colorScheme.surfaceContainerLow
            },
            border = BorderStroke(
              width = if (selected) 2.dp else 1.dp,
              color = if (selected) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
              },
            ),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center,
            ) {
              Icon(
                imageVector = if (mode == ReaderReadingMode.PAGES) {
                  Icons.AutoMirrored.Filled.MenuBook
                } else {
                  Icons.Default.ViewDay
                },
                contentDescription = null,
                tint = if (selected) {
                  MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
              )
              Spacer(modifier = Modifier.widthIn(min = 8.dp).padding(start = 8.dp))
              Text(
                text = mode.title,
                style = MaterialTheme.typography.titleSmall.copy(
                  fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = if (selected) {
                  MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                  MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }

      Text(
        text = "Возможности Pixel и Android",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
      )

      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
      ) {
        Column {
          SettingSwitchRow(
            icon = Icons.Default.Palette,
            title = "Динамические цвета Material You",
            subtitle = "Подстраивает акценты под обои Pixel вместо темы книги",
            checked = preferences.useDynamicColor,
            onCheckedChange = onToggleDynamicColor,
            testTag = "switch_dynamic_color",
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
          SettingSwitchRow(
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            title = "Листание кнопками громкости",
            subtitle = "Удобное перелистывание глав/страниц одной рукой",
            checked = preferences.volumeKeyNavigation,
            onCheckedChange = onToggleVolumePaging,
            testTag = "switch_volume_paging",
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
          SettingSwitchRow(
            icon = Icons.Default.Brightness6,
            title = "Не выключать экран при чтении",
            subtitle = "Блокирует автовыключение дисплея, пока открыта книга",
            checked = preferences.keepScreenOn,
            onCheckedChange = onToggleKeepScreenOn,
            testTag = "switch_keep_screen_on",
          )
        }
      }

      Text(
        text = "Озвучка текста",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
      )

      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
      ) {
        val cardSurface = MaterialTheme.colorScheme.surfaceContainerLow
        val inactiveTrack = preferences.readerTheme.inactiveTrackColor(cardSurface)
        val clampedSpeed = preferences.ttsSpeed.coerceIn(0.7f, 1.3f)
        val clampedPitch = preferences.ttsPitch.coerceIn(0.7f, 1.3f)
        val selectedVoiceOption = remember(preferences.ttsVoiceName, availableVoices) {
          availableVoices.firstOrNull { it.name == preferences.ttsVoiceName }
        }
        val selectedVoiceLabel = when {
          preferences.ttsVoiceName.isNullOrBlank() -> "Системный голос по умолчанию (офлайн)"
          selectedVoiceOption != null -> {
            val modeLabel = if (selectedVoiceOption.isNetworkRequired) "Онлайн" else "Офлайн"
            "${selectedVoiceOption.displayName} • $modeLabel"
          }
          else -> preferences.ttsVoiceName
        }

        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Default.RecordVoiceOver,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Column(
              modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
            ) {
              Text(
                text = "Скорость речи",
                style = MaterialTheme.typography.titleSmall,
              )
              Text(
                text = "Чтение по предложениям от 0.7x до 1.3x",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.primaryContainer,
            ) {
              Text(
                text = String.format(Locale.US, "%.1fx", clampedSpeed),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                  .padding(horizontal = 10.dp, vertical = 4.dp)
                  .testTag("settings_tts_speed_value"),
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          ThemedThinSlider(
            value = clampedSpeed,
            onValueChange = { raw ->
              val snapped = (raw * 10f).roundToInt() / 10f
              onTtsSpeedChanged(snapped.coerceIn(0.7f, 1.3f))
            },
            valueRange = 0.7f..1.3f,
            activeColor = MaterialTheme.colorScheme.primary,
            inactiveColor = inactiveTrack,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("settings_tts_speed_slider"),
          )

          Spacer(modifier = Modifier.height(6.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            listOf(0.7f, 0.9f, 1.0f, 1.1f, 1.3f).forEach { preset ->
              val selected = abs(clampedSpeed - preset) < 0.05f
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (selected) {
                  MaterialTheme.colorScheme.primaryContainer
                } else {
                  MaterialTheme.colorScheme.surfaceContainerHigh
                },
                border = BorderStroke(
                  width = if (selected) 1.5.dp else 1.dp,
                  color = if (selected) {
                    MaterialTheme.colorScheme.primary
                  } else {
                    MaterialTheme.colorScheme.outlineVariant
                  },
                ),
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 48.dp)
                  .testTag("settings_tts_preset_${(preset * 10).roundToInt()}")
                  .clickable { onTtsSpeedChanged(preset) },
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(
                    text = String.format(Locale.US, "%.1fx", preset),
                    style = MaterialTheme.typography.labelMedium.copy(
                      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (selected) {
                      MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                      MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(vertical = 8.dp),
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
          Spacer(modifier = Modifier.height(14.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Default.GraphicEq,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Column(
              modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
            ) {
              Text(
                text = "Тон голоса",
                style = MaterialTheme.typography.titleSmall,
              )
              Text(
                text = "Высота тембра (по умолчанию 1.0x)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.primaryContainer,
            ) {
              Text(
                text = String.format(Locale.US, "%.1fx", clampedPitch),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                  .padding(horizontal = 10.dp, vertical = 4.dp)
                  .testTag("settings_tts_pitch_value"),
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          ThemedThinSlider(
            value = clampedPitch,
            onValueChange = { raw ->
              val snapped = (raw * 10f).roundToInt() / 10f
              onTtsPitchChanged(snapped.coerceIn(0.7f, 1.3f))
            },
            valueRange = 0.7f..1.3f,
            activeColor = MaterialTheme.colorScheme.primary,
            inactiveColor = inactiveTrack,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("settings_tts_pitch_slider"),
          )

          Spacer(modifier = Modifier.height(6.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            listOf(0.8f, 0.9f, 1.0f, 1.1f, 1.2f).forEach { preset ->
              val selected = abs(clampedPitch - preset) < 0.05f
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (selected) {
                  MaterialTheme.colorScheme.primaryContainer
                } else {
                  MaterialTheme.colorScheme.surfaceContainerHigh
                },
                border = BorderStroke(
                  width = if (selected) 1.5.dp else 1.dp,
                  color = if (selected) {
                    MaterialTheme.colorScheme.primary
                  } else {
                    MaterialTheme.colorScheme.outlineVariant
                  },
                ),
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 48.dp)
                  .testTag("settings_tts_pitch_preset_${(preset * 10).roundToInt()}")
                  .clickable { onTtsPitchChanged(preset) },
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(
                    text = String.format(Locale.US, "%.1fx", preset),
                    style = MaterialTheme.typography.labelMedium.copy(
                      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (selected) {
                      MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                      MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(vertical = 8.dp),
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
          Spacer(modifier = Modifier.height(14.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Default.Tune,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Column(
              modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
            ) {
              Text(
                text = "Голос озвучки",
                style = MaterialTheme.typography.titleSmall,
              )
              Text(
                text = selectedVoiceLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("settings_tts_selected_voice"),
              )
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Button(
              onClick = {
                onlyOfflineVoices = true
                showVoiceDialog = true
                loadVoicesAction()
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
              shape = RoundedCornerShape(14.dp),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("settings_tts_choose_voice_button"),
            ) {
              Icon(
                imageVector = Icons.Default.RecordVoiceOver,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Выбрать голос",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }

            OutlinedButton(
              onClick = {
                if (isTestingVoice) {
                  resolvedGateway.stopSample()
                  isTestingVoice = false
                } else {
                  TtsPlaybackManager.peek()?.pause()
                  ttsErrorBanner = null
                  isTestingVoice = true
                  resolvedGateway.speakSample(
                    text = TTS_SAMPLE_PHRASE,
                    speechRate = clampedSpeed,
                    pitch = clampedPitch,
                    voiceName = preferences.ttsVoiceName,
                  ) { result ->
                    isTestingVoice = false
                    result.onFailure { err ->
                      ttsErrorBanner = err.message ?: AndroidTtsVoiceGateway.MSG_ENGINE_UNAVAILABLE
                    }
                  }
                }
              },
              colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
              ),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
              shape = RoundedCornerShape(14.dp),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("settings_tts_test_voice_button"),
            ) {
              Icon(
                imageVector = if (isTestingVoice) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (isTestingVoice) "Остановить" else "Проверить голос",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          OutlinedButton(
            onClick = openSystemTtsSettingsAction,
            colors = ButtonDefaults.outlinedButtonColors(
              contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .testTag("settings_tts_system_settings_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Settings,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Открыть системные настройки синтеза речи",
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              textAlign = TextAlign.Center,
            )
          }

          val activeBanner = ttsErrorBanner
          if (!activeBanner.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.errorContainer,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_tts_error_banner"),
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.ErrorOutline,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                  text = activeBanner,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }
        }
      }

      Text(
        text = "Поддерживаемые форматы",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
      )

      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Text(
            text = "Локальный движок без облака",
            style = MaterialTheme.typography.titleSmall,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Все книги и заметки хранятся только на вашем телефоне. Можно открывать файлы напрямую из Telegram или приложения «Файлы».",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(12.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            listOf("EPUB", "FB2 / FB2.ZIP", "TXT (UTF-8 / CP1251)", "PDF").forEach { fmt ->
              Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(10.dp),
              ) {
                Text(
                  text = fmt,
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))
    }
  }

  if (showVoiceDialog) {
    TtsVoicePickerDialog(
      voices = availableVoices,
      selectedVoiceName = preferences.ttsVoiceName,
      onlyOffline = onlyOfflineVoices,
      isLoading = isLoadingVoices,
      errorMessage = voiceLoadError,
      onToggleOnlyOffline = { onlyOfflineVoices = it },
      onSelectVoice = { chosen ->
        onTtsVoiceSelected(chosen)
        ttsErrorBanner = null
        showVoiceDialog = false
      },
      onRetry = loadVoicesAction,
      onOpenSystemTtsSettings = {
        showVoiceDialog = false
        openSystemTtsSettingsAction()
      },
      onDismiss = { showVoiceDialog = false },
    )
  }
}

@Composable
private fun StatBlock(
  value: String,
  label: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = value,
      style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun SettingSwitchRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  testTag: String,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
    )
    Column(
      modifier = Modifier
        .weight(1f)
        .padding(horizontal = 14.dp),
    ) {
      Text(text = title, style = MaterialTheme.typography.titleSmall)
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        checkedBorderColor = MaterialTheme.colorScheme.primary,
        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        uncheckedBorderColor = MaterialTheme.colorScheme.outline,
      ),
      modifier = Modifier.testTag(testTag),
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TtsVoicePickerDialog(
  voices: List<TtsVoiceOption>,
  selectedVoiceName: String?,
  onlyOffline: Boolean,
  isLoading: Boolean,
  errorMessage: String?,
  onToggleOnlyOffline: (Boolean) -> Unit,
  onSelectVoice: (String?) -> Unit,
  onRetry: () -> Unit,
  onOpenSystemTtsSettings: () -> Unit,
  onDismiss: () -> Unit,
) {
  val filteredVoices = remember(voices, onlyOffline) {
    if (onlyOffline) voices.filter { !it.isNetworkRequired } else voices
  }
  val hasOnlineVoices = remember(voices) { voices.any { it.isNetworkRequired } }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "Русские голоса",
          style = MaterialTheme.typography.titleLarge,
        )
        TextButton(onClick = onOpenSystemTtsSettings) {
          Text("Движок TTS")
        }
      }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          FilterChip(
            selected = onlyOffline,
            onClick = { onToggleOnlyOffline(!onlyOffline) },
            label = {
              Text(
                text = if (onlyOffline) "Только офлайн" else "Показаны все голоса",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
            leadingIcon = if (onlyOffline) {
              {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              }
            } else {
              {
                Icon(
                  imageVector = Icons.Default.CloudQueue,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              }
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
              selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
              containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
              labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
              iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("voice_picker_offline_chip"),
          )
        }

        when {
          isLoading -> {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
              )
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                text = "Загружаем голоса...",
                style = MaterialTheme.typography.bodyMedium,
              )
            }
          }

          !errorMessage.isNullOrBlank() -> {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.errorContainer,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("voice_picker_error"),
            ) {
              Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  text = errorMessage,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  TextButton(onClick = onRetry) {
                    Text("Повторить")
                  }
                  TextButton(onClick = onOpenSystemTtsSettings) {
                    Text("Системные настройки")
                  }
                }
              }
            }
          }

          else -> {
            LazyColumn(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .testTag("voice_picker_list"),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              item(key = "__default__") {
                val isSelected = selectedVoiceName.isNullOrBlank()
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                  } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                  },
                  border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) {
                      MaterialTheme.colorScheme.primary
                    } else {
                      MaterialTheme.colorScheme.outlineVariant
                    },
                  ),
                  modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("voice_option_default")
                    .clickable { onSelectVoice(null) },
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = "Системный голос по умолчанию",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isSelected) {
                          MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                          MaterialTheme.colorScheme.onSurface
                        },
                      )
                      Text(
                        text = "Автовыбор офлайн-голоса системы",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    if (isSelected) {
                      Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Выбран",
                        tint = MaterialTheme.colorScheme.primary,
                      )
                    }
                  }
                }
              }

              if (filteredVoices.isEmpty()) {
                item(key = "__empty__") {
                  Text(
                    text = if (onlyOffline && hasOnlineVoices) {
                      "Офлайн-голосов на русском не найдено. Снимите фильтр «Только офлайн» или загрузите голос в системных настройках."
                    } else {
                      "Русские голоса в текущем движке не найдены. Откройте системные настройки синтеза речи, чтобы выбрать другой движок или загрузить русский пакет."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                      .padding(vertical = 8.dp)
                      .testTag("voice_picker_empty"),
                  )
                }
              } else {
                items(filteredVoices, key = { it.name }) { voice ->
                  val isSelected = selectedVoiceName == voice.name
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) {
                      MaterialTheme.colorScheme.primaryContainer
                    } else {
                      MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                      width = if (isSelected) 1.5.dp else 1.dp,
                      color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                      } else {
                        MaterialTheme.colorScheme.outlineVariant
                      },
                    ),
                    modifier = Modifier
                      .fillMaxWidth()
                      .heightIn(min = 48.dp)
                      .testTag("voice_option_${voice.name}")
                      .clickable { onSelectVoice(voice.name) },
                  ) {
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = voice.displayName,
                          style = MaterialTheme.typography.titleSmall,
                          color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                          } else {
                            MaterialTheme.colorScheme.onSurface
                          },
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                          text = "${voice.name} • Качество: ${voice.qualityLabel}",
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          maxLines = 2,
                          overflow = TextOverflow.Ellipsis,
                        )
                      }
                      Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (voice.isNetworkRequired) {
                          MaterialTheme.colorScheme.secondaryContainer
                        } else {
                          MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = if (voice.isNetworkRequired) {
                          Modifier.testTag("voice_online_badge_${voice.name}")
                        } else {
                          Modifier
                        },
                      ) {
                        Row(
                          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                          if (voice.isNetworkRequired) {
                            Icon(
                              imageVector = Icons.Default.CloudQueue,
                              contentDescription = "Требует сеть",
                              tint = MaterialTheme.colorScheme.onSecondaryContainer,
                              modifier = Modifier.size(14.dp),
                            )
                          }
                          Text(
                            text = if (voice.isNetworkRequired) "Онлайн" else "Офлайн",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (voice.isNetworkRequired) {
                              MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                              MaterialTheme.colorScheme.onSurfaceVariant
                            },
                          )
                        }
                      }
                      if (isSelected) {
                        Icon(
                          imageVector = Icons.Default.Check,
                          contentDescription = "Выбран",
                          tint = MaterialTheme.colorScheme.primary,
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
  }
}
