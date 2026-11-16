package com.example.reader.engine

import android.util.Base64
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

object Fb2Parser {

  private data class BinaryEntry(
    val contentType: String?,
    val base64Data: String,
  )

  fun parseForImport(
    file: File,
    fallbackTitle: String,
    imageOutputDir: File? = null,
  ): Pair<ParsedBookMetadata, LoadedBookContent.Reflowable> {
    if (!file.exists() || file.length() == 0L) {
      throw BookParseException("Файл FB2 пуст или недоступен.")
    }
    val xmlBytes = readFb2XmlBytes(file)
    if (xmlBytes.isEmpty()) {
      throw BookParseException("Файл FB2 пуст.")
    }
    val parsed = try {
      parseFb2(xmlBytes, fallbackTitle, imageOutputDir)
    } catch (e: BookParseException) {
      throw e
    } catch (t: Throwable) {
      throw BookParseException("Файл FB2 повреждён или имеет неверный XML-формат.", t)
    }
    val chapters = parsed.chapters
    val hasReadableContent = chapters.any { ch ->
      ch.paragraphs.any { it.text.isNotBlank() || !it.imagePath.isNullOrBlank() }
    }
    if (chapters.isEmpty() || !hasReadableContent) {
      throw BookParseException("Файл FB2 повреждён или не содержит читаемого текста.")
    }
    val toc = TocFilter.filterToc(
      entries = chapters.map { TocEntry(it.title, it.index) },
      bookTitle = parsed.title,
      bookAuthor = parsed.author,
    )
    val content = ChapterMetricsBuilder.buildReflowable(chapters, toc)
    val meta = ParsedBookMetadata(
      title = parsed.title,
      author = parsed.author,
      coverBytes = parsed.coverBytes,
      totalUnits = content.chapters.size.coerceAtLeast(1),
      totalChars = content.totalChars,
    )
    return meta to content
  }

  fun parse(
    file: File,
    fallbackTitle: String,
    imageOutputDir: File? = null,
  ): LoadedBookContent.Reflowable = parseForImport(file, fallbackTitle, imageOutputDir).second

