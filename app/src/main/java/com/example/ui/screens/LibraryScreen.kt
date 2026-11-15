package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.BookEntity
import com.example.data.local.BookFormat
import com.example.ui.components.BookCoverArt
import com.example.ui.viewmodel.LibraryUiState
import kotlin.math.roundToInt

@Composable
fun LibraryScreen(
  uiState: LibraryUiState,
  onSearchQueryChanged: (String) -> Unit,
  onFormatSelected: (BookFormat?) -> Unit,
  onImportBookClick: () -> Unit,
  onOpenBook: (Long) -> Unit,
  onDeleteBook: (BookEntity) -> Unit,
  modifier: Modifier = Modifier,
) {
  var pendingDeleteBook by remember { mutableStateOf<BookEntity?>(null) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    floatingActionButton = {
      FloatingActionButton(
        onClick = onImportBookClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("add_book_fab"),
      ) {
        Icon(
          imageVector = Icons.Default.Add,
          contentDescription = stringResource(R.string.action_add_book),
        )
      }
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 760.dp),
      ) {
        LibraryHeader(
          bookCount = uiState.books.size,
          isImporting = uiState.isImporting,
        )

        if (uiState.books.isEmpty()) {
          EmptyLibraryState(
            onImportClick = onImportBookClick,
            modifier = Modifier.weight(1f),
          )
        } else {
          OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChanged,
            placeholder = {
              Text(
                text = stringResource(R.string.action_search_books),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
            leadingIcon = {
              Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
              if (uiState.searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChanged("") }) {
                  Icon(Icons.Default.Close, contentDescription = "Очистить поиск")
                }
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp)
              .testTag("library_search_input"),
          )

          Spacer(modifier = Modifier.height(8.dp))

          FormatFilterRow(
            selectedFormat = uiState.selectedFormat,
            onFormatSelected = onFormatSelected,
          )

          Spacer(modifier = Modifier.height(6.dp))

          val continueBook = uiState.continueReadingBook
          val showContinueHero = continueBook != null &&
            uiState.searchQuery.isBlank() &&
            uiState.selectedFormat == null
          val shelfBooks = if (showContinueHero && continueBook != null) {
            uiState.filteredBooks.filter { it.id != continueBook.id }
          } else {
            uiState.filteredBooks
          }

          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
              start = 16.dp,
              end = 16.dp,
              top = 4.dp,
              bottom = 72.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            if (showContinueHero && continueBook != null) {
              item(key = "continue_${continueBook.id}") {
                ContinueReadingCard(
                  book = continueBook,
                  onClick = { onOpenBook(continueBook.id) },
                  onDeleteClick = { pendingDeleteBook = continueBook },
                )
              }
              if (shelfBooks.isNotEmpty()) {
                item(key = "section_all") {
                  Text(
                    text = "Все книги",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                  )
                }
              }
            }

            if (shelfBooks.isEmpty() && !showContinueHero) {
              item(key = "no_matches") {
                Text(
                  text = "По вашему запросу книги не найдены.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                  textAlign = TextAlign.Center,
                )
              }
            } else {
              items(shelfBooks, key = { it.id }) { book ->
                BookShelfItemCard(
                  book = book,
                  onClick = { onOpenBook(book.id) },
                  onDeleteClick = { pendingDeleteBook = book },
                )
              }
            }
          }
        }
      }
    }
  }

  pendingDeleteBook?.let { bookToDelete ->
    AlertDialog(
      onDismissRequest = { pendingDeleteBook = null },
      title = { Text("Удалить книгу?") },
      text = {
        Text("«${bookToDelete.title}» будет удалена из библиотеки, включая закладки и цитаты.")
      },
      confirmButton = {
        TextButton(
          onClick = {
            onDeleteBook(bookToDelete)
            pendingDeleteBook = null
          }
        ) {
          Text("Удалить", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingDeleteBook = null }) {
          Text("Отмена")
        }
      },
    )
  }
}

@Composable
private fun LibraryHeader(
  bookCount: Int,
  isImporting: Boolean,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = if (bookCount == 0) {
          "Личная оффлайн-библиотека"
        } else {
          "Книг на полке: $bookCount"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    AnimatedVisibility(visible = isImporting, enter = fadeIn(), exit = fadeOut()) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 12.dp),
      ) {
        CircularProgressIndicator(
          modifier = Modifier.size(18.dp),
          strokeWidth = 2.dp,
        )
        Text("Открытие...", style = MaterialTheme.typography.labelMedium, maxLines = 1)
      }
    }
  }
}

