package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.BookEntity
import com.example.data.local.BookFormat
import com.example.data.local.PreferencesRepository
import com.example.data.local.QuoteEntity
import com.example.data.local.ReaderPreferences
import com.example.data.repository.BookRepository
import com.example.data.repository.ImportResult
import com.example.reader.tts.TtsPlaybackManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
  val books: List<BookEntity> = emptyList(),
  val searchQuery: String = "",
  val selectedFormat: BookFormat? = null,
  val isImporting: Boolean = false,
) {
  val continueReadingBook: BookEntity?
    get() = books.firstOrNull { it.lastReadAt > 0L }

  val filteredBooks: List<BookEntity>
    get() = books.filter { book ->
      val matchesFormat = selectedFormat == null || book.format == selectedFormat.name
      val q = searchQuery.trim()
      val matchesQuery = q.isEmpty() ||
        book.title.contains(q, ignoreCase = true) ||
        book.author.contains(q, ignoreCase = true)
      matchesFormat && matchesQuery
    }
}

sealed interface LibraryEvent {
  data class OpenBook(val bookId: Long) : LibraryEvent
  data class ShowMessage(val message: String) : LibraryEvent
}

class LibraryViewModel(
  private val bookRepository: BookRepository,
  private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

  private val searchQuery = MutableStateFlow("")
  private val selectedFormat = MutableStateFlow<BookFormat?>(null)
  private val isImporting = MutableStateFlow(false)

  private val _events = MutableSharedFlow<LibraryEvent>()
  val events: SharedFlow<LibraryEvent> = _events.asSharedFlow()

  val uiState: StateFlow<LibraryUiState> = combine(
    bookRepository.allBooks,
    searchQuery,
    selectedFormat,
    isImporting,
  ) { books, query, format, importing ->
    LibraryUiState(
      books = books,
      searchQuery = query,
      selectedFormat = format,
      isImporting = importing,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000L),
    initialValue = LibraryUiState(),
  )

  val allQuotes: StateFlow<List<QuoteEntity>> = bookRepository.allQuotes.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000L),
    initialValue = emptyList(),
  )

  val preferences: StateFlow<ReaderPreferences> = preferencesRepository.preferencesFlow.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000L),
    initialValue = ReaderPreferences(),
  )

  fun onSearchQueryChanged(query: String) {
    searchQuery.value = query
  }

  fun onFormatFilterSelected(format: BookFormat?) {
    selectedFormat.value = format
  }

  fun importBook(uri: Uri, openImmediately: Boolean = true) {
    viewModelScope.launch {
      isImporting.value = true
      when (val result = bookRepository.importBookFromUri(uri)) {
        is ImportResult.Success -> {
          isImporting.value = false
          if (result.alreadyExisted) {
            _events.emit(LibraryEvent.ShowMessage("Книга уже на полке — открываем сохранённую позицию"))
          }
          if (openImmediately) {
            _events.emit(LibraryEvent.OpenBook(result.bookId))
          }
        }
        is ImportResult.Failure -> {
          isImporting.value = false
          _events.emit(LibraryEvent.ShowMessage(result.message))
        }
      }
    }
  }

  fun deleteBook(book: BookEntity) {
    viewModelScope.launch {
      bookRepository.deleteBook(book)
      _events.emit(LibraryEvent.ShowMessage("Книга удалена с устройства"))
    }
  }

  fun deleteQuote(quoteId: Long) {
    viewModelScope.launch {
      bookRepository.deleteQuote(quoteId)
    }
  }

  fun setReaderTheme(mode: com.example.data.local.ReaderThemeMode) {
    viewModelScope.launch { preferencesRepository.updateTheme(mode) }
  }

  fun setReadingMode(mode: com.example.data.local.ReaderReadingMode) {
    viewModelScope.launch { preferencesRepository.updateReadingMode(mode) }
  }

  fun setUseDynamicColor(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.updateDynamicColor(enabled) }
  }

  fun setVolumeKeyNavigation(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.updateVolumeKeyNavigation(enabled) }
  }

  fun setKeepScreenOn(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.updateKeepScreenOn(enabled) }
  }

  fun setTtsSpeed(speed: Float) {
    val clamped = speed.coerceIn(0.7f, 1.3f)
    TtsPlaybackManager.peek()?.setSpeed(clamped)
    viewModelScope.launch { preferencesRepository.updateTtsSpeed(clamped) }
  }

  fun setTtsPitch(pitch: Float) {
    val clamped = pitch.coerceIn(0.7f, 1.3f)
    TtsPlaybackManager.peek()?.setPitch(clamped)
    viewModelScope.launch { preferencesRepository.updateTtsPitch(clamped) }
  }

  fun setTtsVoiceName(voiceName: String?) {
    val clean = voiceName?.trim()?.takeIf { it.isNotEmpty() }
    TtsPlaybackManager.peek()?.setVoiceName(clean)
    viewModelScope.launch { preferencesRepository.updateTtsVoiceName(clean) }
  }

  class Factory(
    private val bookRepository: BookRepository,
    private val preferencesRepository: PreferencesRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return LibraryViewModel(bookRepository, preferencesRepository) as T
    }
  }
}
