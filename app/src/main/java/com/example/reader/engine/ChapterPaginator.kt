package com.example.reader.engine

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ReaderFontOption
import com.example.data.local.ReaderPreferences
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class PaginationCacheKey(
  val bookId: Long,
  val chapterIndex: Int,
  val fontOption: ReaderFontOption,
  val fontSizeSp: Float,
  val lineHeightMultiplier: Float,
  val horizontalMarginDp: Int,
  val justifyText: Boolean,
  val hyphenationEnabled: Boolean,
  val viewportWidthPx: Int,
  val viewportHeightPx: Int,
  val density: Float,
  val fontScale: Float,
)

data class PageSlice(
  val paragraphIndex: Int,
  val kind: ParagraphKind,
  val text: String,
  val startCharInParagraph: Int,
  val endCharInParagraph: Int,
  val isFirstSliceOfParagraph: Boolean,
  val isLastSliceOfParagraph: Boolean,
  val imagePath: String? = null,
  val imageWidth: Int = 0,
  val imageHeight: Int = 0,
  val imageAlt: String? = null,
  val imageDisplayHeightDp: Float = 0f,
)

data class BookPage(
  val pageIndexInChapter: Int,
  val chapterIndex: Int,
  val slices: List<PageSlice>,
  val startLocator: Locator,
  val endParagraphIndex: Int,
  val endCharInParagraph: Int,
  val hasSyntheticChapterHeader: Boolean = false,
)

data class ParagraphLayoutSpec(
  val textStyle: TextStyle,
  val textAlign: TextAlign,
  val extraStartPaddingDp: Dp,
  val extraEndPaddingDp: Dp,
  val extraTopPaddingDp: Dp,
  val extraBottomPaddingDp: Dp,
)

object ReaderParagraphStyleResolver {
  fun maxLineColumnWidthDp(preferences: ReaderPreferences): Dp {
    val charFactor = if (preferences.fontOption == ReaderFontOption.JETBRAINS_MONO) 39f else 32f
    return (preferences.fontSizeSp * charFactor).dp.coerceIn(320.dp, 580.dp)
  }

