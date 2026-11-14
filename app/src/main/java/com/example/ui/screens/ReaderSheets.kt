package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.BookmarkEntity
import com.example.data.local.QuoteEntity
import com.example.data.local.ReaderFontOption
import com.example.data.local.ReaderPreferences
import com.example.data.local.ReaderThemeMode
import com.example.reader.engine.TocEntry
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderAppearanceSheet(
  preferences: ReaderPreferences,
  onDismiss: () -> Unit,
  onThemeSelected: (ReaderThemeMode) -> Unit,
  onFontSelected: (ReaderFontOption) -> Unit,
  onFontSizeChanged: (Float) -> Unit,
  onLineHeightChanged: (Float) -> Unit,
  onMarginChanged: (Int) -> Unit,
  onJustifyChanged: (Boolean) -> Unit,
  onVolumeNavChanged: (Boolean) -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        text = "Оформление страницы",
        style = MaterialTheme.typography.titleLarge,
      )

      Text(text = "Тема чтения", style = MaterialTheme.typography.labelLarge)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        ReaderThemeMode.entries.forEach { mode ->
          val selected = preferences.readerTheme == mode
          Surface(
            modifier = Modifier
              .weight(1f)
              .testTag("theme_${mode.name.lowercase()}")
              .clickable { onThemeSelected(mode) },
            shape = RoundedCornerShape(14.dp),
            color = mode.background,
            border = BorderStroke(
              width = if (selected) 2.5.dp else 1.dp,
              color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
          ) {
            Column(
              modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                text = "Аа",
                style = MaterialTheme.typography.titleMedium,
                color = mode.text,
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = mode.title,
                style = MaterialTheme.typography.labelSmall,
                color = mode.mutedText,
                maxLines = 1,
              )
            }
          }
        }
      }

      Text(text = "Шрифт", style = MaterialTheme.typography.labelLarge)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        ReaderFontOption.entries.forEach { option ->
          FilterChip(
            selected = preferences.fontOption == option,
            onClick = { onFontSelected(option) },
            label = {
              Text(
                text = option.title,
                fontFamily = option.fontFamily,
                maxLines = 1,
              )
            },
            modifier = Modifier.weight(1f),
          )
        }
      }

      Column {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text("Размер текста", style = MaterialTheme.typography.labelLarge)
          Text(
            "${preferences.fontSizeSp.roundToInt()} pt",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        Slider(
          value = preferences.fontSizeSp,
          onValueChange = onFontSizeChanged,
          valueRange = 14f..28f,
          steps = 13,
        )
      }

      Column {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text("Межстрочный интервал", style = MaterialTheme.typography.labelLarge)
          Text(
            String.format("%.2fx", preferences.lineHeightMultiplier),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        Slider(
          value = preferences.lineHeightMultiplier,
          onValueChange = onLineHeightChanged,
          valueRange = 1.2f..2.0f,
          steps = 7,
        )
      }

      Column {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text("Поля страницы", style = MaterialTheme.typography.labelLarge)
          Text(
            "${preferences.horizontalMarginDp} dp",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        Slider(
          value = preferences.horizontalMarginDp.toFloat(),
          onValueChange = { onMarginChanged(it.roundToInt()) },
          valueRange = 12f..36f,
          steps = 5,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text("Выравнивание по ширине", style = MaterialTheme.typography.bodyLarge)
        Switch(
          checked = preferences.justifyText,
          onCheckedChange = onJustifyChanged,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text("Листание кнопками громкости", style = MaterialTheme.typography.bodyLarge)
        Switch(
          checked = preferences.volumeKeyNavigation,
          onCheckedChange = onVolumeNavChanged,
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookNavigationSheet(
  toc: List<TocEntry>,
  currentUnitIndex: Int,
  bookmarks: List<BookmarkEntity>,
  quotes: List<QuoteEntity>,
  onDismiss: () -> Unit,
  onSelectUnit: (unitIndex: Int, paragraphIndex: Int) -> Unit,
  onDeleteBookmark: (Long) -> Unit,
  onDeleteQuote: (Long) -> Unit,
) {
  var selectedTab by remember { mutableIntStateOf(0) }
  val context = LocalContext.current

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight(0.75f),
    ) {
      TabRow(selectedTabIndex = selectedTab) {
        Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = { Text("Оглавление (${toc.size})") },
        )
        Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = { Text("Закладки (${bookmarks.size})") },
        )
        Tab(
          selected = selectedTab == 2,
          onClick = { selectedTab = 2 },
          text = { Text("Цитаты (${quotes.size})") },
        )
      }

      when (selectedTab) {
        0 -> {
          LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            items(toc) { entry ->
              val isActive = entry.unitIndex == currentUnitIndex
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isActive) {
                  MaterialTheme.colorScheme.primaryContainer
                } else {
                  Color.Transparent
                },
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    onSelectUnit(entry.unitIndex, 0)
                    onDismiss()
                  },
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                      fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (isActive) {
                      MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                      MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                  )
                  if (isActive) {
                    Icon(
                      imageVector = Icons.Default.Check,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(18.dp),
                    )
                  }
                }
              }
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
          }
        }

        1 -> {
          if (bookmarks.isEmpty()) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(36.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = "Закладок пока нет. Нажмите на значок закладки в верхней панели.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            LazyColumn(
              modifier = Modifier.fillMaxWidth(),
              contentPadding = PaddingValues(16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              items(bookmarks, key = { it.id }) { bm ->
                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color = MaterialTheme.colorScheme.surfaceContainerLow,
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                      onSelectUnit(bm.unitIndex, bm.paragraphIndex)
                      onDismiss()
                    },
                ) {
                  Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Icon(
                      imageVector = Icons.Default.Bookmark,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = bm.unitTitle,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Text(
                        text = bm.previewText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                    IconButton(onClick = { onDeleteBookmark(bm.id) }) {
                      Icon(Icons.Default.DeleteOutline, contentDescription = "Удалить закладку")
                    }
                  }
                }
              }
            }
          }
        }

        2 -> {
          if (quotes.isEmpty()) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(36.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = "Нажмите на любой абзац во время чтения, чтобы сохранить цитату.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            LazyColumn(
              modifier = Modifier.fillMaxWidth(),
              contentPadding = PaddingValues(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              items(quotes, key = { it.id }) { quote ->
                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color = MaterialTheme.colorScheme.surfaceContainerLow,
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                      onSelectUnit(quote.unitIndex, quote.paragraphIndex)
                      onDismiss()
                    },
                ) {
                  Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                      text = "«${quote.text}»",
                      style = MaterialTheme.typography.bodyMedium,
                      maxLines = 4,
                      overflow = TextOverflow.Ellipsis,
                    )
                    if (quote.note.isNotBlank()) {
                      Spacer(modifier = Modifier.height(4.dp))
                      Text(
                        text = quote.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                      )
                    }
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                      Text(
                        text = quote.unitTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Row {
                        IconButton(
                          onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                              type = "text/plain"
                              putExtra(Intent.EXTRA_TEXT, "«${quote.text}» — ${quote.bookTitle}")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Поделиться цитатой"))
                          },
                        ) {
                          Icon(Icons.Default.Share, contentDescription = "Поделиться")
                        }
                        IconButton(onClick = { onDeleteQuote(quote.id) }) {
                          Icon(Icons.Default.DeleteOutline, contentDescription = "Удалить")
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
  }
}

@Composable
fun SaveQuoteDialog(
  paragraphText: String,
  onDismiss: () -> Unit,
  onSave: (note: String, colorTag: String) -> Unit,
) {
  var note by remember { mutableStateOf("") }
  var selectedColor by remember { mutableStateOf("TERRACOTTA") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Сохранить цитату") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          shape = RoundedCornerShape(12.dp),
        ) {
          Text(
            text = "«$paragraphText»",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(12.dp),
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text("Цвет:", style = MaterialTheme.typography.labelLarge)
          listOf(
            "TERRACOTTA" to Color(0xFF9C422E),
            "SAGE" to Color(0xFF4F6B52),
            "AMBER" to Color(0xFFB37A1E),
          ).forEach { (tag, color) ->
            Box(
              modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                  if (selectedColor == tag) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                  } else {
                    Modifier
                  }
                )
                .clickable { selectedColor = tag },
            )
          }
        }

        OutlinedTextField(
          value = note,
          onValueChange = { note = it },
          label = { Text("Личная заметка (необязательно)") },
          maxLines = 3,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onSave(note, selectedColor) },
        modifier = Modifier.testTag("confirm_save_quote"),
      ) {
        Text("Сохранить")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Отмена")
      }
    },
  )
}
