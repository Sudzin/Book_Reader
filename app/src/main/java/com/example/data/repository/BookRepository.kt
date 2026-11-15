package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.AppDatabase
import com.example.data.local.BookEntity
import com.example.data.local.BookFormat
import com.example.data.local.BookmarkEntity
import com.example.data.local.QuoteEntity
import com.example.reader.engine.BookCacheStorage
import com.example.reader.engine.EpubParser
import com.example.reader.engine.Fb2Parser
import com.example.reader.engine.LoadedBookContent
import com.example.reader.engine.Locator
import com.example.reader.engine.PdfParser
import com.example.reader.engine.TxtParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

sealed class ImportResult {
  data class Success(val bookId: Long, val alreadyExisted: Boolean) : ImportResult()
  data class Failure(val message: String) : ImportResult()
}

class BookRepository(
  private val context: Context,
  private val database: AppDatabase,
) {
  private val bookDao = database.bookDao()
  private val bookmarkDao = database.bookmarkDao()
  private val quoteDao = database.quoteDao()

  private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val booksDir: File
    get() = File(context.filesDir, "books").apply { if (!exists()) mkdirs() }

  private val coversDir: File
    get() = File(context.filesDir, "covers").apply { if (!exists()) mkdirs() }

  private val cacheDir: File
    get() = File(context.filesDir, "book_cache").apply { if (!exists()) mkdirs() }

  val allBooks: Flow<List<BookEntity>> = bookDao.observeAllBooks()
  val allQuotes: Flow<List<QuoteEntity>> = quoteDao.observeAllQuotes()

  fun observeBook(bookId: Long): Flow<BookEntity?> = bookDao.observeBookById(bookId)

  fun observeBookmarksForBook(bookId: Long): Flow<List<BookmarkEntity>> =
    bookmarkDao.observeBookmarksForBook(bookId)

  fun observeQuotesForBook(bookId: Long): Flow<List<QuoteEntity>> =
    quoteDao.observeQuotesForBook(bookId)

  suspend fun importBookFromUri(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
    var tempFile: File? = null
    var storedFile: File? = null
    var cacheFile: File? = null
    var coverFile: File? = null
    try {
      val resolver = context.contentResolver
      val displayName = queryFileName(uri) ?: "book_${System.currentTimeMillis()}"
      val mimeType = resolver.getType(uri)
      val format = BookFormat.fromExtensionOrMime(displayName, mimeType)
        ?: return@withContext ImportResult.Failure(
          "Неподдерживаемый формат файла. Выберите EPUB, FB2, TXT или PDF."
        )

      val tmp = File(context.cacheDir, "import_${UUID.randomUUID()}")
      tempFile = tmp
      resolver.openInputStream(uri)?.use { input ->
        tmp.outputStream().use { output -> input.copyTo(output) }
      } ?: return@withContext ImportResult.Failure("Не удалось прочитать выбранный файл.")

      if (tmp.length() == 0L) {
        return@withContext ImportResult.Failure("Выбранный файл пуст.")
      }

      val hash = computeSha256(tmp)
      val existing = bookDao.getBookByHash(hash)
      if (existing != null && File(existing.localFilePath).exists()) {
        return@withContext ImportResult.Success(existing.id, alreadyExisted = true)
      }

      val ext = when {
        displayName.lowercase().endsWith(".fb2.zip") -> "fb2.zip"
        else -> format.name.lowercase()
      }
      val stored = File(booksDir, "${UUID.randomUUID()}.$ext")
      storedFile = stored
      tmp.copyTo(stored, overwrite = true)

      val fallbackTitle = displayName
        .removeSuffix(".fb2.zip")
        .substringBeforeLast('.')
        .ifBlank { "Без названия" }

      val (metadata, parsedContent) = when (format) {
        BookFormat.EPUB -> EpubParser.parseForImport(stored, fallbackTitle)
        BookFormat.FB2 -> Fb2Parser.parseForImport(stored, fallbackTitle)
        BookFormat.TXT -> TxtParser.parseForImport(stored, fallbackTitle)
        BookFormat.PDF -> PdfParser.parseForImport(stored, fallbackTitle)
      }

      val cache = File(cacheDir, "${UUID.randomUUID()}.lcache")
      cacheFile = cache
      BookCacheStorage.write(cache, parsedContent)

      val coverPath = metadata.coverBytes?.let { bytes ->
        val cFile = File(coversDir, "${UUID.randomUUID()}.jpg")
        coverFile = cFile
        cFile.writeBytes(bytes)
        cFile.absolutePath
      }

      val palette = listOf(0xFF8C4B2DL, 0xFF6E5A45L, 0xFF7D5A3CL, 0xFF5D4D3EL, 0xFF7B4E38L)
      val accentColor = palette[kotlin.math.abs(metadata.title.hashCode()) % palette.size]

      val entity = BookEntity(
        title = metadata.title,
        author = metadata.author,
        format = format.name,
        localFilePath = stored.absolutePath,
        coverPath = coverPath,
        contentHash = hash,
        totalUnits = metadata.totalUnits.coerceAtLeast(1),
        totalChars = metadata.totalChars,
        cacheFilePath = cache.absolutePath,
        locator = Locator(0, 0, 0),
        progressPercent = 0f,
        lastReadAt = System.currentTimeMillis(),
        accentColorHex = accentColor,
      )
      val newId = bookDao.insertBook(entity)
      ImportResult.Success(newId, alreadyExisted = false)
    } catch (e: com.example.reader.engine.BookParseException) {
      runCatching { storedFile?.delete() }
      runCatching { cacheFile?.delete() }
      runCatching { coverFile?.delete() }
      ImportResult.Failure(e.message)
    } catch (_: OutOfMemoryError) {
      runCatching { storedFile?.delete() }
      runCatching { cacheFile?.delete() }
      runCatching { coverFile?.delete() }
      ImportResult.Failure("Файл слишком большой или повреждён.")
    } catch (_: Throwable) {
      runCatching { storedFile?.delete() }
      runCatching { cacheFile?.delete() }
      runCatching { coverFile?.delete() }
      ImportResult.Failure("Не удалось открыть файл: возможно, он повреждён или имеет неверный формат.")
    } finally {
      runCatching { tempFile?.delete() }
    }
  }

  suspend fun loadBookContent(book: BookEntity): Result<LoadedBookContent> = withContext(Dispatchers.IO) {
    try {
      val cachedPath = book.cacheFilePath
      if (!cachedPath.isNullOrBlank()) {
        val cachedFile = File(cachedPath)
        val cachedContent = BookCacheStorage.read(cachedFile)
        if (cachedContent != null) {
          return@withContext Result.success(cachedContent)
        }
      }

      val file = File(book.localFilePath)
      if (!file.exists()) {
        return@withContext Result.failure(
          com.example.reader.engine.BookParseException("Файл книги не найден на устройстве.")
        )
      }

      val format = runCatching { BookFormat.valueOf(book.format) }.getOrNull()
        ?: return@withContext Result.failure(
          com.example.reader.engine.BookParseException("Неподдерживаемый формат книги.")
        )

      val (metadata, parsedContent) = when (format) {
        BookFormat.EPUB -> EpubParser.parseForImport(file, book.title)
        BookFormat.FB2 -> Fb2Parser.parseForImport(file, book.title)
        BookFormat.TXT -> TxtParser.parseForImport(file, book.title)
        BookFormat.PDF -> PdfParser.parseForImport(file, book.title)
      }

      val newCacheFile = File(cacheDir, "book_${book.id}_${UUID.randomUUID()}.lcache")
      BookCacheStorage.write(newCacheFile, parsedContent)
      bookDao.updateCacheMetadata(
        bookId = book.id,
        cacheFilePath = newCacheFile.absolutePath,
        totalUnits = metadata.totalUnits.coerceAtLeast(1),
        totalChars = metadata.totalChars,
      )
      Result.success(parsedContent)
    } catch (e: com.example.reader.engine.BookParseException) {
      Result.failure(e)
    } catch (t: Throwable) {
      Result.failure(
        com.example.reader.engine.BookParseException(
          "Не удалось открыть книгу: файл повреждён или недоступен.",
          t,
        )
      )
    }
  }

  suspend fun searchBookText(
    book: BookEntity,
    fallbackContent: LoadedBookContent.Reflowable?,
    query: String,
    maxResults: Int = 200,
  ): List<com.example.reader.engine.BookSearchResult> = withContext(Dispatchers.Default) {
    val cachedPath = book.cacheFilePath
    if (!cachedPath.isNullOrBlank()) {
      val cachedFile = File(cachedPath)
      if (cachedFile.exists()) {
        val cacheResults = com.example.reader.engine.BookSearchEngine.searchFromCache(
          cacheFile = cachedFile,
          query = query,
          maxResults = maxResults,
        )
        if (cacheResults.isNotEmpty() || BookCacheStorage.read(cachedFile) != null) {
          return@withContext cacheResults
        }
      }
    }
    if (fallbackContent != null) {
      com.example.reader.engine.BookSearchEngine.search(
        content = fallbackContent,
        query = query,
        maxResults = maxResults,
      )
    } else {
      emptyList()
    }
  }

  suspend fun saveProgress(
    bookId: Long,
    locator: Locator,
    progressPercent: Float,
    totalUnits: Int,
    totalChars: Long,
  ) = withContext(NonCancellable + Dispatchers.IO) {
    bookDao.updateProgress(
      bookId = bookId,
      chapterIndex = locator.chapterIndex.coerceAtLeast(0),
      paragraphIndex = locator.paragraphIndex.coerceAtLeast(0),
      charOffset = locator.charOffset.coerceAtLeast(0),
      progressPercent = progressPercent.coerceIn(0f, 1f),
      totalUnits = totalUnits.coerceAtLeast(1),
      totalChars = totalChars.coerceAtLeast(0L),
      lastReadAt = System.currentTimeMillis(),
    )
  }

  fun saveProgressAsync(
    bookId: Long,
    locator: Locator,
    progressPercent: Float,
    totalUnits: Int,
    totalChars: Long,
  ) {
    persistScope.launch {
      runCatching {
        bookDao.updateProgress(
          bookId = bookId,
          chapterIndex = locator.chapterIndex.coerceAtLeast(0),
          paragraphIndex = locator.paragraphIndex.coerceAtLeast(0),
          charOffset = locator.charOffset.coerceAtLeast(0),
          progressPercent = progressPercent.coerceIn(0f, 1f),
          totalUnits = totalUnits.coerceAtLeast(1),
          totalChars = totalChars.coerceAtLeast(0L),
          lastReadAt = System.currentTimeMillis(),
        )
      }
    }
  }

  suspend fun toggleBookmark(
    bookId: Long,
    locator: Locator,
    unitTitle: String,
    previewText: String,
  ): Boolean = withContext(Dispatchers.IO) {
    val existing = bookmarkDao.findBookmarkAtLocator(
      bookId = bookId,
      chapterIndex = locator.chapterIndex,
      paragraphIndex = locator.paragraphIndex,
      charOffset = locator.charOffset,
    )
    if (existing != null) {
      bookmarkDao.deleteBookmarkById(existing.id)
      false
    } else {
      bookmarkDao.insertBookmark(
        BookmarkEntity(
          bookId = bookId,
          locator = locator,
          unitTitle = unitTitle,
          previewText = previewText.take(140),
        )
      )
      true
    }
  }

  suspend fun deleteBookmark(bookmarkId: Long) = withContext(Dispatchers.IO) {
    bookmarkDao.deleteBookmarkById(bookmarkId)
  }

  suspend fun saveQuote(
    book: BookEntity,
    locator: Locator,
    unitTitle: String,
    text: String,
    note: String,
    colorTag: String,
  ) = withContext(Dispatchers.IO) {
    quoteDao.insertQuote(
      QuoteEntity(
        bookId = book.id,
        bookTitle = book.title,
        bookAuthor = book.author,
        locator = locator,
        unitTitle = unitTitle,
        text = text,
        note = note.trim(),
        colorTag = colorTag,
      )
    )
  }

  suspend fun deleteQuote(quoteId: Long) = withContext(Dispatchers.IO) {
    quoteDao.deleteQuoteById(quoteId)
  }

  suspend fun deleteBook(book: BookEntity) = withContext(Dispatchers.IO) {
    runCatching { File(book.localFilePath).delete() }
    book.coverPath?.let { runCatching { File(it).delete() } }
    book.cacheFilePath?.let { runCatching { File(it).delete() } }
    bookDao.deleteBookById(book.id)
  }

  private fun queryFileName(uri: Uri): String? {
    if (uri.scheme == "content") {
      context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (idx >= 0) return cursor.getString(idx)
        }
      }
    }
    return uri.lastPathSegment?.substringAfterLast('/')
  }

  private fun computeSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buf = ByteArray(8192)
      var read = input.read(buf)
      while (read > 0) {
        digest.update(buf, 0, read)
        read = input.read(buf)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }
}
