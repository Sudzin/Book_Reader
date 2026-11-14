package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.BookEntity
import com.example.data.local.BookmarkEntity
import com.example.data.local.PreferencesRepository
import com.example.data.local.QuoteEntity
import com.example.data.local.ReaderFontOption
import com.example.data.local.ReaderPreferences
import com.example.data.local.ReaderThemeMode
import com.example.data.repository.BookRepository
import com.example.reader.engine.LoadedBookContent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ReaderLoadState {
  data object Loading : ReaderLoadState
  data class Ready(
    val book: BookEntity,
    val content: LoadedBookContent,
    val currentUnitIndex: Int,
    val initialParagraphIndex: Int,
  ) : ReaderLoadState
  data class Error(val message: String) : ReaderLoadState
}

class ReaderViewModel(
  private val bookId: Long,
  private val initialUnitOverride: Int?,
  private val initialParagraphOverride: Int?,
  private val bookRepository: BookRepository,
  private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

  private val _loadState = MutableStateFlow<ReaderLoadState>(ReaderLoadState.Loading)
  val loadState: StateFlow<ReaderLoadState> = _loadState.asStateFlow()

  private val _currentUnitIndex = MutableStateFlow(0)
  val currentUnitIndex: StateFlow<Int> = _currentUnitIndex.asStateFlow()

  private val _toastMessage = MutableSharedFlow<String>()
  val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

  val preferences: StateFlow<ReaderPreferences> = preferencesRepository.preferencesFlow.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000L),
    initialValue = ReaderPreferences(),
  )

  val bookmarks: StateFlow<List<BookmarkEntity>> = bookRepository.observeBookmarksForBook(bookId).stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000L),
    initialValue = emptyList(),
  )

  val quotes: StateFlow<List<QuoteEntity>> = bookRepository.observeQuotesForBook(bookId).stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000L),
    initialValue = emptyList(),
  )

  val isCurrentUnitBookmarked: StateFlow<Boolean> = combine(
    bookmarks,
    currentUnitIndex,
  ) { list, unitIdx ->
    list.any { it.unitIndex == unitIdx }
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000L),
    initialValue = false,
  )

  init {
    loadBook()
  }

  private fun loadBook() {
    viewModelScope.launch {
      _loadState.value = ReaderLoadState.Loading
      val book = bookRepository.observeBook(bookId).firstOrNull()
      if (book == null) {
        _loadState.value = ReaderLoadState.Error("Книга не найдена в библиотеке.")
        return@launch
      }

      val loaded = bookRepository.loadBookContent(book)
      loaded.onSuccess { content ->
        val total = when (content) {
          is LoadedBookContent.Reflowable -> content.chapters.size
          is LoadedBookContent.PdfDocumentContent -> content.pageCount
        }.coerceAtLeast(1)

        val startUnit = (initialUnitOverride ?: book.currentUnitIndex).coerceIn(0, total - 1)
        val startParagraph = (initialParagraphOverride ?: book.currentParagraphIndex).coerceAtLeast(0)
        _currentUnitIndex.value = startUnit
        _loadState.value = ReaderLoadState.Ready(
          book = book,
          content = content,
          currentUnitIndex = startUnit,
          initialParagraphIndex = startParagraph,
        )
        bookRepository.saveProgress(book.id, startUnit, startParagraph, total)
      }.onFailure { err ->
        _loadState.value = ReaderLoadState.Error(err.localizedMessage ?: "Не удалось открыть книгу.")
      }
    }
  }

  fun selectUnit(unitIndex: Int, paragraphIndex: Int = 0) {
    val state = _loadState.value as? ReaderLoadState.Ready ?: return
    val total = when (val c = state.content) {
      is LoadedBookContent.Reflowable -> c.chapters.size
      is LoadedBookContent.PdfDocumentContent -> c.pageCount
    }.coerceAtLeast(1)
    val clamped = unitIndex.coerceIn(0, total - 1)
    _currentUnitIndex.value = clamped
    _loadState.value = state.copy(
      currentUnitIndex = clamped,
      initialParagraphIndex = paragraphIndex.coerceAtLeast(0),
    )
    viewModelScope.launch {
      bookRepository.saveProgress(bookId, clamped, paragraphIndex, total)
    }
  }

  fun updateParagraphProgress(paragraphIndex: Int) {
    val state = _loadState.value as? ReaderLoadState.Ready ?: return
    val total = when (val c = state.content) {
      is LoadedBookContent.Reflowable -> c.chapters.size
      is LoadedBookContent.PdfDocumentContent -> c.pageCount
    }.coerceAtLeast(1)
    viewModelScope.launch {
      bookRepository.saveProgress(bookId, _currentUnitIndex.value, paragraphIndex, total)
    }
  }

  fun nextUnit(): Boolean {
    val state = _loadState.value as? ReaderLoadState.Ready ?: return false
    val total = when (val c = state.content) {
      is LoadedBookContent.Reflowable -> c.chapters.size
      is LoadedBookContent.PdfDocumentContent -> c.pageCount
    }
    if (_currentUnitIndex.value + 1 < total) {
      selectUnit(_currentUnitIndex.value + 1, 0)
      return true
    }
    return false
  }

  fun previousUnit(): Boolean {
    if (_currentUnitIndex.value > 0) {
      selectUnit(_currentUnitIndex.value - 1, 0)
      return true
    }
    return false
  }

  fun toggleBookmarkForCurrentUnit() {
    val state = _loadState.value as? ReaderLoadState.Ready ?: return
    val unitIdx = _currentUnitIndex.value
    val (unitTitle, preview) = when (val content = state.content) {
      is LoadedBookContent.Reflowable -> {
        val chapter = content.chapters.getOrNull(unitIdx)
        val title = chapter?.title ?: "Глава ${unitIdx + 1}"
        val snippet = chapter?.paragraphs?.firstOrNull { it.text.length > 20 }?.text
          ?: chapter?.paragraphs?.firstOrNull()?.text
          ?: title
        title to snippet
      }
      is LoadedBookContent.PdfDocumentContent -> {
        "Страница ${unitIdx + 1}" to "${state.book.title} — страница ${unitIdx + 1} из ${content.pageCount}"
      }
    }
    viewModelScope.launch {
      val added = bookRepository.toggleBookmark(
        bookId = bookId,
        unitIndex = unitIdx,
        paragraphIndex = 0,
        unitTitle = unitTitle,
        previewText = preview,
      )
      _toastMessage.emit(if (added) "Закладка добавлена" else "Закладка удалена")
    }
  }

  fun deleteBookmark(bookmarkId: Long) {
    viewModelScope.launch { bookRepository.deleteBookmark(bookmarkId) }
  }

  fun saveQuote(
    unitIndex: Int,
    paragraphIndex: Int,
    unitTitle: String,
    text: String,
    note: String,
    colorTag: String,
  ) {
    val state = _loadState.value as? ReaderLoadState.Ready ?: return
    viewModelScope.launch {
      bookRepository.saveQuote(
        book = state.book,
        unitIndex = unitIndex,
        paragraphIndex = paragraphIndex,
        unitTitle = unitTitle,
        text = text,
        note = note,
        colorTag = colorTag,
      )
      _toastMessage.emit("Цитата сохранена")
    }
  }

  fun deleteQuote(quoteId: Long) {
    viewModelScope.launch { bookRepository.deleteQuote(quoteId) }
  }

  fun setTheme(mode: ReaderThemeMode) {
    viewModelScope.launch { preferencesRepository.updateTheme(mode) }
  }

  fun setFont(option: ReaderFontOption) {
    viewModelScope.launch { preferencesRepository.updateFont(option) }
  }

  fun setFontSize(sizeSp: Float) {
    viewModelScope.launch { preferencesRepository.updateFontSize(sizeSp) }
  }

  fun setLineHeight(mult: Float) {
    viewModelScope.launch { preferencesRepository.updateLineHeight(mult) }
  }

  fun setHorizontalMargin(marginDp: Int) {
    viewModelScope.launch { preferencesRepository.updateHorizontalMargin(marginDp) }
  }

  fun setJustifyText(justify: Boolean) {
    viewModelScope.launch { preferencesRepository.updateJustifyText(justify) }
  }

  fun setVolumeKeyNavigation(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.updateVolumeKeyNavigation(enabled) }
  }

  fun setKeepScreenOn(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.updateKeepScreenOn(enabled) }
  }

  fun setTtsSpeed(speed: Float) {
    viewModelScope.launch { preferencesRepository.updateTtsSpeed(speed) }
  }

  class Factory(
    private val bookId: Long,
    private val initialUnitOverride: Int?,
    private val initialParagraphOverride: Int?,
    private val bookRepository: BookRepository,
    private val preferencesRepository: PreferencesRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return ReaderViewModel(
        bookId = bookId,
        initialUnitOverride = initialUnitOverride,
        initialParagraphOverride = initialParagraphOverride,
        bookRepository = bookRepository,
        preferencesRepository = preferencesRepository,
      ) as T
    }
  }
}
