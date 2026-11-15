package com.example

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.example.data.local.ReaderFontOption
import com.example.data.local.ReaderPreferences
import com.example.data.local.ReaderReadingMode
import com.example.reader.engine.BookChapter
import com.example.reader.engine.BookParagraph
import com.example.reader.engine.ChapterPaginator
import com.example.reader.engine.Locator
import com.example.reader.engine.MeasuredLine
import com.example.reader.engine.MeasuredParagraphLayout
import com.example.reader.engine.PaginationCacheKey
import com.example.reader.engine.ParagraphKind
import com.example.reader.engine.ReaderParagraphStyleResolver
import com.example.reader.engine.TextLineMeasurer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterPaginatorTest {

  private class FakeMeasuredTextLineMeasurer : TextLineMeasurer {
    var measureCallCount: Int = 0

    override fun measure(
      text: String,
      style: TextStyle,
      textAlign: TextAlign,
      maxWidthPx: Int,
    ): MeasuredParagraphLayout {
      measureCallCount++
      val fontPx = style.fontSize.value
      val lineHeightPx = (style.lineHeight.value.takeIf { it > 0f } ?: fontPx * 1.5f)
      val approxCharWidth = (fontPx * 0.52f).coerceAtLeast(6f)
      val charsPerLine = (maxWidthPx / approxCharWidth).toInt().coerceAtLeast(8)

      val lines = mutableListOf<MeasuredLine>()
      var start = 0
      var lineIdx = 0
      while (start < text.length) {
        var end = (start + charsPerLine).coerceAtMost(text.length)
        if (end < text.length) {
          val lastSpace = text.lastIndexOf(' ', end - 1)
          if (lastSpace > start) {
            end = lastSpace
          }
        }
        val visibleEnd = end
        lines.add(
          MeasuredLine(
            startChar = start,
            endCharVisible = visibleEnd,
            topPx = lineIdx * lineHeightPx,
            bottomPx = (lineIdx + 1) * lineHeightPx,
          )
        )
        start = if (end < text.length && text[end] == ' ') end + 1 else end
        if (start == visibleEnd && start < text.length) {
          start++
        }
        lineIdx++
      }
      return MeasuredParagraphLayout(
        lines = lines,
        totalHeightPx = lineIdx * lineHeightPx,
      )
    }
  }

  private fun sampleChapter(): BookChapter {
    val sentence = "Это длинный русский текст для проверки постраничной разбивки с учётом реальных строк и переносов. "
    val paragraphs = (0 until 10).map { idx ->
      BookParagraph(
        index = idx,
        text = "Абзац $idx. " + sentence.repeat(4).trim(),
        kind = if (idx == 0) ParagraphKind.HEADING else ParagraphKind.BODY,
      )
    }
    return BookChapter(index = 0, title = "Глава 1", paragraphs = paragraphs)
  }

  @Test
  fun `paginator caches by chapter font spacing margin and screen size`() = runTest {
    val paginator = ChapterPaginator(maxCachedChapters = 8)
    val measurer = FakeMeasuredTextLineMeasurer()
    val density = Density(density = 1f, fontScale = 1f)
    val chapter = sampleChapter()
    val prefs = ReaderPreferences(
      readingMode = ReaderReadingMode.PAGES,
      fontOption = ReaderFontOption.LITERATA,
      fontSizeSp = 18f,
      lineHeightMultiplier = 1.5f,
      horizontalMarginDp = 20,
    )

    val key1 = PaginationCacheKey(
      bookId = 1L,
      chapterIndex = 0,
      fontOption = prefs.fontOption,
      fontSizeSp = prefs.fontSizeSp,
      lineHeightMultiplier = prefs.lineHeightMultiplier,
      horizontalMarginDp = prefs.horizontalMarginDp,
      justifyText = prefs.justifyText,
      hyphenationEnabled = prefs.hyphenationEnabled,
      viewportWidthPx = 360,
      viewportHeightPx = 560,
      density = 1f,
      fontScale = 1f,
    )

    val pages1 = paginator.getOrPaginate(key1, chapter, prefs, density, measurer)
    assertTrue(pages1.size > 1)
    val callsAfterFirst = measurer.measureCallCount
    assertTrue(callsAfterFirst > 0)

    // Repeat with same key -> served from cache without measuring again
    assertNotNull(paginator.getCached(key1))
    val pages1Repeat = paginator.getOrPaginate(key1, chapter, prefs, density, measurer)
    assertEquals(pages1, pages1Repeat)
    assertEquals(callsAfterFirst, measurer.measureCallCount)

    // Changing font size, spacing, margin, or screen size invalidates key
    val largerFontKey = key1.copy(fontSizeSp = 24f)
    assertNull(paginator.getCached(largerFontKey))
    val pagesLargerFont = paginator.getOrPaginate(
      largerFontKey,
      chapter,
      prefs.copy(fontSizeSp = 24f),
      density,
      measurer,
    )
    assertTrue(pagesLargerFont.size > pages1.size)
  }

  @Test
  fun `changing font size preserves exact reading position via Locator`() = runTest {
    val paginator = ChapterPaginator()
    val measurer = FakeMeasuredTextLineMeasurer()
    val density = Density(density = 1f, fontScale = 1f)
    val chapter = sampleChapter()

    val smallPrefs = ReaderPreferences(fontSizeSp = 16f, lineHeightMultiplier = 1.4f)
    val smallKey = PaginationCacheKey(
      bookId = 1L,
      chapterIndex = 0,
      fontOption = smallPrefs.fontOption,
      fontSizeSp = smallPrefs.fontSizeSp,
      lineHeightMultiplier = smallPrefs.lineHeightMultiplier,
      horizontalMarginDp = smallPrefs.horizontalMarginDp,
      justifyText = smallPrefs.justifyText,
      hyphenationEnabled = smallPrefs.hyphenationEnabled,
      viewportWidthPx = 360,
      viewportHeightPx = 500,
      density = 1f,
      fontScale = 1f,
    )
    val smallPages = paginator.getOrPaginate(smallKey, chapter, smallPrefs, density, measurer)
    val midSmallPage = smallPages[smallPages.size / 2]
    val savedLocator: Locator = midSmallPage.startLocator

    // User switches to much larger font -> re-paginate and map savedLocator back to page index
    val largePrefs = smallPrefs.copy(fontSizeSp = 26f, lineHeightMultiplier = 1.8f)
    val largeKey = smallKey.copy(fontSizeSp = 26f, lineHeightMultiplier = 1.8f)
    val largePages = paginator.getOrPaginate(largeKey, chapter, largePrefs, density, measurer)
    val mappedPageIndex = ChapterPaginator.findPageForLocator(largePages, savedLocator)

    val mappedPage = largePages[mappedPageIndex]
    val mappedLocator = mappedPage.startLocator
    assertTrue(
      "Mapped page start locator should not jump ahead of saved locator",
      mappedLocator.paragraphIndex < savedLocator.paragraphIndex ||
        (mappedLocator.paragraphIndex == savedLocator.paragraphIndex &&
          mappedLocator.charOffset <= savedLocator.charOffset),
    )
    assertTrue(
      "Mapped page end should reach or pass saved locator",
      mappedPage.endParagraphIndex > savedLocator.paragraphIndex ||
        (mappedPage.endParagraphIndex == savedLocator.paragraphIndex &&
          mappedPage.endCharInParagraph >= savedLocator.charOffset),
    )
  }

  @Test
  fun `page to Locator and Locator back to page round-trips accurately`() = runTest {
    val paginator = ChapterPaginator()
    val measurer = FakeMeasuredTextLineMeasurer()
    val density = Density(density = 1f, fontScale = 1f)
    val chapter = sampleChapter()
    val prefs = ReaderPreferences(
      readingMode = ReaderReadingMode.PAGES,
      fontSizeSp = 18f,
      lineHeightMultiplier = 1.5f,
      horizontalMarginDp = 20,
    )
    val key = PaginationCacheKey(
      bookId = 7L,
      chapterIndex = 0,
      fontOption = prefs.fontOption,
      fontSizeSp = prefs.fontSizeSp,
      lineHeightMultiplier = prefs.lineHeightMultiplier,
      horizontalMarginDp = prefs.horizontalMarginDp,
      justifyText = prefs.justifyText,
      hyphenationEnabled = prefs.hyphenationEnabled,
      viewportWidthPx = 360,
      viewportHeightPx = 520,
      density = 1f,
      fontScale = 1f,
    )

    val pages = paginator.getOrPaginate(key, chapter, prefs, density, measurer)
    assertTrue("Expected multi-page chapter", pages.size >= 3)

    // Every page's startLocator must resolve back to that exact pageIndexInChapter
    for (page in pages) {
      val resolvedIndex = ChapterPaginator.findPageForLocator(pages, page.startLocator)
      assertEquals(
        "startLocator for page ${page.pageIndexInChapter} should map back to the same page",
        page.pageIndexInChapter,
        resolvedIndex,
      )
    }

    // Any mid-page Locator (e.g. a search match or bookmark) must resolve to the page containing that slice
    for (page in pages) {
      val slice = page.slices.lastOrNull() ?: continue
      if (slice.endCharInParagraph - slice.startCharInParagraph > 4) {
        val midOffset = (slice.startCharInParagraph + slice.endCharInParagraph) / 2
        val midLocator = Locator(
          chapterIndex = chapter.index,
          paragraphIndex = slice.paragraphIndex,
          charOffset = midOffset,
        )
        val resolvedPage = ChapterPaginator.findPageForLocator(pages, midLocator)
        assertEquals(page.pageIndexInChapter, resolvedPage)
      }
    }
  }

  @Test
  fun `body paragraphs use first-line indent without vertical gap and omit indent on continuation slices`() {
    val prefs = ReaderPreferences(fontSizeSp = 18f)
    val firstSliceSpec = ReaderParagraphStyleResolver.resolve(
      kind = ParagraphKind.BODY,
      text = "Первый абзац текста.",
      preferences = prefs,
      isContinuationSlice = false,
    )
    assertTrue((firstSliceSpec.textStyle.textIndent?.firstLine ?: TextUnit.Unspecified).value > 20f)
    assertEquals(0.dp, firstSliceSpec.extraTopPaddingDp)
    assertEquals(0.dp, firstSliceSpec.extraBottomPaddingDp)

    val continuationSpec = ReaderParagraphStyleResolver.resolve(
      kind = ParagraphKind.BODY,
      text = "Продолжение абзаца.",
      preferences = prefs,
      isContinuationSlice = true,
    )
    assertEquals(TextIndent.None, continuationSpec.textStyle.textIndent)

    val headingSpec = ReaderParagraphStyleResolver.resolve(
      kind = ParagraphKind.HEADING,
      text = "Глава 1",
      preferences = prefs,
    )
    assertTrue(headingSpec.textStyle.fontSize.value >= prefs.fontSizeSp + 8f)
  }

  @Test
  fun `maxLineColumnWidthDp caps line width up to 65 characters across font sizes`() {
    for (fontSize in listOf(14f, 18f, 24f, 28f)) {
      val prefs = ReaderPreferences(fontOption = ReaderFontOption.LITERATA, fontSizeSp = fontSize)
      val colWidthDp = ReaderParagraphStyleResolver.maxLineColumnWidthDp(prefs).value
      val approxAvgCharDp = fontSize * 0.5f
      val approxCharsPerLine = colWidthDp / approxAvgCharDp
      assertTrue(
        "Expected <= 65 chars per line at $fontSize sp, got $approxCharsPerLine",
        approxCharsPerLine <= 65.5f,
      )
    }
  }

  @Test
  fun `first page gets synthetic chapter header only when chapter has no leading HEADING`() = runTest {
    val paginator = ChapterPaginator()
    val measurer = FakeMeasuredTextLineMeasurer()
    val density = Density(density = 1f, fontScale = 1f)
    val prefs = ReaderPreferences(readingMode = ReaderReadingMode.PAGES)

    val withoutLeadingHeading = BookChapter(
      index = 0,
      title = "Пролог",
      paragraphs = (0 until 8).map { idx ->
        BookParagraph(idx, "Длинный абзац номер $idx. " + "Текст для проверки разбиения. ".repeat(6), ParagraphKind.BODY)
      },
    )
    val pagesSynthetic = paginator.paginateChapter(withoutLeadingHeading, prefs, 360, 420, density, measurer)
    assertTrue(pagesSynthetic.size > 1)
    assertTrue(pagesSynthetic.first().hasSyntheticChapterHeader)
    assertFalse(pagesSynthetic[1].hasSyntheticChapterHeader)

    val withLeadingHeading = sampleChapter()
    val pagesExplicit = paginator.paginateChapter(withLeadingHeading, prefs, 360, 420, density, measurer)
    assertFalse(pagesExplicit.first().hasSyntheticChapterHeader)
  }
}
