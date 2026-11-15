package com.example

import com.example.reader.engine.BookCacheStorage
import com.example.reader.engine.BookChapter
import com.example.reader.engine.BookParagraph
import com.example.reader.engine.ChapterMetricsBuilder
import com.example.reader.engine.LoadedBookContent
import com.example.reader.engine.Locator
import com.example.reader.engine.ParagraphKind
import com.example.reader.engine.TocEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReaderCacheAndProgressTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun `progressFor uses accumulated characters rather than chapter index`() {
    val ch1Text = "a".repeat(100)
    val ch2Text = "b".repeat(900)
    val content = ChapterMetricsBuilder.buildReflowable(
      rawChapters = listOf(
        BookChapter(
          index = 0,
          title = "Короткая глава",
          paragraphs = listOf(BookParagraph(0, ch1Text, ParagraphKind.BODY)),
        ),
        BookChapter(
          index = 1,
          title = "Длинная глава",
          paragraphs = listOf(BookParagraph(0, ch2Text, ParagraphKind.BODY)),
        ),
      ),
      rawToc = listOf(TocEntry("Короткая глава", 0), TocEntry("Длинная глава", 1)),
    )

    assertEquals(1000L, content.totalChars)
    assertEquals(0L, content.chapters[0].startCharOffset)
    assertEquals(100L, content.chapters[1].startCharOffset)

    // Start of book -> 0%
    assertEquals(0f, content.progressFor(Locator(0, 0, 0)), 0.0001f)
    // Start of chapter 2 (after 100 of 1000 chars) -> 10%, NOT 50%
    assertEquals(0.10f, content.progressFor(Locator(1, 0, 0)), 0.0001f)
    // Midpoint of chapter 2 (100 + 450 = 550 of 1000 chars) -> 55%
    assertEquals(0.55f, content.progressFor(Locator(1, 0, 450)), 0.0001f)
    // End of chapter 2 -> 100%
    assertEquals(1.0f, content.progressFor(Locator(1, 0, 900)), 0.0001f)
  }

  @Test
  fun `BookCacheStorage round-trips Reflowable and PdfDocumentContent accurately`() {
    val originalReflowable = ChapterMetricsBuilder.buildReflowable(
      rawChapters = listOf(
        BookChapter(
          index = 0,
          title = "Глава 1",
          paragraphs = listOf(
            BookParagraph(0, "Глава 1", ParagraphKind.HEADING),
            BookParagraph(1, "Текст первого абзаца на русском языке.", ParagraphKind.BODY),
          ),
        ),
        BookChapter(
          index = 1,
          title = "Глава 2",
          paragraphs = listOf(
            BookParagraph(0, "Цитата", ParagraphKind.QUOTE),
          ),
        ),
      ),
      rawToc = listOf(TocEntry("Глава 1", 0), TocEntry("Глава 2", 1)),
    )

    val reflowableFile = tempFolder.newFile("book.lcache")
    BookCacheStorage.write(reflowableFile, originalReflowable)
    val loadedReflowable = BookCacheStorage.read(reflowableFile) as? LoadedBookContent.Reflowable
    assertNotNull(loadedReflowable)
    assertEquals(originalReflowable, loadedReflowable)

    val originalPdf = LoadedBookContent.PdfDocumentContent(
      filePath = "/data/local/tmp/sample.pdf",
      pageCount = 42,
      toc = listOf(TocEntry("Страница 1", 0), TocEntry("Страница 6", 5)),
    )
    val pdfFile = tempFolder.newFile("pdf.lcache")
    BookCacheStorage.write(pdfFile, originalPdf)
    val loadedPdf = BookCacheStorage.read(pdfFile) as? LoadedBookContent.PdfDocumentContent
    assertNotNull(loadedPdf)
    assertEquals(originalPdf, loadedPdf)
  }

  @Test
  fun `progressFor handles empty chapters clamping and PDF pages safely`() {
    val content = ChapterMetricsBuilder.buildReflowable(
      rawChapters = listOf(
        BookChapter(index = 0, title = "Пустая глава", paragraphs = emptyList()),
        BookChapter(
          index = 1,
          title = "Основная глава",
          paragraphs = listOf(
            BookParagraph(0, "x".repeat(200), ParagraphKind.BODY),
            BookParagraph(1, "y".repeat(300), ParagraphKind.BODY),
          ),
        ),
      ),
      rawToc = listOf(TocEntry("Основная глава", 1)),
    )

    assertEquals(500L, content.totalChars)
    assertEquals(0f, content.progressFor(Locator(0, 0, 0)), 0.0001f)
    assertEquals(0.4f, content.progressFor(Locator(1, 1, 0)), 0.0001f)
    assertEquals(0.7f, content.progressFor(Locator(1, 1, 150)), 0.0001f)
    assertEquals(1.0f, content.progressFor(Locator(99, 99, 9999)), 0.0001f)

    val pdf = LoadedBookContent.PdfDocumentContent("/tmp/test.pdf", pageCount = 10, toc = emptyList())
    assertEquals(0.1f, pdf.progressFor(Locator(0, 0, 0)), 0.0001f)
    assertEquals(0.5f, pdf.progressFor(Locator(4, 0, 0)), 0.0001f)
    assertEquals(1.0f, pdf.progressFor(Locator(9, 0, 0)), 0.0001f)
  }
}
