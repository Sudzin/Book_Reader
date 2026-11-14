package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.AppDatabase
import com.example.data.local.BookEntity
import com.example.data.local.BookFormat
import com.example.data.local.BookmarkEntity
import com.example.data.local.QuoteEntity
import com.example.reader.engine.EpubParser
import com.example.reader.engine.Fb2Parser
import com.example.reader.engine.LoadedBookContent
import com.example.reader.engine.PdfParser
import com.example.reader.engine.TxtParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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

  private val booksDir: File
    get() = File(context.filesDir, "books").apply { if (!exists()) mkdirs() }

  private val coversDir: File
    get() = File(context.filesDir, "covers").apply { if (!exists()) mkdirs() }

  val allBooks: Flow<List<BookEntity>> = bookDao.observeAllBooks()
  val allQuotes: Flow<List<QuoteEntity>> = quoteDao.observeAllQuotes()

  fun observeBook(bookId: Long): Flow<BookEntity?> = bookDao.observeBookById(bookId)

  fun observeBookmarksForBook(bookId: Long): Flow<List<BookmarkEntity>> =
    bookmarkDao.observeBookmarksForBook(bookId)

  fun observeQuotesForBook(bookId: Long): Flow<List<QuoteEntity>> =
    quoteDao.observeQuotesForBook(bookId)

  suspend fun importBookFromUri(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
    runCatching {
      val resolver = context.contentResolver
      val displayName = queryFileName(uri) ?: "book_${System.currentTimeMillis()}"
      val mimeType = resolver.getType(uri)
      val format = BookFormat.fromExtensionOrMime(displayName, mimeType)
        ?: return@withContext ImportResult.Failure(
          "Неподдерживаемый формат файла. Выберите EPUB, FB2, TXT или PDF."
        )

      val tempFile = File(context.cacheDir, "import_${UUID.randomUUID()}")
      resolver.openInputStream(uri)?.use { input ->
        tempFile.outputStream().use { output -> input.copyTo(output) }
      } ?: return@withContext ImportResult.Failure("Не удалось прочитать выбранный файл.")

      if (tempFile.length() == 0L) {
        tempFile.delete()
        return@withContext ImportResult.Failure("Выбранный файл пуст.")
      }

      val hash = computeSha256(tempFile)
      val existing = bookDao.getBookByHash(hash)
      if (existing != null && File(existing.localFilePath).exists()) {
        tempFile.delete()
        return@withContext ImportResult.Success(existing.id, alreadyExisted = true)
      }

      val ext = when {
        displayName.lowercase().endsWith(".fb2.zip") -> "fb2.zip"
        else -> format.name.lowercase()
      }
      val storedFile = File(booksDir, "${UUID.randomUUID()}.$ext")
      tempFile.copyTo(storedFile, overwrite = true)
      tempFile.delete()

      val fallbackTitle = displayName
        .removeSuffix(".fb2.zip")
        .substringBeforeLast('.')
        .ifBlank { "Без названия" }

      val metadata = when (format) {
        BookFormat.EPUB -> EpubParser.extractMetadata(storedFile, fallbackTitle)
        BookFormat.FB2 -> Fb2Parser.extractMetadata(storedFile, fallbackTitle)
        BookFormat.TXT -> TxtParser.extractMetadata(storedFile, fallbackTitle)
        BookFormat.PDF -> PdfParser.extractMetadata(storedFile, fallbackTitle)
      }

      val coverPath = metadata.coverBytes?.let { bytes ->
        val cFile = File(coversDir, "${UUID.randomUUID()}.jpg")
        cFile.writeBytes(bytes)
        cFile.absolutePath
      }

      val palette = listOf(0xFF9C422EL, 0xFF4F6B52L, 0xFF3E5673L, 0xFF7B580DL, 0xFF6B425EL)
      val accentColor = palette[kotlin.math.abs(metadata.title.hashCode()) % palette.size]

      val entity = BookEntity(
        title = metadata.title,
        author = metadata.author,
        format = format.name,
        localFilePath = storedFile.absolutePath,
        coverPath = coverPath,
        contentHash = hash,
        totalUnits = metadata.totalUnits.coerceAtLeast(1),
        currentUnitIndex = 0,
        currentParagraphIndex = 0,
        progressPercent = 0f,
        lastReadAt = System.currentTimeMillis(),
        accentColorHex = accentColor,
      )
      val newId = bookDao.insertBook(entity)
      ImportResult.Success(newId, alreadyExisted = false)
    }.getOrElse { err ->
      ImportResult.Failure(err.localizedMessage ?: "Ошибка импорта книги.")
    }
  }

  suspend fun loadBookContent(book: BookEntity): Result<LoadedBookContent> = withContext(Dispatchers.IO) {
    runCatching {
      val file = File(book.localFilePath)
      require(file.exists()) { "Файл книги не найден на устройстве." }
      when (BookFormat.valueOf(book.format)) {
        BookFormat.EPUB -> EpubParser.parse(file, book.title)
        BookFormat.FB2 -> Fb2Parser.parse(file, book.title)
        BookFormat.TXT -> TxtParser.parse(file)
        BookFormat.PDF -> PdfParser.loadDescriptor(file)
      }
    }
  }

  suspend fun saveProgress(
    bookId: Long,
    unitIndex: Int,
    paragraphIndex: Int,
    totalUnits: Int,
  ) = withContext(Dispatchers.IO) {
    val safeTotal = totalUnits.coerceAtLeast(1)
    val progress = if (safeTotal == 1) 1f else ((unitIndex + 1).toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)
    bookDao.updateProgress(
      bookId = bookId,
      unitIndex = unitIndex.coerceAtLeast(0),
      paragraphIndex = paragraphIndex.coerceAtLeast(0),
      progressPercent = progress,
      totalUnits = safeTotal,
      lastReadAt = System.currentTimeMillis(),
    )
  }

  suspend fun toggleBookmark(
    bookId: Long,
    unitIndex: Int,
    paragraphIndex: Int,
    unitTitle: String,
    previewText: String,
  ): Boolean = withContext(Dispatchers.IO) {
    val existing = bookmarkDao.findBookmarkInUnit(bookId, unitIndex)
    if (existing != null) {
      bookmarkDao.deleteBookmarkById(existing.id)
      false
    } else {
      bookmarkDao.insertBookmark(
        BookmarkEntity(
          bookId = bookId,
          unitIndex = unitIndex,
          paragraphIndex = paragraphIndex,
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
    unitIndex: Int,
    paragraphIndex: Int,
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
        unitIndex = unitIndex,
        paragraphIndex = paragraphIndex,
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