@Composable
private fun FormatFilterRow(
  selectedFormat: BookFormat?,
  onFormatSelected: (BookFormat?) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
      .padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    FilterChip(
      selected = selectedFormat == null,
      onClick = { onFormatSelected(null) },
      label = { Text("Все", maxLines = 1) },
      modifier = Modifier.testTag("filter_all"),
    )
    BookFormat.entries.forEach { format ->
      FilterChip(
        selected = selectedFormat == format,
        onClick = { onFormatSelected(if (selectedFormat == format) null else format) },
        label = { Text(format.label, maxLines = 1) },
        modifier = Modifier.testTag("filter_${format.name.lowercase()}"),
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueReadingCard(
  book: BookEntity,
  onClick: () -> Unit,
  onDeleteClick: () -> Unit,
) {
  val percent = (book.progressPercent * 100).roundToInt().coerceIn(0, 100)
  val haptics = LocalHapticFeedback.current
  var menuExpanded by remember { mutableStateOf(false) }

  Box(modifier = Modifier.fillMaxWidth()) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("continue_reading_card")
        .combinedClickable(
          onClick = onClick,
          onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            menuExpanded = true
          },
        ),
      shape = RoundedCornerShape(22.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      ),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        BookCoverArt(
          book = book,
          modifier = Modifier
            .width(84.dp)
            .aspectRatio(0.68f),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(8.dp),
          ) {
            Text(
              text = "Читаете сейчас",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = book.title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = book.author,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            LinearProgressIndicator(
              progress = { book.progressPercent.coerceIn(0f, 1f) },
              modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            )
            Text(
              text = "$percent%",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.primary,
              maxLines = 1,
              softWrap = false,
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
          Button(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = Modifier.testTag("continue_reading_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.MenuBook,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(R.string.action_continue_reading),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }

    DropdownMenu(
      expanded = menuExpanded,
      onDismissRequest = { menuExpanded = false },
    ) {
      DropdownMenuItem(
        text = { Text("Открыть") },
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
        onClick = {
          menuExpanded = false
          onClick()
        },
      )
      DropdownMenuItem(
        text = { Text("Удалить книгу", color = MaterialTheme.colorScheme.error) },
        leadingIcon = {
          Icon(
            Icons.Default.DeleteOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
          )
        },
        onClick = {
          menuExpanded = false
          onDeleteClick()
        },
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookShelfItemCard(
  book: BookEntity,
  onClick: () -> Unit,
  onDeleteClick: () -> Unit,
) {
  val percent = (book.progressPercent * 100).roundToInt().coerceIn(0, 100)
  val haptics = LocalHapticFeedback.current
  var menuExpanded by remember { mutableStateOf(false) }

  Box(modifier = Modifier.fillMaxWidth()) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("book_item_${book.id}")
        .combinedClickable(
          onClick = onClick,
          onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            menuExpanded = true
          },
        ),
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
      ),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        BookCoverArt(
          book = book,
          modifier = Modifier
            .width(60.dp)
            .aspectRatio(0.7f),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = book.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = book.author,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            LinearProgressIndicator(
              progress = { book.progressPercent.coerceIn(0f, 1f) },
              modifier = Modifier
                .weight(1f)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            )
            Text(
              text = "$percent%",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              softWrap = false,
            )
          }
        }
      }
    }

    DropdownMenu(
      expanded = menuExpanded,
      onDismissRequest = { menuExpanded = false },
    ) {
      DropdownMenuItem(
        text = { Text("Открыть") },
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
        onClick = {
          menuExpanded = false
          onClick()
        },
      )
      DropdownMenuItem(
        text = { Text("Удалить книгу", color = MaterialTheme.colorScheme.error) },
        leadingIcon = {
          Icon(
            Icons.Default.DeleteOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
          )
        },
        onClick = {
          menuExpanded = false
          onDeleteClick()
        },
      )
    }
  }
}

@Composable
private fun EmptyLibraryState(
  onImportClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 84.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Image(
      painter = painterResource(id = R.drawable.img_empty_shelf_1794685413709),
      contentDescription = stringResource(R.string.cd_empty_shelf),
      contentScale = ContentScale.Crop,
      modifier = Modifier
        .fillMaxWidth(0.75f)
        .aspectRatio(4f / 3f)
        .clip(RoundedCornerShape(24.dp)),
    )
    Spacer(modifier = Modifier.height(20.dp))
    Text(
      text = stringResource(R.string.empty_library_title),
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = stringResource(R.string.empty_library_subtitle),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(20.dp))
    Button(
      onClick = onImportClick,
      shape = RoundedCornerShape(14.dp),
      contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
      modifier = Modifier.testTag("empty_import_button"),
    ) {
      Icon(Icons.Default.Add, contentDescription = null)
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = "Выбрать книгу (EPUB, FB2, TXT, PDF)",
        maxLines = 2,
        textAlign = TextAlign.Center,
      )
    }
  }
}
