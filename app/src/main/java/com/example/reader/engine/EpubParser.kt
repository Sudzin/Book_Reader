package com.example.reader.engine

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipFile

object EpubParser {

  private data class OpfPackage(
    val title: String,
    val author: String,
    val coverZipPath: String?,
    val spinePaths: List<String>,
    val ncxZipPath: String?,
    val navZipPath: String?,
  )

  fun parseForImport(file: File, fallbackTitle: String): Pair<ParsedBookMetadata, LoadedBookContent.Reflowable> {
    if (!file.exists() || file.length() == 0L) {
      throw BookParseException("Файл EPUB пуст или недоступен.")
    }
    val zip = try {
      ZipFile(file)
    } catch (e: Throwable) {
      throw BookParseException("Файл EPUB повреждён или не является корректным архивом.", e)
    }

    return try {
      zip.use { archive ->
        val opf = parseOpf(archive, fallbackTitle)
        val coverBytes = opf.coverZipPath?.let { path ->
          runCatching {
            findZipEntry(archive, path)?.let { entry ->
              archive.getInputStream(entry).use { it.readBytes() }
            }
          }.getOrNull()
        }
        val tocTitleByHref = runCatching { buildTocMap(archive, opf) }.getOrDefault(emptyMap())

        val chapters = mutableListOf<BookChapter>()
        opf.spinePaths.forEach { zipPath ->
          val entry = findZipEntry(archive, zipPath) ?: return@forEach
          val html = runCatching {
            archive.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
          }.getOrNull() ?: return@forEach
          val paragraphs = parseXhtmlParagraphs(html)
          if (paragraphs.isNotEmpty()) {
            val normalized = zipPath.lowercase()
            val matchedToc = tocTitleByHref.entries.firstOrNull { (href, _) ->
              normalized.endsWith(href.lowercase())
            }?.value?.trim()?.takeIf { it.isNotEmpty() }
            val headingText = paragraphs.firstOrNull {
              it.kind == ParagraphKind.HEADING || it.kind == ParagraphKind.SUBHEADING
            }?.text?.trim()?.takeIf { it.isNotEmpty() }
            val chapterTitle = matchedToc
              ?: headingText?.take(80)
              ?: "Часть ${chapters.size + 1}"
            chapters.add(
              BookChapter(
                index = chapters.size,
                title = chapterTitle,
                paragraphs = paragraphs,
              )
            )
          }
        }

        if (chapters.isEmpty() || chapters.sumOf { ch -> ch.paragraphs.sumOf { it.text.length } } == 0) {
          throw BookParseException("Файл EPUB пуст или не содержит читаемого текста.")
        }

        val toc = TocFilter.filterToc(
          entries = chapters.map { TocEntry(it.title, it.index) },
          bookTitle = opf.title,
          bookAuthor = opf.author,
        )
        val content = ChapterMetricsBuilder.buildReflowable(chapters, toc)
        val meta = ParsedBookMetadata(
          title = opf.title,
          author = opf.author,
          coverBytes = coverBytes,
          totalUnits = content.chapters.size.coerceAtLeast(1),
          totalChars = content.totalChars,
        )
        meta to content
      }
    } catch (e: BookParseException) {
      throw e
    } catch (t: Throwable) {
      throw BookParseException("Не удалось прочитать EPUB: файл повреждён.", t)
    }
  }

  fun parse(file: File, fallbackTitle: String): LoadedBookContent.Reflowable =
    parseForImport(file, fallbackTitle).second