  private fun readFb2XmlBytes(file: File): ByteArray {
    val raw = try {
      file.readBytes()
    } catch (t: Throwable) {
      throw BookParseException("Не удалось прочитать файл FB2.", t)
    }
    if (raw.size >= 2 && raw[0] == 0x50.toByte() && raw[1] == 0x4B.toByte()) {
      return try {
        ZipInputStream(ByteArrayInputStream(raw)).use { zis ->
          var firstEntryBytes: ByteArray? = null
          var entry = zis.nextEntry
          while (entry != null) {
            if (!entry.isDirectory) {
              if (entry.name.lowercase().endsWith(".fb2")) {
                return zis.readBytes()
              }
              if (firstEntryBytes == null) {
                firstEntryBytes = zis.readBytes()
              }
            }
            entry = zis.nextEntry
          }
          firstEntryBytes ?: throw BookParseException("Архив FB2 пуст или не содержит файла книги.")
        }
      } catch (e: BookParseException) {
        throw e
      } catch (t: Throwable) {
        throw BookParseException("Архив FB2 повреждён и не может быть открыт.", t)
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
    if (bytes.size >= 2) {
      if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return Charsets.UTF_16LE
      if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return Charsets.UTF_16BE
    }
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
      return Charsets.UTF_8
    }
    val head = String(bytes, 0, bytes.size.coerceAtMost(256), Charsets.ISO_8859_1)
    val match = Regex("""encoding=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(head)
    val enc = match?.groupValues?.getOrNull(1)?.trim()
    if (!enc.isNullOrEmpty()) {
      runCatching { return Charset.forName(enc) }
    }
    return Charsets.UTF_8
  }

  private fun parseFb2(
    xmlBytes: ByteArray,
    fallbackTitle: String,
    imageOutputDir: File?,
  ): InternalFb2Result {
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

    val binaries = mutableMapOf<String, BinaryEntry>()
    var currentBinaryId: String? = null
    var currentBinaryContentType: String? = null
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
        val title = activeChapterTitle.trim().ifBlank { "Часть ${idx + 1}" }
        chapters.add(BookChapter(idx, title, activeParagraphs.toList()))
        activeParagraphs = mutableListOf()
        activeChapterTitle = ""
      }
    }

    fun currentParagraphKind(): ParagraphKind = when {
      inEpigraph || inCite -> ParagraphKind.QUOTE
      inPoem || inV -> ParagraphKind.POEM
      else -> ParagraphKind.BODY
    }

    fun flushInlineTextBeforeImage() {
      if (inParagraph || inV) {
        val buffered = textBuffer.toString().replace(Regex("\\s+"), " ").trim()
        if (buffered.isNotEmpty()) {
          activeParagraphs.add(
            BookParagraph(
              index = activeParagraphs.size,
              text = buffered,
              kind = currentParagraphKind(),
            )
          )
        }
        textBuffer.clear()
      }
    }

    var eventType = parser.eventType
    while (eventType != XmlPullParser.END_DOCUMENT) {
      when (eventType) {
        XmlPullParser.START_TAG -> {
          when (parser.name.substringAfter(':').lowercase()) {
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
            "image" -> {
              var hrefVal: String? = null
              var altVal: String? = null
              for (i in 0 until parser.attributeCount) {
                val attrName = parser.getAttributeName(i)
                when {
                  attrName.endsWith("href", ignoreCase = true) -> {
                    hrefVal = parser.getAttributeValue(i).trim().removePrefix("#").trim()
                  }
                  attrName.equals("alt", ignoreCase = true) ||
                    attrName.equals("title", ignoreCase = true) -> {
                    if (altVal.isNullOrBlank()) {
                      altVal = parser.getAttributeValue(i)?.trim()
                    }
                  }
                }
              }
              if (!hrefVal.isNullOrEmpty()) {
                if (inCoverpage && coverBinaryId == null) {
                  coverBinaryId = hrefVal
                } else if (inBody && !inTitleTag) {
                  flushInlineTextBeforeImage()
                  activeParagraphs.add(
                    BookParagraph(
                      index = activeParagraphs.size,
                      text = "",
                      kind = ParagraphKind.IMAGE,
                      imagePath = hrefVal,
                      imageAlt = altVal?.takeIf { it.isNotBlank() },
                    )
                  )
                }
              }
            }
            "binary" -> {
              currentBinaryId = parser.getAttributeValue(null, "id")?.trim()
              currentBinaryContentType = parser.getAttributeValue(null, "content-type")?.trim()
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
          when (parser.name.substringAfter(':').lowercase()) {
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
              if (!id.isNullOrEmpty() && currentBinaryData.isNotEmpty()) {
                binaries[id] = BinaryEntry(
                  contentType = currentBinaryContentType,
                  base64Data = currentBinaryData.toString(),
                )
              }
              currentBinaryId = null
              currentBinaryContentType = null
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
                  activeParagraphs.add(BookParagraph(activeParagraphs.size, pText, currentParagraphKind()))
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
      findBinaryEntry(binaries, id)?.let { decodeBinaryBytes(it.base64Data) }
    }

    val extractedCache = mutableMapOf<String, ExtractedImage?>()
    val resolvedChapters = chapters.mapNotNull { ch ->
      val resolvedParagraphs = ch.paragraphs.mapNotNull { para ->
        if (para.kind != ParagraphKind.IMAGE) {
          para
        } else {
          val refId = para.imagePath
          if (imageOutputDir == null || refId.isNullOrBlank()) {
            null
          } else {
            val extracted = extractedCache.getOrPut(refId) {
              val entry = findBinaryEntry(binaries, refId) ?: return@getOrPut null
              val bytes = decodeBinaryBytes(entry.base64Data) ?: return@getOrPut null
              val preferredExt = extensionFromContentType(entry.contentType)
                ?: refId.substringAfterLast('.', "").takeIf { it.isNotBlank() }
              BookImageExtractor.saveExtractedImage(
                outputDir = imageOutputDir,
                key = "fb2_$refId",
                bytes = bytes,
                preferredExt = preferredExt,
              )
            }
            if (extracted != null) {
              para.copy(
                imagePath = extracted.file.absolutePath,
                imageWidth = extracted.width,
                imageHeight = extracted.height,
              )
            } else {
              null
            }
          }
        }
      }.mapIndexed { idx, para -> para.copy(index = idx) }
      if (resolvedParagraphs.isEmpty()) null else ch.copy(paragraphs = resolvedParagraphs)
    }.mapIndexed { idx, ch -> ch.copy(index = idx) }

    return InternalFb2Result(
      title = bookTitle.ifBlank { fallbackTitle },
      author = authorParts.joinToString(" ").ifBlank { "Автор не указан" },
      coverBytes = coverBytes,
      chapters = resolvedChapters,
    )
  }

  private fun findBinaryEntry(binaries: Map<String, BinaryEntry>, rawId: String): BinaryEntry? {
    val cleanId = rawId.trim().removePrefix("#").trim()
    if (cleanId.isEmpty()) return null
    binaries[cleanId]?.let { return it }
    val decoded = runCatching { URLDecoder.decode(cleanId, "UTF-8") }.getOrDefault(cleanId)
    binaries[decoded]?.let { return it }
    return binaries.entries.firstOrNull { (k, _) ->
      k.equals(cleanId, ignoreCase = true) || k.equals(decoded, ignoreCase = true)
    }?.value
  }

  private fun decodeBinaryBytes(base64Data: String): ByteArray? =
    runCatching {
      Base64.decode(base64Data.replace("\\s".toRegex(), ""), Base64.DEFAULT)
    }.getOrNull()?.takeIf { it.isNotEmpty() }

  private fun extensionFromContentType(contentType: String?): String? {
    val mime = contentType?.lowercase()?.trim() ?: return null
    return when {
      mime.contains("png") -> "png"
      mime.contains("webp") -> "webp"
      mime.contains("gif") -> "gif"
      mime.contains("bmp") -> "bmp"
      mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
      else -> null
    }
  }
}
