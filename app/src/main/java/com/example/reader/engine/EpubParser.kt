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

  fun extractMetadata(file: File, fallbackTitle: String): ParsedBookMetadata {
    ZipFile(file).use { zip ->
      val opf = parseOpf(zip, fallbackTitle)
      val coverBytes = opf.coverZipPath?.let { path ->
        findZipEntry(zip, path)?.let { entry -> zip.getInputStream(entry).use { it.readBytes() } }
      }
      return ParsedBookMetadata(
        title = opf.title,
        author = opf.author,
        coverBytes = coverBytes,
        totalUnits = opf.spinePaths.size.coerceAtLeast(1),
      )
    }
  }

  fun parse(file: File, fallbackTitle: String): LoadedBookContent.Reflowable {
    ZipFile(file).use { zip ->
      val opf = parseOpf(zip, fallbackTitle)
      val tocTitleByHref = buildTocMap(zip, opf)

      val chapters = mutableListOf<BookChapter>()
      opf.spinePaths.forEachIndexed { _, zipPath ->
        val entry = findZipEntry(zip, zipPath) ?: return@forEachIndexed
        val html = zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
        val paragraphs = parseXhtmlParagraphs(html)
        if (paragraphs.isNotEmpty()) {
          val normalized = zipPath.lowercase()
          val matchedToc = tocTitleByHref.entries.firstOrNull { (href, _) ->
            normalized.endsWith(href.lowercase())
          }?.value
          val headingText = paragraphs.firstOrNull {
            it.kind == ParagraphKind.HEADING || it.kind == ParagraphKind.SUBHEADING
          }?.text
          val chapterTitle = matchedToc
            ?: headingText?.take(80)
            ?: "Глава ${chapters.size + 1}"
          chapters.add(
            BookChapter(
              index = chapters.size,
              title = chapterTitle,
              paragraphs = paragraphs,
            )
          )
        }
      }

      val finalChapters = chapters.ifEmpty {
        listOf(
          BookChapter(
            index = 0,
            title = opf.title,
            paragraphs = listOf(BookParagraph(0, "Не удалось извлечь содержимое EPUB.", ParagraphKind.BODY)),
          )
        )
      }
      val toc = finalChapters.map { TocEntry(it.title, it.index) }
      return LoadedBookContent.Reflowable(finalChapters, toc)
    }
  }

  private fun parseOpf(zip: ZipFile, fallbackTitle: String): OpfPackage {
    val containerEntry = findZipEntry(zip, "META-INF/container.xml")
    var opfPath: String? = null
    if (containerEntry != null) {
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
    if (opfPath.isNullOrBlank()) {
      opfPath = zip.entries().asSequence().firstOrNull { it.name.endsWith(".opf", ignoreCase = true) }?.name
    }
    if (opfPath.isNullOrBlank()) {
      return OpfPackage(fallbackTitle, "Автор не указан", null, emptyList(), null, null)
    }

    val opfDir = opfPath.substringBeforeLast('/', "")
    val opfEntry = findZipEntry(zip, opfPath)
      ?: return OpfPackage(fallbackTitle, "Автор не указан", null, emptyList(), null, null)

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