  fun resolve(
    kind: ParagraphKind,
    text: String,
    preferences: ReaderPreferences,
    isContinuationSlice: Boolean = false,
  ): ParagraphLayoutSpec {
    val baseSize = preferences.fontSizeSp
    val bodyLineHeight = (baseSize * preferences.lineHeightMultiplier).sp
    val useHyphens = preferences.hyphenationEnabled
    val hasCyrillic = text.any { it in '\u0400'..'\u04FF' }
    val localeList = if (useHyphens && hasCyrillic) LocaleList("ru-RU") else null
    val hyphens = if (useHyphens) Hyphens.Auto else Hyphens.None
    val lineBreak = if (useHyphens) LineBreak.Paragraph else LineBreak.Simple

    return when (kind) {
      ParagraphKind.HEADING -> ParagraphLayoutSpec(
        textStyle = TextStyle(
          fontFamily = preferences.fontOption.fontFamily,
          fontSize = (baseSize + 9f).sp,
          lineHeight = ((baseSize + 9f) * 1.22f).sp,
          fontWeight = FontWeight.Bold,
        ),
        textAlign = TextAlign.Start,
        extraStartPaddingDp = 0.dp,
        extraEndPaddingDp = 0.dp,
        extraTopPaddingDp = 16.dp,
        extraBottomPaddingDp = 18.dp,
      )

      ParagraphKind.SUBHEADING -> ParagraphLayoutSpec(
        textStyle = TextStyle(
          fontFamily = preferences.fontOption.fontFamily,
          fontSize = (baseSize + 2f).sp,
          lineHeight = ((baseSize + 2f) * 1.35f).sp,
          fontWeight = FontWeight.Medium,
        ),
        textAlign = TextAlign.Start,
        extraStartPaddingDp = 0.dp,
        extraEndPaddingDp = 0.dp,
        extraTopPaddingDp = 12.dp,
        extraBottomPaddingDp = 8.dp,
      )

      ParagraphKind.QUOTE -> ParagraphLayoutSpec(
        textStyle = TextStyle(
          fontFamily = preferences.fontOption.fontFamily,
          fontSize = (baseSize - 1f).sp,
          lineHeight = bodyLineHeight,
          fontStyle = FontStyle.Italic,
          hyphens = hyphens,
          lineBreak = lineBreak,
          localeList = localeList,
        ),
        textAlign = TextAlign.Start,
        extraStartPaddingDp = 15.dp,
        extraEndPaddingDp = 0.dp,
        extraTopPaddingDp = 8.dp,
        extraBottomPaddingDp = 8.dp,
      )

      ParagraphKind.POEM -> ParagraphLayoutSpec(
        textStyle = TextStyle(
          fontFamily = preferences.fontOption.fontFamily,
          fontSize = baseSize.sp,
          lineHeight = bodyLineHeight,
          fontStyle = FontStyle.Italic,
        ),
        textAlign = TextAlign.Start,
        extraStartPaddingDp = 20.dp,
        extraEndPaddingDp = 0.dp,
        extraTopPaddingDp = 4.dp,
        extraBottomPaddingDp = 4.dp,
      )

      ParagraphKind.CODE -> ParagraphLayoutSpec(
        textStyle = TextStyle(
          fontFamily = JetBrainsMonoFontFamily,
          fontSize = (baseSize - 2f).coerceAtLeast(12f).sp,
          lineHeight = (baseSize * 1.35f).sp,
        ),
        textAlign = TextAlign.Start,
        extraStartPaddingDp = 10.dp,
        extraEndPaddingDp = 10.dp,
        extraTopPaddingDp = 10.dp,
        extraBottomPaddingDp = 10.dp,
      )

      ParagraphKind.BODY -> ParagraphLayoutSpec(
        textStyle = TextStyle(
          fontFamily = preferences.fontOption.fontFamily,
          fontSize = baseSize.sp,
          lineHeight = bodyLineHeight,
          textIndent = if (isContinuationSlice) {
            TextIndent.None
          } else {
            TextIndent(firstLine = (baseSize * 1.4f).sp)
          },
          hyphens = hyphens,
          lineBreak = lineBreak,
          localeList = localeList,
        ),
        textAlign = if (preferences.justifyText) TextAlign.Justify else TextAlign.Start,
        extraStartPaddingDp = 0.dp,
        extraEndPaddingDp = 0.dp,
        extraTopPaddingDp = 0.dp,
        extraBottomPaddingDp = 0.dp,
      )

      ParagraphKind.IMAGE -> ParagraphLayoutSpec(
        textStyle = TextStyle(
          fontFamily = preferences.fontOption.fontFamily,
          fontSize = (baseSize - 2f).coerceAtLeast(11f).sp,
          lineHeight = (baseSize * 1.25f).sp,
        ),
        textAlign = TextAlign.Center,
        extraStartPaddingDp = 0.dp,
        extraEndPaddingDp = 0.dp,
        extraTopPaddingDp = 12.dp,
        extraBottomPaddingDp = 12.dp,
      )
    }
  }
}

interface TextLineMeasurer {
  fun measure(text: String, style: TextStyle, textAlign: TextAlign, maxWidthPx: Int): MeasuredParagraphLayout
}

data class MeasuredLine(
  val startChar: Int,
  val endCharVisible: Int,
  val topPx: Float,
  val bottomPx: Float,
)

data class MeasuredParagraphLayout(
  val lines: List<MeasuredLine>,
  val totalHeightPx: Float,
)

class ComposeTextLineMeasurer(
  private val fontFamilyResolver: FontFamily.Resolver,
  private val density: Density,
  private val layoutDirection: LayoutDirection,
) : TextLineMeasurer {
  private val measurer = TextMeasurer(
    defaultFontFamilyResolver = fontFamilyResolver,
    defaultDensity = density,
    defaultLayoutDirection = layoutDirection,
    cacheSize = 0,
  )

  override fun measure(
    text: String,
    style: TextStyle,
    textAlign: TextAlign,
    maxWidthPx: Int,
  ): MeasuredParagraphLayout {
    val result = measurer.measure(
      text = AnnotatedString(text),
      style = style.copy(textAlign = textAlign),
      constraints = Constraints(maxWidth = maxWidthPx.coerceAtLeast(1)),
      skipCache = true,
    )
    val lines = (0 until result.lineCount).map { lineIdx ->
      MeasuredLine(
        startChar = result.getLineStart(lineIdx),
        endCharVisible = result.getLineEnd(lineIdx, visibleEnd = true),
        topPx = result.getLineTop(lineIdx),
        bottomPx = result.getLineBottom(lineIdx),
      )
    }
    return MeasuredParagraphLayout(
      lines = lines,
      totalHeightPx = result.size.height.toFloat(),
    )
  }
}

