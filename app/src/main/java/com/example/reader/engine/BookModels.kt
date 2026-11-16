package com.example.reader.engine

enum class ParagraphKind {
  HEADING,
  SUBHEADING,
  BODY,
  QUOTE,
  POEM,
  CODE,
  IMAGE,
}

class BookParseException(
  override val message: String,
  cause: Throwable? = null,
) : Exception(message, cause)

data class Locator(
  val chapterIndex: Int = 0,
  val paragraphIndex: Int = 0,
  val charOffset: Int = 0,
)

data class BookParagraph(
  val index: Int,
  val text: String,
  val kind: ParagraphKind = ParagraphKind.BODY,
  val charOffsetInChapter: Int = 0,
  val imagePath: String? = null,
  val imageWidth: Int = 0,
  val imageHeight: Int = 0,
  val imageAlt: String? = null,
) {
  val textLength: Int get() = text.length
}

data class BookChapter(
  val index: Int,
  val title: String,
  val paragraphs: List<BookParagraph>,
  val charCount: Long = paragraphs.sumOf { it.text.length.toLong() },
  val startCharOffset: Long = 0L,
)

data class TocEntry(
  val title: String,
  val unitIndex: Int,
)

data class ParsedBookMetadata(
  val title: String,
  val author: String,
  val coverBytes: ByteArray?,
  val totalUnits: Int,
  val totalChars: Long = 0L,
)

sealed interface LoadedBookContent {
  data class Reflowable(
    val chapters: List<BookChapter>,
    val toc: List<TocEntry>,
    val totalChars: Long = chapters.sumOf { it.charCount },
  ) : LoadedBookContent {
    fun progressFor(locator: Locator): Float {
      if (totalChars <= 0L || chapters.isEmpty()) return 0f
      val chIdx = locator.chapterIndex.coerceIn(0, chapters.lastIndex)
      val chapter = chapters[chIdx]
      if (chapter.paragraphs.isEmpty()) {
        return (chapter.startCharOffset.toDouble() / totalChars.toDouble()).toFloat().coerceIn(0f, 1f)
      }
      val paraIdx = locator.paragraphIndex.coerceIn(0, chapter.paragraphs.lastIndex)
      val para = chapter.paragraphs[paraIdx]
      val charInPara = locator.charOffset.coerceIn(0, para.text.length)
      val accumulated = chapter.startCharOffset + para.charOffsetInChapter.toLong() + charInPara.toLong()
      return (accumulated.toDouble() / totalChars.toDouble()).toFloat().coerceIn(0f, 1f)
    }
  }

  data class PdfDocumentContent(
    val filePath: String,
    val pageCount: Int,
    val toc: List<TocEntry>,
  ) : LoadedBookContent {
    fun progressFor(locator: Locator): Float {
      if (pageCount <= 1) return 1f
      val page = locator.chapterIndex.coerceIn(0, pageCount - 1)
      return ((page + 1).toFloat() / pageCount.toFloat()).coerceIn(0f, 1f)
    }
  }
}

object ChapterMetricsBuilder {
  fun buildReflowable(
    rawChapters: List<BookChapter>,
    rawToc: List<TocEntry>,
  ): LoadedBookContent.Reflowable {
    var runningBookChars = 0L
    val normalizedChapters = rawChapters.mapIndexed { idx, ch ->
      var runningChapterChars = 0
      val normalizedParagraphs = ch.paragraphs.mapIndexed { pIdx, p ->
        val para = BookParagraph(
          index = pIdx,
          text = p.text,
          kind = p.kind,
          charOffsetInChapter = runningChapterChars,
          imagePath = p.imagePath,
          imageWidth = p.imageWidth,
          imageHeight = p.imageHeight,
          imageAlt = p.imageAlt,
        )
        runningChapterChars += p.text.length
        para
      }
      val chapterStart = runningBookChars
      val chapterChars = runningChapterChars.toLong()
      runningBookChars += chapterChars
      BookChapter(
        index = idx,
        title = ch.title,
        paragraphs = normalizedParagraphs,
        charCount = chapterChars,
        startCharOffset = chapterStart,
      )
    }
    return LoadedBookContent.Reflowable(
      chapters = normalizedChapters,
      toc = rawToc,
      totalChars = runningBookChars,
    )
  }
}

object TocFilter {
  private fun normalize(value: String): String =
    value.lowercase()
      .replace(Regex("[\\p{Punct}\\s—–-]+"), " ")
      .trim()

  fun filterToc(
    entries: List<TocEntry>,
    bookTitle: String,
    bookAuthor: String,
  ): List<TocEntry> {
    val normTitle = normalize(bookTitle)
    val normAuthor = normalize(bookAuthor)
    val authorTokens = normAuthor.split(' ').filter { it.length >= 2 }
    val ignoreAuthor = normAuthor.isEmpty() ||
      normAuthor == normalize("Автор не указан") ||
      normAuthor == normalize("Локальный текст")

    return entries.filter { entry ->
      val trimmed = entry.title.trim()
      val normEntry = normalize(trimmed)
      if (trimmed.isEmpty() || normEntry.isEmpty()) return@filter false

      if (!ignoreAuthor && normTitle.isNotEmpty()) {
        val combined1 = normalize("$bookTitle $bookAuthor")
        val combined2 = normalize("$bookAuthor $bookTitle")
        if (normEntry == combined1 || normEntry == combined2) return@filter false

        val containsTitle = normEntry.contains(normTitle)
        val containsAuthor = normEntry.contains(normAuthor) ||
          (authorTokens.isNotEmpty() && authorTokens.all { normEntry.contains(it) })
        if (containsTitle && containsAuthor && normEntry.length <= normTitle.length + normAuthor.length + 14) {
          return@filter false
        }
      }
      true
    }
  }
}
