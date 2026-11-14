package com.example.reader.engine

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object TxtParser {

  private val chapterRegex = Regex(
    pattern = """^(глава|часть|раздел|том|книга|chapter|part|book|пролог|эпилог|введение|заключение)\b.*$|^[IVXLCDM]{1,7}\.?$""",
    option = RegexOption.IGNORE_CASE,
  )

  fun extractMetadata(file: File, fallbackTitle: String): ParsedBookMetadata {
    val content = parse(file)
    return ParsedBookMetadata(
      title = fallbackTitle,
      author = "Локальный текст",
      coverBytes = null,
      totalUnits = content.chapters.size.coerceAtLeast(1),
    )
  }

  fun parse(file: File): LoadedBookContent.Reflowable {
    val text = decodeBytesSmart(file.readBytes())
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
      val emptyChapter = BookChapter(
        index = 0,
        title = "Текст",
        paragraphs = listOf(BookParagraph(0, "Файл пуст.", ParagraphKind.BODY)),
      )
      return LoadedBookContent.Reflowable(listOf(emptyChapter), listOf(TocEntry("Текст", 0)))
    }

    val chapters = mutableListOf<BookChapter>()
    var currentTitle = "Начало"
    var currentParagraphs = mutableListOf<BookParagraph>()

    fun flushChapter() {
      if (currentParagraphs.isNotEmpty()) {
        val idx = chapters.size
        chapters.add(BookChapter(idx, currentTitle, currentParagraphs.toList()))
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

    val toc = chapters.map { TocEntry(it.title, it.index) }
    return LoadedBookContent.Reflowable(chapters, toc)
  }

  private fun decodeBytesSmart(bytes: ByteArray): String {
    if (bytes.isEmpty()) return ""
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
      return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
    }
    if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
      return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
    }
    if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
      return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
    }
    return try {
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