class ChapterPaginator(
  private val maxCachedChapters: Int = 64,
) {
  private val cache = object : LinkedHashMap<PaginationCacheKey, List<BookPage>>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PaginationCacheKey, List<BookPage>>?): Boolean {
      return size > maxCachedChapters
    }
  }

  fun getCached(key: PaginationCacheKey): List<BookPage>? = synchronized(cache) {
    cache[key]
  }

  suspend fun getOrPaginate(
    key: PaginationCacheKey,
    chapter: BookChapter,
    preferences: ReaderPreferences,
    density: Density,
    measurer: TextLineMeasurer,
  ): List<BookPage> {
    getCached(key)?.let { return it }
    val computed = withContext(Dispatchers.Default) {
      paginateChapter(
        chapter = chapter,
        preferences = preferences,
        viewportWidthPx = key.viewportWidthPx,
        viewportHeightPx = key.viewportHeightPx,
        density = density,
        measurer = measurer,
      )
    }
    synchronized(cache) {
      cache[key] = computed
    }
    return computed
  }

  fun paginateChapter(
    chapter: BookChapter,
    preferences: ReaderPreferences,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    density: Density,
    measurer: TextLineMeasurer,
  ): List<BookPage> {
    val needsSyntheticHeader = chapter.title.isNotBlank() &&
      chapter.paragraphs.firstOrNull()?.kind != ParagraphKind.HEADING

    if (chapter.paragraphs.isEmpty()) {
      return listOf(
        BookPage(
          pageIndexInChapter = 0,
          chapterIndex = chapter.index,
          slices = emptyList(),
          startLocator = Locator(chapter.index, 0, 0),
          endParagraphIndex = 0,
          endCharInParagraph = 0,
          hasSyntheticChapterHeader = needsSyntheticHeader,
        )
      )
    }

    val safeHeightPx = viewportHeightPx.coerceAtLeast(120).toFloat()
    val paragraphGapPx = 0f
    val pages = mutableListOf<BookPage>()
    val currentSlices = mutableListOf<PageSlice>()
    val syntheticHeaderHeightPx = if (needsSyntheticHeader) {
      val headerSpec = ReaderParagraphStyleResolver.resolve(ParagraphKind.HEADING, chapter.title, preferences)
      val measuredHeader = measurer.measure(
        text = chapter.title,
        style = headerSpec.textStyle,
        textAlign = headerSpec.textAlign,
        maxWidthPx = viewportWidthPx.coerceAtLeast(48),
      )
      val topPx = with(density) { headerSpec.extraTopPaddingDp.toPx() }
      val bottomPx = with(density) { headerSpec.extraBottomPaddingDp.toPx() }
      val ornamentPx = with(density) { 10.dp.toPx() }
      (measuredHeader.totalHeightPx + topPx + bottomPx + ornamentPx)
        .coerceAtMost(safeHeightPx * 0.55f)
    } else {
      0f
    }
    var usedHeightPx = syntheticHeaderHeightPx

    fun flushPage() {
      if (currentSlices.isEmpty()) return
      val first = currentSlices.first()
      val last = currentSlices.last()
      val isFirstPageOfChapter = pages.isEmpty()
      pages.add(
        BookPage(
          pageIndexInChapter = pages.size,
          chapterIndex = chapter.index,
          slices = currentSlices.toList(),
          startLocator = Locator(
            chapterIndex = chapter.index,
            paragraphIndex = first.paragraphIndex,
            charOffset = first.startCharInParagraph,
          ),
          endParagraphIndex = last.paragraphIndex,
          endCharInParagraph = last.endCharInParagraph,
          hasSyntheticChapterHeader = isFirstPageOfChapter && needsSyntheticHeader,
        )
      )
      currentSlices.clear()
      usedHeightPx = 0f
    }

    for (paragraph in chapter.paragraphs) {
      val spec = ReaderParagraphStyleResolver.resolve(paragraph.kind, paragraph.text, preferences)
      val continuationSpec = if (paragraph.kind == ParagraphKind.BODY) {
        ReaderParagraphStyleResolver.resolve(
          kind = paragraph.kind,
          text = paragraph.text,
          preferences = preferences,
          isContinuationSlice = true,
        )
      } else {
        spec
      }
      val horizExtraPx = with(density) { (spec.extraStartPaddingDp + spec.extraEndPaddingDp).toPx() }
      val topExtraPx = with(density) { spec.extraTopPaddingDp.toPx() }
      val bottomExtraPx = with(density) { spec.extraBottomPaddingDp.toPx() }
      val effectiveWidthPx = (viewportWidthPx - horizExtraPx.roundToInt()).coerceAtLeast(48)

      if (paragraph.kind == ParagraphKind.IMAGE) {
        val gap = if (currentSlices.isEmpty()) 0f else paragraphGapPx
        val imgW = paragraph.imageWidth
        val imgH = paragraph.imageHeight
        val maxImageHeightPx = ((safeHeightPx - topExtraPx - bottomExtraPx) * 0.78f).coerceAtLeast(80f)
        val targetWidthPx = if (imgW in 1..140 && imgH in 1..140) {
          (imgW * density.density).coerceAtMost(effectiveWidthPx.toFloat())
        } else {
          effectiveWidthPx.toFloat()
        }
        val aspect = if (imgW > 0 && imgH > 0) imgH.toFloat() / imgW.toFloat() else 0.65f
        val desiredImageHeightPx = (targetWidthPx * aspect).coerceIn(32f, maxImageHeightPx)
        val neededTotalPx = gap + topExtraPx + desiredImageHeightPx + bottomExtraPx
        if (currentSlices.isNotEmpty() && usedHeightPx + neededTotalPx > safeHeightPx) {
          flushPage()
        }
        val sliceGap = if (currentSlices.isEmpty()) 0f else paragraphGapPx
        val remainingForImage = (safeHeightPx - usedHeightPx - sliceGap - topExtraPx - bottomExtraPx)
          .coerceAtLeast(64f)
        val finalImageHeightPx = desiredImageHeightPx.coerceAtMost(remainingForImage)
        val displayHeightDp = (finalImageHeightPx / density.density).coerceAtLeast(32f)

        currentSlices.add(
          PageSlice(
            paragraphIndex = paragraph.index,
            kind = paragraph.kind,
            text = "",
            startCharInParagraph = 0,
            endCharInParagraph = 0,
            isFirstSliceOfParagraph = true,
            isLastSliceOfParagraph = true,
            imagePath = paragraph.imagePath,
            imageWidth = paragraph.imageWidth,
            imageHeight = paragraph.imageHeight,
            imageAlt = paragraph.imageAlt,
            imageDisplayHeightDp = displayHeightDp,
          )
        )
        usedHeightPx += sliceGap + topExtraPx + finalImageHeightPx + bottomExtraPx
        continue
      }

      val measured = measurer.measure(
        text = paragraph.text,
        style = spec.textStyle,
        textAlign = spec.textAlign,
        maxWidthPx = effectiveWidthPx,
      )
      val lines = measured.lines
      if (lines.isEmpty()) {
        val gap = if (currentSlices.isEmpty()) 0f else paragraphGapPx
        val needed = gap + topExtraPx + bottomExtraPx + measured.totalHeightPx
        if (currentSlices.isNotEmpty() && usedHeightPx + needed > safeHeightPx) {
          flushPage()
        }
        val sliceGap = if (currentSlices.isEmpty()) 0f else paragraphGapPx
        currentSlices.add(
          PageSlice(
            paragraphIndex = paragraph.index,
            kind = paragraph.kind,
            text = paragraph.text,
            startCharInParagraph = 0,
            endCharInParagraph = paragraph.text.length,
            isFirstSliceOfParagraph = true,
            isLastSliceOfParagraph = true,
          )
        )
        usedHeightPx += sliceGap + topExtraPx + bottomExtraPx + measured.totalHeightPx
        continue
      }

      var fromLine = 0
      while (fromLine < lines.size) {
        val gap = if (currentSlices.isEmpty()) 0f else paragraphGapPx
        val isFirstSlice = fromLine == 0
        val sliceSpec = if (isFirstSlice) spec else continuationSpec
        val sliceTopExtra = if (isFirstSlice) topExtraPx else 0f
        val remainingPx = safeHeightPx - usedHeightPx - gap - sliceTopExtra

        if (currentSlices.isNotEmpty()) {
          val firstLineHeight = (lines[fromLine].bottomPx - lines[fromLine].topPx) +
            if (fromLine + 1 == lines.size) bottomExtraPx else 0f
          if (firstLineHeight > remainingPx) {
            flushPage()
            continue
          }
        }

        val availableForLines = (safeHeightPx - usedHeightPx - (if (currentSlices.isEmpty()) 0f else paragraphGapPx) - sliceTopExtra)
          .coerceAtLeast(1f)
        val baseTop = lines[fromLine].topPx
        var toLine = fromLine + 1
        while (toLine <= lines.size) {
          val lastLineBottom = lines[toLine - 1].bottomPx - baseTop
          val sliceBottomExtra = if (toLine == lines.size) bottomExtraPx else 0f
          if (lastLineBottom + sliceBottomExtra <= availableForLines) {
            toLine++
          } else {
            break
          }
        }
        toLine = (toLine - 1).coerceAtLeast(fromLine + 1)

        // Avoid breaking mid-word across pages when hyphenated, unless it's the only line on an empty page.
        if (toLine < lines.size && toLine > fromLine + 1) {
          val splitChar = lines[toLine].startChar
          if (splitChar > 0 &&
            splitChar < paragraph.text.length &&
            !paragraph.text[splitChar - 1].isWhitespace() &&
            !paragraph.text[splitChar].isWhitespace()
          ) {
            toLine -= 1
          }
        } else if (toLine < lines.size && toLine == fromLine + 1 && currentSlices.isNotEmpty()) {
          val splitChar = lines[toLine].startChar
          if (splitChar > 0 &&
            splitChar < paragraph.text.length &&
            !paragraph.text[splitChar - 1].isWhitespace() &&
            !paragraph.text[splitChar].isWhitespace()
          ) {
            flushPage()
            continue
          }
        }

        // Verify re-measured slice text fits inside available height (guards LineBreak.Paragraph differences on splits).
        var sliceStartChar = lines[fromLine].startChar.coerceIn(0, paragraph.text.length)
        var sliceEndChar: Int
        var sliceText: String
        var isLastSlice: Boolean

        while (true) {
          isLastSlice = toLine >= lines.size
          val rawEnd = if (isLastSlice) {
            paragraph.text.length
          } else {
            lines[toLine - 1].endCharVisible.coerceIn(sliceStartChar, paragraph.text.length)
          }
          sliceEndChar = rawEnd
          sliceText = paragraph.text.substring(sliceStartChar, sliceEndChar).trimEnd('\n', '\r', ' ')
          if (isFirstSlice && isLastSlice) break

          val sliceBottomExtra = if (isLastSlice) bottomExtraPx else 0f
          val reMeasured = measurer.measure(
            text = sliceText,
            style = sliceSpec.textStyle,
            textAlign = sliceSpec.textAlign,
            maxWidthPx = effectiveWidthPx,
          )
          if (reMeasured.totalHeightPx + sliceBottomExtra <= availableForLines || toLine <= fromLine + 1) {
            break
          }
          toLine -= 1
        }

        val actualSliceHeight = if (isFirstSlice && isLastSlice) {
          measured.totalHeightPx + sliceTopExtra + bottomExtraPx
        } else {
          val sliceBottomExtra = if (isLastSlice) bottomExtraPx else 0f
          val reMeasured = measurer.measure(
            text = sliceText,
            style = sliceSpec.textStyle,
            textAlign = sliceSpec.textAlign,
            maxWidthPx = effectiveWidthPx,
          )
          reMeasured.totalHeightPx + sliceTopExtra + sliceBottomExtra
        }

        if (currentSlices.isNotEmpty() && usedHeightPx + gap + actualSliceHeight > safeHeightPx) {
          flushPage()
          continue
        }

        currentSlices.add(
          PageSlice(
            paragraphIndex = paragraph.index,
            kind = paragraph.kind,
            text = sliceText,
            startCharInParagraph = sliceStartChar,
            endCharInParagraph = sliceEndChar,
            isFirstSliceOfParagraph = isFirstSlice,
            isLastSliceOfParagraph = isLastSlice,
          )
        )
        usedHeightPx += (if (currentSlices.size == 1) 0f else paragraphGapPx) + actualSliceHeight

        fromLine = toLine
        if (fromLine < lines.size) {
          flushPage()
        }
      }
    }

    flushPage()
    return pages.ifEmpty {
      listOf(
        BookPage(
          pageIndexInChapter = 0,
          chapterIndex = chapter.index,
          slices = emptyList(),
          startLocator = Locator(chapter.index, 0, 0),
          endParagraphIndex = 0,
          endCharInParagraph = 0,
          hasSyntheticChapterHeader = needsSyntheticHeader,
        )
      )
    }
  }

  companion object {
    fun findPageForLocator(pages: List<BookPage>, locator: Locator): Int {
      if (pages.isEmpty()) return 0
      var bestIdx = 0
      for (i in pages.indices) {
        val start = pages[i].startLocator
        val cmpPara = start.paragraphIndex.compareTo(locator.paragraphIndex)
        val isBeforeOrAt = cmpPara < 0 || (cmpPara == 0 && start.charOffset <= locator.charOffset)
        if (isBeforeOrAt) {
          bestIdx = i
        } else {
          break
        }
      }
      return bestIdx
    }
  }
}
