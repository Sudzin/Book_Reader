package com.example.reader.engine

import android.util.Base64
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.io.StringReader
import java.net.URLDecoder
import java.util.zip.ZipEntry
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

  fun parseForImport(
    file: File,
    fallbackTitle: String,
    imageOutputDir: File? = null,
  ): Pair<ParsedBookMetadata, LoadedBookContent.Reflowable> {
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
        val extractedCache = mutableMapOf<String, ExtractedImage?>()

        fun resolveImage(baseDir: String, rawSrc: String): ExtractedImage? {
          if (imageOutputDir == null) return null
          val trimmed = rawSrc.trim()
          if (trimmed.isEmpty()) return null
          if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
          ) {
            return null
          }
          if (trimmed.startsWith("data:image/", ignoreCase = true)) {
            val base64Marker = trimmed.indexOf(";base64,", ignoreCase = true)
            if (base64Marker < 0) return null
            val mimePart = trimmed.substring(11, base64Marker).lowercase()
            val payload = trimmed.substring(base64Marker + 8)
            val bytes = runCatching {
              Base64.decode(payload.replace("\\s".toRegex(), ""), Base64.DEFAULT)
            }.getOrNull() ?: return null
            val key = "epub_data_${trimmed.hashCode()}_${trimmed.take(48)}"
            return extractedCache.getOrPut(key) {
              BookImageExtractor.saveExtractedImage(
                outputDir = imageOutputDir,
                key = key,
                bytes = bytes,
                preferredExt = mimePart.substringBefore(';'),
              )
            }
          }
          val resolvedPath = resolveRelative(baseDir, trimmed)
          if (resolvedPath.isBlank()) return null
          return extractedCache.getOrPut(resolvedPath) {
            val entry = findZipEntry(archive, resolvedPath) ?: return@getOrPut null
            val bytes = runCatching {
              archive.getInputStream(entry).use { it.readBytes() }
            }.getOrNull() ?: return@getOrPut null
            BookImageExtractor.saveExtractedImage(
              outputDir = imageOutputDir,
              key = "epub_$resolvedPath",
              bytes = bytes,
              preferredExt = resolvedPath.substringAfterLast('.', ""),
            )
          }
        }

        val coverBytes = resolveCoverBytes(archive, opf)
        val tocTitleByHref = runCatching { buildTocMap(archive, opf) }.getOrDefault(emptyMap())

        val chapters = mutableListOf<BookChapter>()
        opf.spinePaths.forEach { zipPath ->
          val entry = findZipEntry(archive, zipPath) ?: return@forEach
          val html = runCatching {
            archive.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
          }.getOrNull() ?: return@forEach
          val xhtmlDir = zipPath.substringBeforeLast('/', "")
          val paragraphs = parseXhtmlParagraphs(html) { src ->
            resolveImage(xhtmlDir, src)
          }
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

        val hasReadableContent = chapters.any { ch ->
          ch.paragraphs.any { it.text.isNotBlank() || !it.imagePath.isNullOrBlank() }
        }
        if (chapters.isEmpty() || !hasReadableContent) {
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

  fun parse(
    file: File,
    fallbackTitle: String,
    imageOutputDir: File? = null,
  ): LoadedBookContent.Reflowable = parseForImport(file, fallbackTitle, imageOutputDir).second

  private fun resolveCoverBytes(archive: ZipFile, opf: OpfPackage): ByteArray? {
    val candidate = opf.coverZipPath
    if (candidate != null) {
      val lower = candidate.lowercase()
      if (!lower.endsWith(".xhtml") && !lower.endsWith(".html") && !lower.endsWith(".htm")) {
        val bytes = runCatching {
          findZipEntry(archive, candidate)?.let { entry ->
            archive.getInputStream(entry).use { it.readBytes() }
          }
        }.getOrNull()
        if (bytes != null && BookImageExtractor.probeDimensions(bytes) != null) {
          return bytes
        }
      } else {
        extractFirstImageBytesFromXhtml(archive, candidate)?.let { return it }
      }
    }
    val firstSpine = opf.spinePaths.firstOrNull()
    if (firstSpine != null && firstSpine.contains("cover", ignoreCase = true)) {
      extractFirstImageBytesFromXhtml(archive, firstSpine)?.let { return it }
    }
    return null
  }

  private fun extractFirstImageBytesFromXhtml(archive: ZipFile, xhtmlPath: String): ByteArray? {
    val entry = findZipEntry(archive, xhtmlPath) ?: return null
    val html = runCatching {
      archive.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrNull() ?: return null
    val dir = xhtmlPath.substringBeforeLast('/', "")
    val match = Regex("""<(?:img|image)\b[^>]*(?:src|xlink:href|href)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
      .find(html) ?: return null
    val src = match.groupValues.getOrNull(1)?.trim() ?: return null
    if (src.isEmpty() || src.startsWith("http", ignoreCase = true) || src.startsWith("data:", ignoreCase = true)) {
      return null
    }
    val imgPath = resolveRelative(dir, src)
    val imgEntry = findZipEntry(archive, imgPath) ?: return null
    val bytes = runCatching { archive.getInputStream(imgEntry).use { it.readBytes() } }.getOrNull() ?: return null
    return if (BookImageExtractor.probeDimensions(bytes) != null) bytes else null
  }

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

  private fun parseXhtmlParagraphs(
    html: String,
    resolveImage: (String) -> ExtractedImage?,
  ): List<BookParagraph> {
    val result = mutableListOf<BookParagraph>()
    val cleanHtml = decodeCommonHtmlEntities(html)

    val xmlAttempt = runCatching {
      val parser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(StringReader(cleanHtml))
      }
      var inBody = false
      var activeTag: String? = null
      var inBlockquote = false
      val buf = StringBuilder()

      fun kindForTag(tag: String?): ParagraphKind = when (tag) {
        "h1", "h2", "h3" -> ParagraphKind.HEADING
        "h4", "h5", "h6" -> ParagraphKind.SUBHEADING
        "pre" -> ParagraphKind.CODE
        else -> if (inBlockquote) ParagraphKind.QUOTE else ParagraphKind.BODY
      }

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
                if (activeTag != null && buf.isNotEmpty()) {
                  flushBlock(kindForTag(activeTag))
                }
                activeTag = tag
                buf.clear()
              }
              "br" -> if (inBody && activeTag != null) buf.append(' ')
              "img", "image" -> if (inBody) {
                var srcVal: String? = null
                var altVal: String? = null
                for (i in 0 until parser.attributeCount) {
                  val attr = parser.getAttributeName(i)
                  when {
                    attr.equals("src", ignoreCase = true) ||
                      attr.equals("href", ignoreCase = true) ||
                      attr.endsWith(":href", ignoreCase = true) -> {
                      if (srcVal.isNullOrBlank()) {
                        srcVal = parser.getAttributeValue(i)?.trim()
                      }
                    }
                    attr.equals("alt", ignoreCase = true) ||
                      attr.equals("title", ignoreCase = true) -> {
                      if (altVal.isNullOrBlank()) {
                        altVal = parser.getAttributeValue(i)?.trim()
                      }
                    }
                  }
                }
                val resolved = srcVal?.let(resolveImage)
                if (resolved != null) {
                  if (activeTag != null) {
                    flushBlock(kindForTag(activeTag))
                  }
                  result.add(
                    BookParagraph(
                      index = result.size,
                      text = "",
                      kind = ParagraphKind.IMAGE,
                      imagePath = resolved.file.absolutePath,
                      imageWidth = resolved.width,
                      imageHeight = resolved.height,
                      imageAlt = altVal?.takeIf { it.isNotBlank() },
                    )
                  )
                }
              }
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
              flushBlock(kindForTag(tag))
              activeTag = null
            }
          }
        }
        ev = parser.next()
      }
    }

    if (xmlAttempt.isFailure || result.isEmpty()) {
      result.clear()
      val imgTokenPrefix = "\u0001LISTAY_IMG:"
      val imgTagRegex = Regex("""<(?:img|image)\b([^>]*)/?>""", RegexOption.IGNORE_CASE)
      val srcAttrRegex = Regex("""(?:src|xlink:href|href)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
      val altAttrRegex = Regex("""(?:alt|title)\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)

      val withoutScripts = cleanHtml
        .replace(Regex("<(script|style|head)[^>]*>[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE), "")
      val withImgMarkers = imgTagRegex.replace(withoutScripts) { match ->
        val attrs = match.groupValues.getOrNull(1).orEmpty()
        val src = srcAttrRegex.find(attrs)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val alt = altAttrRegex.find(attrs)?.groupValues?.getOrNull(1)?.trim().orEmpty()
          .replace('\n', ' ')
          .replace('\u0001', ' ')
          .replace('\u0002', ' ')
        if (src.isNotEmpty()) {
          "\n$imgTokenPrefix$src\u0002$alt\u0001\n"
        } else {
          ""
        }
      }
      val stripped = withImgMarkers
        .replace(Regex("</?(p|div|h[1-6]|br|li|blockquote|figure|figcaption|svg)[^>]*>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
      stripped.split('\n').forEach { line ->
        val trimmed = line.replace(Regex("\\s+"), " ").trim()
        if (trimmed.startsWith(imgTokenPrefix)) {
          val payload = trimmed.removePrefix(imgTokenPrefix).substringBefore('\u0001')
          val src = payload.substringBefore('\u0002').trim()
          val alt = payload.substringAfter('\u0002', "").trim()
          val resolved = if (src.isNotEmpty()) resolveImage(src) else null
          if (resolved != null) {
            result.add(
              BookParagraph(
                index = result.size,
                text = "",
                kind = ParagraphKind.IMAGE,
                imagePath = resolved.file.absolutePath,
                imageWidth = resolved.width,
                imageHeight = resolved.height,
                imageAlt = alt.takeIf { it.isNotBlank() },
              )
            )
          }
        } else if (trimmed.isNotEmpty()) {
          result.add(BookParagraph(result.size, trimmed, ParagraphKind.BODY))
        }
      }
    }
    return result
  }

  private fun decodeCommonHtmlEntities(html: String): String {
    var s = html
      .replace("&nbsp;", " ")
      .replace("&ensp;", " ")
      .replace("&emsp;", " ")
      .replace("&thinsp;", " ")
      .replace("&mdash;", "—")
      .replace("&ndash;", "–")
      .replace("&laquo;", "«")
      .replace("&raquo;", "»")
      .replace("&hellip;", "…")
      .replace("&rsquo;", "’")
      .replace("&lsquo;", "‘")
      .replace("&rdquo;", "”")
      .replace("&ldquo;", "“")
      .replace("&apos;", "'")
      .replace("&copy;", "©")
      .replace("&reg;", "®")
      .replace("&trade;", "™")
      .replace("&deg;", "°")
      .replace("&sect;", "§")
      .replace("&bull;", "•")
      .replace("&middot;", "·")
      .replace("&prime;", "′")
      .replace("&Prime;", "″")
      .replace("&times;", "×")
      .replace("&divide;", "÷")
      .replace("&plusmn;", "±")
    // Decode numeric character references like &#160; or &#xA0; safely (skip XML reserved chars)
    s = Regex("""&#(\d+);""").replace(s) { m ->
      val code = m.groupValues[1].toIntOrNull()
      if (code != null && code !in setOf(34, 38, 39, 60, 62) && Character.isValidCodePoint(code)) {
        String(Character.toChars(code))
      } else {
        m.value
      }
    }
    s = Regex("""&#[xX]([0-9a-fA-F]+);""").replace(s) { m ->
      val code = m.groupValues[1].toIntOrNull(16)
      if (code != null && code !in setOf(34, 38, 39, 60, 62) && Character.isValidCodePoint(code)) {
        String(Character.toChars(code))
      } else {
        m.value
      }
    }
    return s
  }

  private fun newParser(stream: InputStream): XmlPullParser = Xml.newPullParser().apply {
    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    setInput(stream, "UTF-8")
  }

  private fun resolveRelative(baseDir: String, href: String): String {
    val cleanHref = href
      .substringBefore('#')
      .substringBefore('?')
      .replace('\\', '/')
      .trimStart('/')
    if (baseDir.isBlank()) {
      val parts = cleanHref.split('/')
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
    val parts = (baseDir.replace('\\', '/').split('/') + cleanHref.split('/')).toMutableList()
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

  private fun findZipEntry(zip: ZipFile, rawPath: String): ZipEntry? {
    val cleanPath = rawPath.substringBefore('#').substringBefore('?').replace('\\', '/').trimStart('/')
    zip.getEntry(cleanPath)?.let { return it }
    val decoded = runCatching { URLDecoder.decode(cleanPath, "UTF-8") }.getOrDefault(cleanPath)
    if (decoded != cleanPath) {
      zip.getEntry(decoded)?.let { return it }
    }
    return zip.entries().asSequence().firstOrNull { entry ->
      entry.name.equals(cleanPath, ignoreCase = true) ||
        entry.name.equals(decoded, ignoreCase = true)
    }
  }
}
