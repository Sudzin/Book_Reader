package com.example.reader.engine

import android.util.Base64
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

object Fb2Parser {

  fun extractMetadata(file: File, fallbackTitle: String): ParsedBookMetadata {
    val xmlBytes = readFb2XmlBytes(file)
    val parsed = parseFb2(xmlBytes, fallbackTitle)
    return ParsedBookMetadata(
      title = parsed.title,
      author = parsed.author,
      coverBytes = parsed.coverBytes,
      totalUnits = parsed.chapters.size.coerceAtLeast(1),
    )
  }

  fun parse(file: File, fallbackTitle: String): LoadedBookContent.Reflowable {
    val xmlBytes = readFb2XmlBytes(file)
    val parsed = parseFb2(xmlBytes, fallbackTitle)
    val chapters = parsed.chapters.ifEmpty {
      listOf(
        BookChapter(
          index = 0,
          title = parsed.title,
          paragraphs = listOf(BookParagraph(0, "Не удалось извлечь текст книги.", ParagraphKind.BODY)),
        )
      )
    }
    val toc = chapters.map { TocEntry(it.title, it.index) }
    return LoadedBookContent.Reflowable(chapters, toc)
  }

  private fun readFb2XmlBytes(file: File): ByteArray {
    val raw = file.readBytes()
    if (raw.size >= 2 && raw[0] == 0x50.toByte() && raw[1] == 0x4B.toByte()) {
      ZipInputStream(ByteArrayInputStream(raw)).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
          if (!entry.isDirectory && entry.name.lowercase().endsWith(".fb2")) {
            return zis.readBytes()
          }
          entry = zis.nextEntry
        }
      }
      ZipInputStream(ByteArrayInputStream(raw)).use { zis ->
        val first = zis.nextEntry
        if (first != null && !first.isDirectory) {
          return zis.readBytes()
        }
      }
    }
    return raw
  }

  private data class InternalFb2Result(
    val title: String,
    val author: String,
    val coverBytes: ByteArray?,
    val chapters: List<BookChapter>,
  )

  private fun detectXmlCharset(bytes: ByteArray): Charset {
    val head = String(bytes, 0, bytes.size.coerceAtMost(256), Charsets.ISO_8859_1)
    val match = Regex("""encoding=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(head)
    val enc = match?.groupValues?.getOrNull(1)?.trim()
    if (!enc.isNullOrEmpty()) {
      runCatching { return Charset.forName(enc) }
    }
    return Charsets.UTF_8
  }

  private fun parseFb2(xmlBytes: ByteArray, fallbackTitle: String): InternalFb2Result {
    val charset = detectXmlCharset(xmlBytes)
    val parser = Xml.newPullParser().apply {
      setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
      setInput(InputStreamReader(ByteArrayInputStream(xmlBytes), charset))
    }

    var bookTitle = ""
    val authorParts = mutableListOf<String>()
    var inTitleInfo = false
    var inAuthor = false
    var inFirstName = false
    var inLastName = false
    var inBookTitle = false
    var inCoverpage = false
    var coverBinaryId: String? = null

    val binaries = mutableMapOf<String, String>()
    var currentBinaryId: String? = null
    val currentBinaryData = StringBuilder()

    var inBody = false
    var bodyIsNotes = false
    var sectionDepth = 0
    var inTitleTag = false
    var inEpigraph = false
    var inPoem = false
    var inCite = false
    var inSubtitle = false
    var inParagraph = false
    var inV = false

    val textBuffer = StringBuilder()
    val titleBuffer = StringBuilder()

    val chapters = mutableListOf<BookChapter>()
    var activeChapterTitle = ""
    var activeParagraphs = mutableListOf<BookParagraph>()

    fun flushChapter() {
      if (activeParagraphs.isNotEmpty()) {
        val idx = chapters.size
        val title = activeChapterTitle.ifBlank { "Раздел ${idx + 1}" }
        chapters.add(BookChapter(idx, title, activeParagraphs.toList()))
        activeParagraphs = mutableListOf()
        activeChapterTitle = ""
      }
    }

    var eventType = parser.eventType
    while (eventType != XmlPullParser.END_DOCUMENT) {
      when (eventType) {
        XmlPullParser.START_TAG -> {
          when (parser.name.lowercase()) {
            "title-info" -> inTitleInfo = true
            "book-title" -> if (inTitleInfo) {
              inBookTitle = true
              textBuffer.clear()
            }
            "author" -> if (inTitleInfo) {
              inAuthor = true
              authorParts.clear()
            }
            "first-name", "middle-name" -> if (inAuthor) {
              inFirstName = true
              textBuffer.clear()
            }
            "last-name" -> if (inAuthor) {
              inLastName = true
              textBuffer.clear()
            }
            "coverpage" -> if (inTitleInfo) inCoverpage = true
            "image" -> if (inCoverpage && coverBinaryId == null) {
              for (i in 0 until parser.attributeCount) {
                if (parser.getAttributeName(i).endsWith("href", ignoreCase = true)) {
                  coverBinaryId = parser.getAttributeValue(i).removePrefix("#")
                  break
                }
              }
            }
            "binary" -> {
              currentBinaryId = parser.getAttributeValue(null, "id")
              currentBinaryData.clear()
            }
            "body" -> {
              inBody = true
              bodyIsNotes = parser.getAttributeValue(null, "name").equals("notes", ignoreCase = true)
              if (bodyIsNotes) {
                flushChapter()
                activeChapterTitle = "Примечания"
              }
            }
            "section" -> if (inBody && !bodyIsNotes) {
              if (sectionDepth == 0 || activeParagraphs.size >= 15) {
                flushChapter()
              }
              sectionDepth++
            }
            "title" -> if (inBody) {
              inTitleTag = true
              titleBuffer.clear()
            }
            "epigraph" -> if (inBody) inEpigraph = true
            "poem" -> if (inBody) inPoem = true
            "cite" -> if (inBody) inCite = true
            "subtitle" -> if (inBody) {
              inSubtitle = true
              textBuffer.clear()
            }
            "p" -> if (inBody) {
              inParagraph = true
              textBuffer.clear()
            }
            "v" -> if (inBody) {
              inV = true
              textBuffer.clear()
            }
          }
        }

        XmlPullParser.TEXT -> {
          val chunk = parser.text ?: ""
          if (currentBinaryId != null) {
            currentBinaryData.append(chunk)
          } else if (inBookTitle || inFirstName || inLastName || inSubtitle || inParagraph || inV) {
            textBuffer.append(chunk)
          } else if (inTitleTag) {
            titleBuffer.append(chunk)
          }
        }

        XmlPullParser.END_TAG -> {
          when (parser.name.lowercase()) {
            "title-info" -> inTitleInfo = false
            "book-title" -> if (inBookTitle) {
              inBookTitle = false
              if (bookTitle.isBlank()) bookTitle = textBuffer.toString().trim()
            }
            "first-name", "middle-name" -> if (inFirstName) {
              inFirstName = false
              val v = textBuffer.toString().trim()
              if (v.isNotEmpty()) authorParts.add(v)
            }
            "last-name" -> if (inLastName) {
              inLastName = false
              val v = textBuffer.toString().trim()
              if (v.isNotEmpty()) authorParts.add(v)
            }
            "author" -> inAuthor = false
            "coverpage" -> inCoverpage = false
            "binary" -> {
              val id = currentBinaryId
              if (id != null && currentBinaryData.isNotEmpty()) {
                binaries[id] = currentBinaryData.toString()
              }
              currentBinaryId = null
              currentBinaryData.clear()
            }
            "body" -> {
              flushChapter()
              inBody = false
              bodyIsNotes = false
            }
            "section" -> if (inBody && !bodyIsNotes) {
              sectionDepth = (sectionDepth - 1).coerceAtLeast(0)
              if (sectionDepth == 0) flushChapter()
            }
            "title" -> if (inTitleTag) {
              inTitleTag = false
              val heading = titleBuffer.toString().replace(Regex("\\s+"), " ").trim()
              if (heading.isNotEmpty()) {
                if (activeChapterTitle.isBlank()) activeChapterTitle = heading
                activeParagraphs.add(BookParagraph(activeParagraphs.size, heading, ParagraphKind.HEADING))
              }
            }
            "epigraph" -> inEpigraph = false
            "poem" -> inPoem = false
            "cite" -> inCite = false
            "subtitle" -> if (inSubtitle) {
              inSubtitle = false
              val sub = textBuffer.toString().replace(Regex("\\s+"), " ").trim()
              if (sub.isNotEmpty()) {
                activeParagraphs.add(BookParagraph(activeParagraphs.size, sub, ParagraphKind.SUBHEADING))
              }
            }
            "p" -> if (inParagraph) {
              inParagraph = false
              val pText = textBuffer.toString().replace(Regex("\\s+"), " ").trim()
              if (pText.isNotEmpty()) {
                if (inTitleTag) {
                  if (titleBuffer.isNotEmpty()) titleBuffer.append(' ')
                  titleBuffer.append(pText)
                } else {
                  val kind = when {
                    inEpigraph || inCite -> ParagraphKind.QUOTE
                    inPoem -> ParagraphKind.POEM
                    else -> ParagraphKind.BODY
                  }
                  activeParagraphs.add(BookParagraph(activeParagraphs.size, pText, kind))
                }
              }
            }
            "v" -> if (inV) {
              inV = false
              val vText = textBuffer.toString().replace(Regex("\\s+"), " ").trim()
              if (vText.isNotEmpty()) {
                activeParagraphs.add(BookParagraph(activeParagraphs.size, vText, ParagraphKind.POEM))
              }
            }
          }
        }
      }
      eventType = runCatching { parser.next() }.getOrDefault(XmlPullParser.END_DOCUMENT)
    }
    flushChapter()

    val coverBytes = coverBinaryId?.let { id ->
      binaries[id]?.let { b64 ->
        runCatching { Base64.decode(b64.replace("\\s".toRegex(), ""), Base64.DEFAULT) }.getOrNull()
      }
    }

    return InternalFb2Result(
      title = bookTitle.ifBlank { fallbackTitle },
      author = authorParts.joinToString(" ").ifBlank { "Автор не указан" },
      coverBytes = coverBytes,
      chapters = chapters,
    )
  }
}
