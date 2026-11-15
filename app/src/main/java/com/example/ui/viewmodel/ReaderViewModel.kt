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
import com.example.data.local.ReaderReadingMode
import com.example.data.local.ReaderThemeMode
import com.example.data.repository.BookRepository
import com.example.reader.engine.BookPage
import com.example.reader.engine.BookSearchResult
import com.example.reader.engine.LoadedBookContent
import com.example.reader.engine.Locator
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

sealed interface ReaderLoadState {
  data object Loading : ReaderLoadState
  data class Ready(
    val book: BookEntity,
    val content: LoadedBookContent,
    val currentUnitIndex: Int,
    val initialLocator: Locator,
  ) : ReaderLoadState
  data class Error(val message: String) : ReaderLoadState
}

@OptIn(FlowPreview::class)
class ReaderViewModel(
  private val bookId: Long,
  private val initialUnitOverride: Int?,
  private val initialParagraphOverride: Int?,
  private val initialCharOffsetOverride: Int?,
  private val bookRepository: BookRepository,
  private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

  private data class PendingProgress(
    val locator: Locator,
    val progressPercent: Float,
    val totalUnits: Int,
    val totalChars: Long,
  )

  private val _loadState = MutableStateFlow<ReaderLoadState>(ReaderLoadState.Loading)
  val loadState: StateFlow<ReaderLoadState> = _loadState.asStateFlow()

  private val _currentUnitIndex = MutableStateFlow(0)
  val currentUnitIndex: StateFlow<Int> = _currentUnitIndex.asStateFlow()

  private val _currentLocator = MutableStateFlow(Locator())
  val currentLocator: StateFlow<Locator> = _currentLocator.asStateFlow()

  private val _currentPageRange = MutableStateFlow<BookPage?>(null)

  private val _progressPercent = MutableStateFlow(0f)
  val progressPercent: StateFlow<Float> = _progressPercent.asStateFlow()

  private val _toastMessage = MutableSharedFlow<String>()
  val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _searchResults = MutableStateFlow<List<BookSearchResult>>(emptyList())
  val searchResults: StateFlow<List<BookSearchResult>> = _searchResults.asStateFlow()

  private val _isSearching = MutableStateFlow(false)
  val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

  private var searchJob: Job? = null

  private val pendingProgressFlow = MutableStateFlow<PendingProgress?>(null)
  private var latestPending: PendingProgress? = null
  private var lastPersistedPending: PendingProgress? = null

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
    currentLocator,
    _currentPageRange,
    preferences,
  ) { list, locator, pageRange, prefs ->
    findBookmarkMatchingCurrentPosition(list, locator, pageRange, prefs.readingMode) != null
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000L),
    initialValue = false,
  )

  init {
    viewModelScope.launch {
      pendingProgressFlow
        .filterNotNull()
        .debounce(500L)
        .collect { pending ->
          persistPendingIfNeeded(pending)
        }
    }
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
        val totalUnits = totalUnitsOf(content)
        val totalChars = totalCharsOf(content)

        val startUnit = (initialUnitOverride ?: book.locator.chapterIndex).coerceIn(0, totalUnits - 1)
        val startParagraph = (initialParagraphOverride ?: book.locator.paragraphIndex).coerceAtLeast(0)
        val startChar = (initialCharOffsetOverride ?: book.locator.charOffset).coerceAtLeast(0)
        val startLocator = normalizeLocator(
          content,
          Locator(
            chapterIndex = startUnit,
            paragraphIndex = startParagraph,
            charOffset = startChar,
          ),
        )
        val initialProgress = computeProgress(content, startLocator)

        _currentUnitIndex.value = startLocator.chapterIndex
        _currentLocator.value = startLocator
        _progressPercent.value = initialProgress
        _loadState.value = ReaderLoadState.Ready(
          book = book,
          content = content,
          currentUnitIndex = startLocator.chapterIndex,
          initialLocator = startLocator,
        )

        val initialPending = PendingProgress(
          locator = startLocator,
          progressPercent = initialProgress,
          totalUnits = totalUnits,
          totalChars = totalChars,
        )
        latestPending = initialPending
        lastPersistedPending = initialPending
        bookRepository.saveProgress(
          bookId = book.id,
          locator = startLocator,
          progressPercent = initialProgress,
          totalUnits = totalUnits,
          totalChars = totalChars,
        )
      }.onFailure { err ->
        _loadState.value = ReaderLoadState.Error(err.localizedMessage ?: "Не удалось открыть книгу.")
      }
    }
  }

  fun selectUnit(unitIndex: Int, paragraphIndex: Int = 0, charOffset: Int = 0) {
    val state = _loadState.value as? ReaderLoadState.Ready ?: return
    val targetLocator = normalizeLocator(
      state.content,
      Locator(
        chapterIndex = unitIndex,
        paragraphIndex = paragraphIndex,
        charOffset = charOffset,
      ),
    )
    val progress = computeProgress(state.content, targetLocator)

    if (targetLocator.chapterIndex != _currentUnitIndex.value) {
      _currentPageRange.value = null
    }
    _currentUnitIndex.value = targetLocator.chapterIndex
    _currentLocator.value = targetLocator
    _progressPercent.value = progress
    _loadState.value = state.copy(
      currentUnitIndex = targetLocator.chapterIndex,
      initialLocator = targetLocator,
    )

    scheduleProgressSave(state.content, targetLocator, progress)
  }

  fun onScrollPositionChanged(paragraphIndex: Int, charOffset: Int = 0) {
    val state = _loadState.value as? ReaderLoadState.Ready ?: return
    _currentPageRange.value = null
    val targetLocator = normalizeLocator(
      state.content,
      Locator(
        chapterIndex = _currentUnitIndex.value,
        paragraphIndex = paragraphIndex,
        charOffset = charOffset,
      ),
    )
    if (targetLocator == _currentLocator.value) return

    val progress = computeProgress(state.content, targetLocator)
    _currentLocator.value = targetLocator
    _progressPercent.value = progress
    scheduleProgressSave(state.content, targetLocator, progress)
  }

  fun onPageChanged(page: BookPage, isLastPageOfBook: Boolean) {
    val state = _loadState.value as? ReaderLoadState.Ready ?: return
    _currentPageRange.value = page
    val targetLocator = normalizeLocator(state.content, page.startLocator)
    val progress = if (isLastPageOfBook) 1f else computeProgress(state.content, targetLocator)
    if (targetLocator == _currentLocator.value && progress == _progressPercent.value) return

    _currentLocator.value = targetLocator
    _progressPercent.value = progress
    scheduleProgressSave(state.content, targetLocator, progress)
  }

  fun flushProgressNow() {
    val pending = latestPending ?: return
    if (pending == lastPersistedPending) return
    lastPersistedPending = pending
    bookRepository.saveProgressAsync(
      bookId = bookId,
      locator = pending.locator,
      progressPercent = pending.progressPercent,
      totalUnits = pending.totalUnits,
      totalChars = pending.totalChars,
    )
  }

  override fun onCleared() {
    flushProgressNow()
    super.onCleared()
  }

  fun nextUnit(): Boolean {
    val state = _loadState.value as? ReaderLoadState.Ready ?: return false
    val total = totalUnitsOf(state.content)
    if (_currentUnitIndex.value + 1 < total) {
      selectUnit(_currentUnitIndex.value + 1, 0, 0)
      return true
    }
    return false
  }

  fun previousUnit(): Boolean {
    if (_currentUnitIndex.value > 0) {
      selectUnit(_currentUnitIndex.value - 1, 0, 0)
      return true
    }
    return false
  }

  fun previousUnitToEnd(): Boolean {
    val state = _loadState.value as? ReaderLoadState.Ready ?: return false
    val prevIdx = _currentUnitIndex.value - 1
    if (prevIdx < 0) return false
    return when (val content = state.content) {
      is LoadedBookContent.Reflowable -> {
        val prevChapter = content.chapters.getOrNull(prevIdx)
        val lastParaIdx = (prevChapter?.paragraphs?.lastIndex ?: 0).coerceAtLeast(0)
        val lastCharOff = prevChapter?.paragraphs?.getOrNull(lastParaIdx)?.text?.length ?: 0
        selectUnit(prevIdx, lastParaIdx, lastCharOff)
        true
      }
      is LoadedBookContent.PdfDocumentContent -> {
        selectUnit(prevIdx, 0, 0)
        true
      }
    }
  }

  fun toggleBookmarkForCurrentUnit() {
    val state = _loadState.value as? ReaderLoadState.Ready ?: return
    val locator = _currentLocator.value
    val existingMatch = findBookmarkMatchingCurrentPosition(
      bookmarks = bookmarks.value,
      locator = locator,
      pageRange = _currentPageRange.value,
      readingMode = preferences.value.readingMode,
    )
    if (existingMatch != null) {
      viewModelScope.launch {
        bookRepository.deleteBookmark(existingMatch.id)
        _toastMessage.emit("Закладка удалена")
      }
      return
    }

    val unitIdx = locator.chapterIndex
    val (unitTitle, preview) = when (val content = state.content) {
      is LoadedBookContent.Reflowable -> {
        val chapter = content.chapters.getOrNull(unitIdx)
        val title = chapter?.title ?: "Глава ${unitIdx + 1}"
        val currentPara = chapter?.paragraphs?.getOrNull(locator.paragraphIndex)?.text
        val offsetSnippet = if (!currentPara.isNullOrBlank()) {
          val safeOffset = locator.charOffset.coerceIn(0, currentPara.length)
          val sliced = currentPara.substring(safeOffset).trimStart()
          sliced.ifBlank { currentPara }
        } else {
          null
        }
        val snippet = offsetSnippet?.takeIf { it.isNotBlank() }
          ?: chapter?.paragraphs?.firstOrNull { it.text.length > 20 }?.text
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
        locator = locator,
        unitTitle = unitTitle,
        previewText = preview,
      )
      _toastMessage.emit(if (added) "Закладка добавлена" else "Закладка удалена")
    }
  }

  fun updateSearchQuery(query: String) {
    _searchQuery.value = query
    searchJob?.cancel()
    val trimmed = query.trim()
    if (trimmed.length < 2) {
      _searchResults.value = emptyList()
      _isSearching.value = false
      return
    }
    val state = _loadState.value as? ReaderLoadState.Ready ?: return
    searchJob = viewModelScope.launch {
      _isSearching.value = true
      val results = bookRepository.searchBookText(
        book = state.book,
        fallbackContent = state.content as? LoadedBookContent.Reflowable,
        query = trimmed,
      )
      _searchResults.value = results
      _isSearching.value = false
    }
  }

  private fun findBookmarkMatchingCurrentPosition(
    bookmarks: List<BookmarkEntity>,
    locator: Locator,
    pageRange: BookPage?,
    readingMode: ReaderReadingMode,
  ): BookmarkEntity? {
    if (bookmarks.isEmpty()) return null
    if (readingMode == ReaderReadingMode.PAGES &&
      pageRange != null &&
      pageRange.chapterIndex == locator.chapterIndex
    ) {
      return bookmarks.firstOrNull { bm ->
        bm.locator.chapterIndex == pageRange.chapterIndex && isLocatorOnPage(bm.locator, pageRange)
      }
    }
    return bookmarks.firstOrNull { bm ->
      bm.locator.chapterIndex == locator.chapterIndex &&
        bm.locator.paragraphIndex == locator.paragraphIndex &&
        abs(bm.locator.charOffset - locator.charOffset) <= 180
    }
  }

  private fun isLocatorOnPage(loc: Locator, page: BookPage): Boolean {
    if (loc.chapterIndex != page.chapterIndex) return false
    val start = page.startLocator
    val afterOrAtStart = loc.paragraphIndex > start.paragraphIndex ||
      (loc.paragraphIndex == start.paragraphIndex && loc.charOffset >= start.charOffset)
    if (!afterOrAtStart) return false

    if (loc.paragraphIndex < page.endParagraphIndex) return true
    if (loc.paragraphIndex > page.endParagraphIndex) return false

    val endsCompleteParagraph = page.slices.lastOrNull()?.isLastSliceOfParagraph ?: true
    return if (endsCompleteParagraph) {
      loc.charOffset <= page.endCharInParagraph
    } else {
      loc.charOffset < page.endCharInParagraph
    }
  }

  fun deleteBookmark(bookmarkId: Long) {
    viewModelScope.launch { bookRepository.deleteBookmark(bookmarkId) }
  }

  fun saveQuote(
    locator: Locator,
    unitTitle: String,
    text: String,
    note: String,
    colorTag: String,
  ) {
    val state = _loadState.value as? ReaderLoadState.Ready ?: return
    viewModelScope.launch {
      bookRepository.saveQuote(
        book = state.book,
        locator = locator,
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

  fun setReadingMode(mode: ReaderReadingMode) {
    val state = _loadState.value as? ReaderLoadState.Ready
    if (mode != ReaderReadingMode.PAGES) {
      _currentPageRange.value = null
    }
    if (state != null) {
      _loadState.value = state.copy(initialLocator = _currentLocator.value)
    }
    viewModelScope.launch { preferencesRepository.updateReadingMode(mode) }
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

  fun setHyphenationEnabled(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.updateHyphenation(enabled) }
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

  private fun scheduleProgressSave(
    content: LoadedBookContent,
    locator: Locator,
    progressPercent: Float,
  ) {
    val pending = PendingProgress(
      locator = locator,
      progressPercent = progressPercent,
      totalUnits = totalUnitsOf(content),
      totalChars = totalCharsOf(content),
    )
    latestPending = pending
    pendingProgressFlow.value = pending
  }

  private suspend fun persistPendingIfNeeded(pending: PendingProgress) {
    if (pending == lastPersistedPending) return
    lastPersistedPending = pending
    bookRepository.saveProgress(
      bookId = bookId,
      locator = pending.locator,
      progressPercent = pending.progressPercent,
      totalUnits = pending.totalUnits,
      totalChars = pending.totalChars,
    )
  }

  private fun totalUnitsOf(content: LoadedBookContent): Int = when (content) {
    is LoadedBookContent.Reflowable -> content.chapters.size
    is LoadedBookContent.PdfDocumentContent -> content.pageCount
  }.coerceAtLeast(1)

  private fun totalCharsOf(content: LoadedBookContent): Long = when (content) {
    is LoadedBookContent.Reflowable -> content.totalChars
    is LoadedBookContent.PdfDocumentContent -> 0L
  }

  private fun computeProgress(content: LoadedBookContent, locator: Locator): Float = when (content) {
    is LoadedBookContent.Reflowable -> content.progressFor(locator)
    is LoadedBookContent.PdfDocumentContent -> content.progressFor(locator)
  }

  private fun normalizeLocator(content: LoadedBookContent, raw: Locator): Locator {
    return when (content) {
      is LoadedBookContent.Reflowable -> {
        if (content.chapters.isEmpty()) return Locator()
        val chIdx = raw.chapterIndex.coerceIn(0, content.chapters.lastIndex)
        val chapter = content.chapters[chIdx]
        if (chapter.paragraphs.isEmpty()) return Locator(chIdx, 0, 0)
        val pIdx = raw.paragraphIndex.coerceIn(0, chapter.paragraphs.lastIndex)
        val maxChar = chapter.paragraphs[pIdx].text.length
        val cOff = raw.charOffset.coerceIn(0, maxChar)
        Locator(chapterIndex = chIdx, paragraphIndex = pIdx, charOffset = cOff)
      }
      is LoadedBookContent.PdfDocumentContent -> {
        val page = raw.chapterIndex.coerceIn(0, (content.pageCount - 1).coerceAtLeast(0))
        Locator(chapterIndex = page, paragraphIndex = 0, charOffset = 0)
      }
    }
  }

  class Factory(
    private val bookId: Long,
    private val initialUnitOverride: Int?,
    private val initialParagraphOverride: Int?,
    private val initialCharOffsetOverride: Int? = null,
    private val bookRepository: BookRepository,
    private val preferencesRepository: PreferencesRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return ReaderViewModel(
        bookId = bookId,
        initialUnitOverride = initialUnitOverride,
        initialParagraphOverride = initialParagraphOverride,
        initialCharOffsetOverride = initialCharOffsetOverride,
        bookRepository = bookRepository,
        preferencesRepository = preferencesRepository,
      ) as T
    }
  }
}
