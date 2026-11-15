package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.data.local.BookmarkEntity
import com.example.data.local.QuoteEntity
import com.example.data.local.ReaderFontOption
import com.example.data.local.ReaderPreferences
import com.example.data.local.ReaderReadingMode
import com.example.data.local.ReaderThemeMode
import com.example.reader.engine.BookSearchResult
import com.example.reader.engine.Locator
import com.example.reader.engine.TocEntry
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderAppearanceSheet(
  preferences: ReaderPreferences,
  onDismiss: () -> Unit,
  onThemeSelected: (ReaderThemeMode) -> Unit,
  onReadingModeSelected: (ReaderReadingMode) -> Unit,
  onFontSelected: (ReaderFontOption) -> Unit,
  onFontSizeChanged: (Float) -> Unit,
  onLineHeightChanged: (Float) -> Unit,
  onMarginChanged: (Int) -> Unit,
  onJustifyChanged: (Boolean) -> Unit,
  onHyphenationChanged: (Boolean) -> Unit,
  onVolumeNavChanged: (Boolean) -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
              modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp),
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

      Text(text = "Режим чтения", style = MaterialTheme.typography.labelLarge)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        ReaderReadingMode.entries.forEach { mode ->
          FilterChip(
            selected = preferences.readingMode == mode,
            onClick = { onReadingModeSelected(mode) },
            label = {
              Text(
                text = mode.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
            modifier = Modifier
              .weight(1f)
              .testTag("appearance_mode_${mode.name.lowercase()}"),
          )
        }
      }

      Text(text = "Шрифт", style = MaterialTheme.typography.labelLarge)
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
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
                overflow = TextOverflow.Ellipsis,
              )
            },
          )
        }
      }

      Column {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Размер текста",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
          )
          Text(
            text = "${preferences.fontSizeSp.roundToInt()} pt",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            softWrap = false,
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
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Межстрочный интервал",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
          )
          Text(
            text = String.format("%.2fx", preferences.lineHeightMultiplier),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            softWrap = false,
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
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Поля страницы",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
          )
          Text(
            text = "${preferences.horizontalMarginDp} dp",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            softWrap = false,
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
        Text(
          text = "Выравнивание по ширине",
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
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
        Text(
          text = "Переносы",
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Switch(
          checked = preferences.hyphenationEnabled,
          onCheckedChange = onHyphenationChanged,
          modifier = Modifier.testTag("switch_hyphenation"),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "Листание кнопками громкости",
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
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
  onSelectUnit: (unitIndex: Int, paragraphIndex: Int, charOffset: Int) -> Unit,
  onDeleteBookmark: (Long) -> Unit,
  onDeleteQuote: (Long) -> Unit,
) {
  var selectedTab by remember { mutableIntStateOf(0) }
  val context = LocalContext.current

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight(0.75f),
    ) {
      TabRow(
        selectedTabIndex = selectedTab,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
      ) {
        Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = {
            Text(
              text = "Главы (${toc.size})",
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
        )
        Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = {
            Text(
              text = "Закладки (${bookmarks.size})",
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
        )
        Tab(
          selected = selectedTab == 2,
          onClick = { selectedTab = 2 },
          text = {
            Text(
              text = "Цитаты (${quotes.size})",
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
        )
      }

      when (selectedTab) {
        0 -> {
          if (toc.isEmpty()) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(36.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = "В этой книге нет разделов оглавления.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
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
                      onSelectUnit(entry.unitIndex, 0, 0)
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
                      onSelectUnit(bm.locator.chapterIndex, bm.locator.paragraphIndex, bm.locator.charOffset)
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
                        text = "${bm.unitTitle} • абз. ${bm.locator.paragraphIndex + 1}",
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
                      onSelectUnit(quote.locator.chapterIndex, quote.locator.paragraphIndex, quote.locator.charOffset)
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
    containerColor = MaterialTheme.colorScheme.surface,
    title = { Text("Сохранить цитату") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
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
          Text("Метка:", style = MaterialTheme.typography.labelLarge)
          listOf(
            "TERRACOTTA" to MaterialTheme.colorScheme.primary,
            "SAGE" to MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
            "AMBER" to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSearchSheet(
  query: String,
  results: List<BookSearchResult>,
  isSearching: Boolean,
  onQueryChange: (String) -> Unit,
  onSelectResult: (Locator) -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight(0.8f)
        .padding(horizontal = 20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Поиск по тексту книги",
        style = MaterialTheme.typography.titleLarge,
      )

      OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text("Введите слово или фразу...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
          if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
              Icon(Icons.Default.Close, contentDescription = "Очистить поиск")
            }
          }
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("reader_search_input"),
      )

      val trimmedLen = query.trim().length
      when {
        trimmedLen < 2 -> {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "Введите минимум 2 символа для поиска по кэшу книги.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        isSearching -> {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator()
          }
        }

        results.isEmpty() -> {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "Совпадений не найдено.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        else -> {
          Text(
            text = "Найдено совпадений: ${results.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(results) { item ->
              val highlightColor = MaterialTheme.colorScheme.primary
              val highlightBg = highlightColor.copy(alpha = 0.16f)
              val annotated = remember(item, highlightColor) {
                val start = item.matchStartInSnippet.coerceIn(0, item.snippet.length)
                val end = (start + item.matchLength).coerceIn(start, item.snippet.length)
                buildAnnotatedString {
                  append(item.snippet.substring(0, start))
                  withStyle(
                    SpanStyle(
                      color = highlightColor,
                      fontWeight = FontWeight.Bold,
                      background = highlightBg,
                    )
                  ) {
                    append(item.snippet.substring(start, end))
                  }
                  append(item.snippet.substring(end))
                }
              }

              Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    onSelectResult(item.locator)
                    onDismiss()
                  },
              ) {
                Column(
                  modifier = Modifier.padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Text(
                    text = "${item.chapterTitle} • абз. ${item.locator.paragraphIndex + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
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
