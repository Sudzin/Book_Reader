package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ReaderPreferences
import com.example.data.local.ReaderReadingMode
import com.example.data.local.ReaderThemeMode
import com.example.ui.components.ThemedThinSlider
import com.example.ui.components.inactiveTrackColor
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

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
  modifier: Modifier = Modifier,
) {
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
            icon = Icons.Default.VolumeUp,
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
                text = "Чтение вслух на экране блокировки и в фоне",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.primaryContainer,
            ) {
              Text(
                text = String.format(Locale.US, "%.1fx", preferences.ttsSpeed),
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
            value = preferences.ttsSpeed,
            onValueChange = { raw ->
              val snapped = (raw * 10f).roundToInt() / 10f
              onTtsSpeedChanged(snapped.coerceIn(0.7f, 2.0f))
            },
            valueRange = 0.7f..2.0f,
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
            listOf(0.8f, 1.0f, 1.2f, 1.5f, 1.8f).forEach { preset ->
              val selected = abs(preferences.ttsSpeed - preset) < 0.06f
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
                  .testTag("settings_tts_preset_${(preset * 10).roundToInt()}")
                  .clickable { onTtsSpeedChanged(preset) },
              ) {
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
