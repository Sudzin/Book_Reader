package com.example.reader.engine

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object TxtParser {

  private val chapterRegex = Regex(
    pattern = """^(глава|часть|раздел|том|книга|chapter|part|book|пролог|эпилог|введение|заключение)(?:$|[\s.:—–-].*$)|^[IVXLCDM]{1,7}\.?$""",
    option = RegexOption.IGNORE_CASE,
  )

  fun parseForImport(file: File, fallbackTitle: String): Pair<ParsedBookMetadata, LoadedBookContent.Reflowable> {
    val content = parse(file)
    val meta = ParsedBookMetadata(
      title = fallbackTitle,
      author = "Локальный текст",
      coverBytes = null,
      totalUnits = content.chapters.size.coerceAtLeast(1),
      totalChars = content.totalChars,
    )
    return meta to content
  }

  fun parse(file: File): LoadedBookContent.Reflowable {
    if (!file.exists() || file.length() == 0L) {
      throw BookParseException("Текстовый файл пуст или недоступен.")
    }
    val rawBytes = try {
      file.readBytes()
    } catch (t: Throwable) {
      throw BookParseException("Не удалось прочитать текстовый файл.", t)
    }
    val text = decodeBytesSmart(rawBytes)
    val rawLines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')

    val blocks = mutableListOf<String>()
    val currentPara = StringBuilder()
    for (line in rawLines) {
      val trimmed = line.trim()
      if (trimmed.isEmpty()) {
        if (currentPara.isNotEmpty()) {
          blocks.add(currentPara.toString())
          currentPara.clear()
        }
      } else {
        if (currentPara.isNotEmpty()) currentPara.append(' ')
        currentPara.append(trimmed)
      }
    }
    if (currentPara.isNotEmpty()) {
      blocks.add(currentPara.toString())
    }

    if (blocks.isEmpty()) {
      throw BookParseException("Текстовый файл пуст или не содержит читаемого текста.")
    }

    val chapters = mutableListOf<BookChapter>()
    var currentTitle = "Часть 1"
    var currentParagraphs = mutableListOf<BookParagraph>()

    fun flushChapter() {
      if (currentParagraphs.isNotEmpty()) {
        val idx = chapters.size
        val resolvedTitle = currentTitle.trim().ifBlank { "Часть ${idx + 1}" }
        chapters.add(BookChapter(idx, resolvedTitle, currentParagraphs.toList()))
        currentParagraphs = mutableListOf()
      }
    }

    for (block in blocks) {
      val looksLikeHeading = block.length <= 90 && chapterRegex.matches(block)
      if (looksLikeHeading && currentParagraphs.size >= 3) {
        flushChapter()
        currentTitle = block
        currentParagraphs.add(BookParagraph(currentParagraphs.size, block, ParagraphKind.HEADING))
      } else if (looksLikeHeading && currentParagraphs.isEmpty()) {
        currentTitle = block
        currentParagraphs.add(BookParagraph(0, block, ParagraphKind.HEADING))
      } else {
        currentParagraphs.add(BookParagraph(currentParagraphs.size, block, ParagraphKind.BODY))
        if (currentParagraphs.size >= 85) {
          flushChapter()
          currentTitle = "Часть ${chapters.size + 1}"
        }
      }
    }
    flushChapter()

    val toc = TocFilter.filterToc(
      entries = chapters.map { TocEntry(it.title, it.index) },
      bookTitle = "",
      bookAuthor = "",
    )
    return ChapterMetricsBuilder.buildReflowable(chapters, toc)
  }

  private fun decodeBytesSmart(bytes: ByteArray): String {
    if (bytes.isEmpty()) return ""
    val decoded = when {
      bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
        String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
      bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
        String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
      bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
        String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
      else -> {
        if (bytes.any { it == 0.toByte() }) {
          throw BookParseException("Файл повреждён или содержит двоичные данные вместо текста.")
        }
        try {
          val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
          decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
          val cp1251 = runCatching { Charset.forName("windows-1251") }.getOrDefault(Charsets.ISO_8859_1)
          String(bytes, cp1251)
        }
      }
    }

    if (decoded.isNotEmpty()) {
      val sampleLen = decoded.length.coerceAtMost(2048)
      var badChars = 0
      for (i in 0 until sampleLen) {
        val c = decoded[i]
        if ((c < ' ' && c != '\n' && c != '\r' && c != '\t') || c == '\uFFFD') {
          badChars++
        }
      }
      if (badChars * 100 / sampleLen > 15) {
        throw BookParseException("Файл повреждён или не является текстовым документом.")
      }
    }
    return decoded
  }
}