  private fun parseOpf(zip: ZipFile, fallbackTitle: String): OpfPackage {
    val containerEntry = findZipEntry(zip, "META-INF/container.xml")
    var opfPath: String? = null
    if (containerEntry != null) {
      runCatching {
        zip.getInputStream(containerEntry).use { stream ->
          val parser = newParser(stream)
          var ev = parser.eventType
          while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && parser.name.endsWith("rootfile", ignoreCase = true)) {
              opfPath = parser.getAttributeValue(null, "full-path")
              if (!opfPath.isNullOrBlank()) break
            }
            ev = runCatching { parser.next() }.getOrDefault(XmlPullParser.END_DOCUMENT)
          }
        }
      }
    }
    if (opfPath.isNullOrBlank()) {
      opfPath = zip.entries().asSequence().firstOrNull { it.name.endsWith(".opf", ignoreCase = true) }?.name
    }
    if (opfPath.isNullOrBlank()) {
      throw BookParseException("Файл EPUB повреждён: отсутствует структура книги (OPF).")
    }

    val opfDir = opfPath.substringBeforeLast('/', "")
    val opfEntry = findZipEntry(zip, opfPath)
      ?: throw BookParseException("Файл EPUB повреждён: не найден файл оглавления (OPF).")

    var title = ""
    var author = ""
    var coverId: String? = null
    var coverHrefFromProperty: String? = null
    var navHref: String? = null
    var ncxId: String? = null

    val manifestHrefs = mutableMapOf<String, String>()
    val manifestMedia = mutableMapOf<String, String>()
    val spineIds = mutableListOf<String>()

    zip.getInputStream(opfEntry).use { stream ->
      val parser = newParser(stream)
      var currentTag = ""
      var ev = parser.eventType
      while (ev != XmlPullParser.END_DOCUMENT) {
        when (ev) {
          XmlPullParser.START_TAG -> {
            currentTag = parser.name.substringAfter(':').lowercase()
            when (currentTag) {
              "meta" -> {
                val name = parser.getAttributeValue(null, "name")
                val content = parser.getAttributeValue(null, "content")
                if (name.equals("cover", ignoreCase = true) && !content.isNullOrBlank()) {
                  coverId = content
                }
              }
              "item" -> {
                val id = parser.getAttributeValue(null, "id").orEmpty()
                val href = parser.getAttributeValue(null, "href").orEmpty()
                val media = parser.getAttributeValue(null, "media-type").orEmpty()
                val props = parser.getAttributeValue(null, "properties").orEmpty()
                if (id.isNotEmpty() && href.isNotEmpty()) {
                  manifestHrefs[id] = href
                  manifestMedia[id] = media
                  if (props.contains("cover-image", ignoreCase = true)) {
                    coverHrefFromProperty = href
                  }
                  if (props.contains("nav", ignoreCase = true)) {
                    navHref = href
                  }
                }
              }
              "spine" -> {
                ncxId = parser.getAttributeValue(null, "toc")
              }
              "itemref" -> {
                val idref = parser.getAttributeValue(null, "idref")
                val linear = parser.getAttributeValue(null, "linear")
                if (!idref.isNullOrBlank() && linear != "no") {
                  spineIds.add(idref)
                }
              }
            }
          }
          XmlPullParser.TEXT -> {
            val txt = parser.text?.trim().orEmpty()
            if (txt.isNotEmpty()) {
              if (currentTag == "title" && title.isBlank()) title = txt
              if (currentTag == "creator" && author.isBlank()) author = txt
            }
          }
          XmlPullParser.END_TAG -> currentTag = ""
        }
        ev = runCatching { parser.next() }.getOrDefault(XmlPullParser.END_DOCUMENT)
      }
    }

    val resolvedCoverHref = coverHrefFromProperty
      ?: coverId?.let { manifestHrefs[it] }
      ?: manifestHrefs.entries.firstOrNull { (id, href) ->
        manifestMedia[id]?.startsWith("image/") == true &&
          (id.contains("cover", ignoreCase = true) || href.contains("cover", ignoreCase = true))
      }?.value

    val resolvedNcxHref = ncxId?.let { manifestHrefs[it] }
      ?: manifestHrefs.entries.firstOrNull { (id, _) ->
        manifestMedia[id] == "application/x-dtbncx+xml"
      }?.value

    val spinePaths = spineIds.mapNotNull { manifestHrefs[it] }.map { resolveRelative(opfDir, it) }
    return OpfPackage(
      title = title.ifBlank { fallbackTitle },
      author = author.ifBlank { "Автор не указан" },
      coverZipPath = resolvedCoverHref?.let { resolveRelative(opfDir, it) },
      spinePaths = spinePaths,
      ncxZipPath = resolvedNcxHref?.let { resolveRelative(opfDir, it) },
      navZipPath = navHref?.let { resolveRelative(opfDir, it) },
    )
  }

  private fun buildTocMap(zip: ZipFile, opf: OpfPackage): Map<String, String> {
    val map = linkedMapOf<String, String>()
    opf.ncxZipPath?.let { ncxPath ->
      val ncxDir = ncxPath.substringBeforeLast('/', "")
      findZipEntry(zip, ncxPath)?.let { entry ->
        zip.getInputStream(entry).use { stream ->
          val parser = newParser(stream)
          var inNavLabel = false
          var inText = false
          var label = ""
          var ev = parser.eventType
          while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
              XmlPullParser.START_TAG -> when (parser.name.substringAfter(':').lowercase()) {
                "navlabel" -> {
                  inNavLabel = true
                  label = ""
                }
                "text" -> if (inNavLabel) inText = true
                "content" -> {
                  val src = parser.getAttributeValue(null, "src")?.substringBefore('#')
                  if (!src.isNullOrBlank() && label.isNotBlank()) {
                    map.putIfAbsent(resolveRelative(ncxDir, src), label.trim())
                  }
                }
              }
              XmlPullParser.TEXT -> if (inText) label += parser.text.orEmpty()
              XmlPullParser.END_TAG -> when (parser.name.substringAfter(':').lowercase()) {
                "text" -> inText = false
                "navlabel" -> inNavLabel = false
              }
            }
            ev = runCatching { parser.next() }.getOrDefault(XmlPullParser.END_DOCUMENT)
          }
        }
      }
    }
    return map
  }

  private fun parseXhtmlParagraphs(html: String): List<BookParagraph> {
    val result = mutableListOf<BookParagraph>()
    val cleanHtml = html
      .replace("&nbsp;", " ")
      .replace("&mdash;", "—")
      .replace("&ndash;", "–")
      .replace("&laquo;", "«")
      .replace("&raquo;", "»")
      .replace("&hellip;", "…")
      .replace("&rsquo;", "’")
      .replace("&lsquo;", "‘")
      .replace("&rdquo;", "”")
      .replace("&ldquo;", "“")

    val xmlAttempt = runCatching {
      val parser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(StringReader(cleanHtml))
      }
      var inBody = false
      var activeTag: String? = null
      var inBlockquote = false
      val buf = StringBuilder()

      fun flushBlock(kind: ParagraphKind) {
        val text = buf.toString().replace(Regex("\\s+"), " ").trim()
        if (text.isNotEmpty()) {
          result.add(BookParagraph(result.size, text, kind))
        }
        buf.clear()
      }

      var ev = parser.eventType
      while (ev != XmlPullParser.END_DOCUMENT) {
        when (ev) {
          XmlPullParser.START_TAG -> {
            val tag = parser.name.substringAfter(':').lowercase()
            when (tag) {
              "body" -> inBody = true
              "blockquote" -> if (inBody) inBlockquote = true
              "h1", "h2", "h3", "h4", "h5", "h6", "p", "li", "pre" -> if (inBody) {
                activeTag = tag
                buf.clear()
              }
              "br" -> if (inBody && activeTag != null) buf.append(' ')
            }
          }
          XmlPullParser.TEXT -> if (inBody && activeTag != null) {
            buf.append(parser.text.orEmpty())
          }
          XmlPullParser.END_TAG -> {
            val tag = parser.name.substringAfter(':').lowercase()
            if (tag == "body") inBody = false
            if (tag == "blockquote") inBlockquote = false
            if (tag == activeTag) {
              val kind = when (tag) {
                "h1", "h2", "h3" -> ParagraphKind.HEADING
                "h4", "h5", "h6" -> ParagraphKind.SUBHEADING
                "pre" -> ParagraphKind.CODE
                else -> if (inBlockquote) ParagraphKind.QUOTE else ParagraphKind.BODY
              }
              flushBlock(kind)
              activeTag = null
            }
          }
        }
        ev = parser.next()
      }
    }

    if (xmlAttempt.isFailure || result.isEmpty()) {
      result.clear()
      val stripped = cleanHtml
        .replace(Regex("<(script|style|head)[^>]*>[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("</?(p|div|h[1-6]|br|li|blockquote)[^>]*>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
      stripped.split('\n').forEach { line ->
        val trimmed = line.replace(Regex("\\s+"), " ").trim()
        if (trimmed.isNotEmpty()) {
          result.add(BookParagraph(result.size, trimmed, ParagraphKind.BODY))
        }
      }
    }
    return result
  }

  private fun newParser(stream: InputStream): XmlPullParser = Xml.newPullParser().apply {
    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    setInput(stream, "UTF-8")
  }

  private fun resolveRelative(baseDir: String, href: String): String {
    val cleanHref = href.substringBefore('#').trimStart('/')
    if (baseDir.isBlank()) return cleanHref
    val parts = (baseDir.split('/') + cleanHref.split('/')).toMutableList()
    val resolved = mutableListOf<String>()
    for (p in parts) {
      when (p) {
        "", "." -> Unit
        ".." -> if (resolved.isNotEmpty()) resolved.removeAt(resolved.lastIndex)
        else -> resolved.add(p)
      }
    }
    return resolved.joinToString("/")
  }

  private fun findZipEntry(zip: ZipFile, path: String) =
    zip.getEntry(path) ?: zip.entries().asSequence().firstOrNull {
      it.name.equals(path, ignoreCase = true)
    }
}
