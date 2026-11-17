package com.example

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.BookEntity
import com.example.data.local.PreferencesRepository
import com.example.data.local.ReaderPreferences
import com.example.data.local.ReaderReadingMode
import com.example.data.repository.BookRepository
import com.example.reader.engine.BookCacheStorage
import com.example.reader.engine.BookChapter
import com.example.reader.engine.BookParagraph
import com.example.reader.engine.ChapterMetricsBuilder
import com.example.reader.engine.ChapterPaginator
import com.example.reader.engine.EpubParser
import com.example.reader.engine.Fb2Parser
import com.example.reader.engine.LoadedBookContent
import com.example.reader.engine.Locator
import com.example.reader.engine.MeasuredLine
import com.example.reader.engine.MeasuredParagraphLayout
import com.example.reader.engine.ParagraphKind
import com.example.reader.engine.TextLineMeasurer
import com.example.reader.engine.TocEntry
import com.example.data.local.ReaderThemeMode
import com.example.ui.components.BookInlineImage
import com.example.ui.screens.PageNavigationHandle
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.ReflowablePagesReader
import com.example.ui.theme.ListayTheme
import com.example.ui.theme.rememberAnimatedReaderColors
import com.example.ui.viewmodel.ReaderViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BookImagesTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val tempFolder = TemporaryFolder()

  private fun createPngBytes(width: Int = 80, height: Int = 60): ByteArray {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bmp.eraseColor(android.graphics.Color.rgb(160, 90, 50))
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    bmp.recycle()
    return out.toByteArray()
  }

  @Test
  fun `EpubParser extracts inline img, svg image, and unclosed html img into imageOutputDir`() {
    val imgBytes1 = createPngBytes(120, 90)
    val imgBytes2 = createPngBytes(90, 90)
    val epubFile = tempFolder.newFile("illustrated.epub")
    ZipOutputStream(epubFile.outputStream()).use { zos ->
      zos.putNextEntry(ZipEntry("META-INF/container.xml"))
      zos.write(
        """<?xml version="1.0"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>""".toByteArray()
      )
      zos.closeEntry()

      zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
      zos.write(
        """<?xml version="1.0" encoding="utf-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>Иллюстрированный EPUB</dc:title>
            <dc:creator>Художник</dc:creator>
            <meta name="cover" content="cover-page"/>
          </metadata>
          <manifest>
            <item id="cover-page" href="Text/cover.xhtml" media-type="application/xhtml+xml"/>
            <item id="ch1" href="Text/ch1.xhtml" media-type="application/xhtml+xml"/>
            <item id="ch2" href="Text/ch2.xhtml" media-type="application/xhtml+xml"/>
            <item id="img1" href="Images/pic 1.png" media-type="image/png"/>
            <item id="img2" href="Images/pic2.png" media-type="image/png"/>
          </manifest>
          <spine>
            <itemref idref="ch1"/>
            <itemref idref="ch2"/>
          </spine>
        </package>""".toByteArray()
      )
      zos.closeEntry()

      zos.putNextEntry(ZipEntry("OEBPS/Text/cover.xhtml"))
      zos.write(
        """<?xml version="1.0" encoding="utf-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml"><body><div><img src="../Images/pic%201.png" alt="Обложка"/></div></body></html>"""
          .toByteArray()
      )
      zos.closeEntry()

      zos.putNextEntry(ZipEntry("OEBPS/Text/ch1.xhtml"))
      zos.write(
        """<?xml version="1.0" encoding="utf-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml" xmlns:xlink="http://www.w3.org/1999/xlink">
          <body>
            <h1>Глава 1</h1>
            <p>Текст перед картинкой.</p>
            <p><img src="../Images/pic%201.png" alt="Первая иллюстрация"/> Текст после картинки.</p>
            <svg viewBox="0 0 90 90"><image xlink:href="../Images/pic2.png" alt="Векторная вставка"/></svg>
          </body>
        </html>""".toByteArray()
      )
      zos.closeEntry()

      // Malformed HTML5 with unclosed <img> and unknown entity triggers fallback path
      zos.putNextEntry(ZipEntry("OEBPS/Text/ch2.xhtml"))
      zos.write(
        """<html><body><h2>Глава 2</h2><p>Текст с unclosed img<br><img src="../Images/pic2.png" alt="HTML5 картинка"><p>Финальный текст.</p></body></html>"""
          .toByteArray()
      )
      zos.closeEntry()

      zos.putNextEntry(ZipEntry("OEBPS/Images/pic 1.png"))
      zos.write(imgBytes1)
      zos.closeEntry()

      zos.putNextEntry(ZipEntry("OEBPS/Images/pic2.png"))
      zos.write(imgBytes2)
      zos.closeEntry()
    }

    val outDir = tempFolder.newFolder("epub_imgs")
    val (meta, content) = EpubParser.parseForImport(epubFile, "Fallback", outDir)

    assertNotNull("Cover bytes should be resolved from cover XHTML", meta.coverBytes)
    assertEquals(2, content.chapters.size)

    val ch1Images = content.chapters[0].paragraphs.filter { it.kind == ParagraphKind.IMAGE }
    assertEquals(2, ch1Images.size)
    assertEquals("Первая иллюстрация", ch1Images[0].imageAlt)
    assertTrue(File(ch1Images[0].imagePath!!).exists())
    assertTrue(ch1Images[0].imageWidth > 0 && ch1Images[0].imageHeight > 0)
    assertTrue(content.chapters[0].paragraphs.any { it.text.contains("Текст перед картинкой") })
    assertTrue(content.chapters[0].paragraphs.any { it.text.contains("Текст после картинки") })

    val ch2Images = content.chapters[1].paragraphs.filter { it.kind == ParagraphKind.IMAGE }
    assertEquals(1, ch2Images.size)
    assertEquals("HTML5 картинка", ch2Images[0].imageAlt)
    assertTrue(File(ch2Images[0].imagePath!!).exists())
  }

  @Test
  fun `Fb2Parser extracts inline images inside sections and paragraphs`() {
    val imgBytes = createPngBytes(100, 75)
    val b64 = Base64.encodeToString(imgBytes, Base64.NO_WRAP)
    val fb2File = tempFolder.newFile("illustrated.fb2").apply {
      writeText(
        """<?xml version="1.0" encoding="utf-8"?>
        <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:l="http://www.w3.org/1999/xlink">
          <description>
            <title-info>
              <book-title>Книга с картинками</book-title>
              <author><first-name>Иван</first-name><last-name>Петров</last-name></author>
              <coverpage><image l:href="#cover.png"/></coverpage>
            </title-info>
          </description>
          <body>
            <section>
              <title><p>Глава 1</p></title>
              <p>Вступление к иллюстрации.</p>
              <image l:href="#ill1.png" alt="Главная сцена"/>
              <p>Среди текста <image l:href="#ill1.png"/> продолжение мысли.</p>
            </section>
          </body>
          <binary id="cover.png" content-type="image/png">$b64</binary>
          <binary id="ill1.png" content-type="image/png">$b64</binary>
        </FictionBook>""".trimIndent()
      )
    }

    val outDir = tempFolder.newFolder("fb2_imgs")
    val (meta, content) = Fb2Parser.parseForImport(fb2File, "Fallback", outDir)

    assertNotNull(meta.coverBytes)
    assertEquals(1, content.chapters.size)
    val paras = content.chapters[0].paragraphs
    val images = paras.filter { it.kind == ParagraphKind.IMAGE }
    assertEquals(2, images.size)
    assertEquals("Главная сцена", images[0].imageAlt)
    assertTrue(File(images[0].imagePath!!).exists())
    assertTrue(images[0].imageWidth > 0 && images[0].imageHeight > 0)
    assertTrue(paras.any { it.text.contains("Среди текста") })
    assertTrue(paras.any { it.text.contains("продолжение мысли") })
  }

  @Test
  fun `BookCacheStorage v2 round-trips IMAGE paragraphs and invalidates stale v1 caches`() {
    val imgFile = tempFolder.newFile("cached_pic.png").apply {
      writeBytes(createPngBytes(100, 50))
    }
    val content = ChapterMetricsBuilder.buildReflowable(
      rawChapters = listOf(
        BookChapter(
          index = 0,
          title = "Глава 1",
          paragraphs = listOf(
            BookParagraph(0, "Заголовок", ParagraphKind.HEADING),
            BookParagraph(
              index = 1,
              text = "",
              kind = ParagraphKind.IMAGE,
              imagePath = imgFile.absolutePath,
              imageWidth = 100,
              imageHeight = 50,
              imageAlt = "Иллюстрация",
            ),
            BookParagraph(2, "Основной текст.", ParagraphKind.BODY),
          ),
        )
      ),
      rawToc = listOf(TocEntry("Глава 1", 0)),
    )

    val cacheFile = tempFolder.newFile("v2.lcache")
    BookCacheStorage.write(cacheFile, content)
    val loaded = BookCacheStorage.read(cacheFile) as? LoadedBookContent.Reflowable
    assertNotNull(loaded)
    assertEquals(content, loaded)

    val v1File = tempFolder.newFile("v1.lcache")
    DataOutputStream(v1File.outputStream()).use { out ->
      out.writeInt(0x4C535432)
      out.writeInt(1)
      out.writeByte(1)
      out.writeLong(0L)
      out.writeInt(0)
      out.writeInt(0)
    }
    assertNull(BookCacheStorage.read(v1File))
  }

  @Test
  fun `ChapterPaginator reserves image height and paginates IMAGE paragraphs cleanly`() {
    val paginator = ChapterPaginator()
    val measurer = object : TextLineMeasurer {
      override fun measure(
        text: String,
        style: androidx.compose.ui.text.TextStyle,
        textAlign: androidx.compose.ui.text.style.TextAlign,
        maxWidthPx: Int,
      ): MeasuredParagraphLayout {
        return MeasuredParagraphLayout(
          lines = listOf(MeasuredLine(0, text.length, 0f, 36f)),
          totalHeightPx = 36f,
        )
      }
    }

    val chapter = BookChapter(
      index = 0,
      title = "Глава 1",
      paragraphs = listOf(
        BookParagraph(0, "Абзац 1", ParagraphKind.BODY),
        BookParagraph(
          index = 1,
          text = "",
          kind = ParagraphKind.IMAGE,
          imagePath = "/tmp/pic.png",
          imageWidth = 600,
          imageHeight = 500,
          imageAlt = "Большая картинка",
        ),
        BookParagraph(2, "Абзац 2", ParagraphKind.BODY),
      ),
    )

    val pages = paginator.paginateChapter(
      chapter = chapter,
      preferences = ReaderPreferences(),
      viewportWidthPx = 480,
      viewportHeightPx = 600,
      density = Density(2f, 1f),
      measurer = measurer,
    )

    val imgSlice = pages.flatMap { it.slices }.firstOrNull { it.kind == ParagraphKind.IMAGE }
    assertNotNull(imgSlice)
    assertEquals("/tmp/pic.png", imgSlice!!.imagePath)
    assertTrue("Image slice should reserve non-zero height", imgSlice.imageDisplayHeightDp >= 32f)
  }

  @Test
  fun `BookRepository re-extracts FB2 images automatically when cache is missing or v1`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val repo = BookRepository(context, db)
      val imgBytes = createPngBytes(90, 70)
      val b64 = Base64.encodeToString(imgBytes, Base64.NO_WRAP)
      val fb2File = tempFolder.newFile("legacy_import.fb2").apply {
        writeText(
          """<?xml version="1.0" encoding="utf-8"?>
          <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:l="http://www.w3.org/1999/xlink">
            <description><title-info><book-title>Старая книга</book-title></title-info></description>
            <body><section><p>Текст.</p><image l:href="#pic.png"/></section></body>
            <binary id="pic.png" content-type="image/png">$b64</binary>
          </FictionBook>""".trimIndent()
        )
      }

      val v1Cache = tempFolder.newFile("legacy.lcache")
      DataOutputStream(v1Cache.outputStream()).use { out ->
        out.writeInt(0x4C535432)
        out.writeInt(1)
        out.writeByte(1)
      }

      val bookId = runBlocking {
        db.bookDao().insertBook(
          BookEntity(
            title = "Старая книга",
            author = "Автор",
            format = "FB2",
            localFilePath = fb2File.absolutePath,
            contentHash = "test_legacy_hash_${System.nanoTime()}",
            cacheFilePath = v1Cache.absolutePath,
          )
        )
      }

      val book = runBlocking { db.bookDao().getBookById(bookId)!! }
      val result = runBlocking { repo.loadBookContent(book) }
      assertTrue(result.isSuccess)
      val reflowable = result.getOrThrow() as LoadedBookContent.Reflowable
      val imgPara = reflowable.chapters.flatMap { it.paragraphs }.firstOrNull { it.kind == ParagraphKind.IMAGE }
      assertNotNull(imgPara)
      assertTrue(File(imgPara!!.imagePath!!).exists())

      // Clean up book images directory
      runBlocking { repo.deleteBook(db.bookDao().getBookById(bookId)!!) }
    } finally {
      db.close()
    }
  }

  @Test
  fun `Reader renders inline book images in both Pages and Scroll modes`() {
    val imgFile = tempFolder.newFile("render_test.png").apply {
      writeBytes(createPngBytes(120, 80))
    }
    val content = ChapterMetricsBuilder.buildReflowable(
      rawChapters = listOf(
        BookChapter(
          index = 0,
          title = "Глава с картинкой",
          paragraphs = listOf(
            BookParagraph(0, "Текст перед иллюстрацией.", ParagraphKind.BODY),
            BookParagraph(
              index = 1,
              text = "",
              kind = ParagraphKind.IMAGE,
              imagePath = imgFile.absolutePath,
              imageWidth = 120,
              imageHeight = 80,
              imageAlt = "Иллюстрация главы",
            ),
          ),
        )
      ),
      rawToc = listOf(TocEntry("Глава с картинкой", 0)),
    )

    composeTestRule.setContent {
      ListayTheme {
        ReflowablePagesReader(
          bookId = 1L,
          content = content,
          unitIdx = 0,
          initialLocator = Locator(0, 0, 0),
          currentLocatorProvider = { Locator(0, 0, 0) },
          preferences = ReaderPreferences(readingMode = ReaderReadingMode.PAGES),
          quotedParagraphIndices = emptySet(),
          ttsActiveParagraphIndex = null,
          paginator = ChapterPaginator(),
          pageNavHandle = PageNavigationHandle(),
          onPageChanged = { _, _ -> },
          onPrevChapterToEnd = {},
          onNextChapter = {},
          onToggleBars = {},
          onLongPressParagraph = { _, _ -> },
        )
      }
    }

    composeTestRule.waitUntil(timeoutMillis = 5000L) {
      composeTestRule.onAllNodesWithTag("reader_inline_image_1").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("reader_inline_image_1").assertIsDisplayed()
  }

  @Test
  fun `ReaderScreen Scroll mode displays inline book image`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val bookRepository = BookRepository(context, db)
      val preferencesRepository = PreferencesRepository(context)
      val imgFile = tempFolder.newFile("scroll_pic.png").apply {
        writeBytes(createPngBytes(100, 80))
      }
      val content = ChapterMetricsBuilder.buildReflowable(
        rawChapters = listOf(
          BookChapter(
            index = 0,
            title = "Глава",
            paragraphs = listOf(
              BookParagraph(0, "Вступление.", ParagraphKind.BODY),
              BookParagraph(
                index = 1,
                text = "",
                kind = ParagraphKind.IMAGE,
                imagePath = imgFile.absolutePath,
                imageWidth = 100,
                imageHeight = 80,
                imageAlt = "Схема",
              ),
            ),
          )
        ),
        rawToc = listOf(TocEntry("Глава", 0)),
      )
      val cacheFile = tempFolder.newFile("scroll_img.lcache")
      BookCacheStorage.write(cacheFile, content)
      val bookId = runBlocking {
        db.bookDao().insertBook(
          BookEntity(
            title = "Книга",
            author = "Автор",
            format = "EPUB",
            localFilePath = cacheFile.absolutePath,
            contentHash = "scroll_img_hash",
            cacheFilePath = cacheFile.absolutePath,
          )
        )
      }

      val viewModel = ReaderViewModel(
        bookId = bookId,
        initialUnitOverride = null,
        initialParagraphOverride = null,
        initialCharOffsetOverride = null,
        bookRepository = bookRepository,
        preferencesRepository = preferencesRepository,
      )

      composeTestRule.setContent {
        ReaderScreen(
          viewModel = viewModel,
          onNavigateBack = {},
          registerVolumeKeyHandler = {},
        )
      }

      composeTestRule.waitUntil(timeoutMillis = 5000L) {
        composeTestRule.onAllNodesWithTag("reader_inline_image_1").fetchSemanticsNodes().isNotEmpty()
      }
      composeTestRule.onNodeWithTag("reader_inline_image_1").assertIsDisplayed()
    } finally {
      db.close()
    }
  }

  @Test
  fun `BookInlineImage scales small formula images to readable column width`() {
    val formulaFile = tempFolder.newFile("formula.png").apply {
      writeBytes(createPngBytes(width = 160, height = 45))
    }

    composeTestRule.setContent {
      ListayTheme {
        val theme = rememberAnimatedReaderColors(ReaderThemeMode.SEPIA, animationsEnabled = false)
        Box(modifier = androidx.compose.ui.Modifier.width(320.dp)) {
          BookInlineImage(
            imagePath = formulaFile.absolutePath,
            imageWidth = 160,
            imageHeight = 45,
            imageAlt = "Дзета-функция",
            paragraphIndex = 7,
            theme = theme,
          )
        }
      }
    }

    composeTestRule.waitUntil(timeoutMillis = 5000L) {
      composeTestRule.onAllNodesWithContentDescription("Дзета-функция").fetchSemanticsNodes().isNotEmpty()
    }
    val bounds = composeTestRule.onNodeWithContentDescription("Дзета-функция").getUnclippedBoundsInRoot()
    val renderedWidthDp = bounds.right.value - bounds.left.value
    val renderedHeightDp = bounds.bottom.value - bounds.top.value
    assertTrue("Expected formula to scale to full 320dp column, was $renderedWidthDp", renderedWidthDp >= 300f)
    assertTrue("Expected formula height >= 80dp, was $renderedHeightDp", renderedHeightDp >= 80f)
  }
}
