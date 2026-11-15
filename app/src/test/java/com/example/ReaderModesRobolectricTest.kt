package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import com.example.reader.engine.Locator
import com.example.reader.engine.ParagraphKind
import com.example.reader.engine.TocEntry
import com.example.ui.screens.PageNavigationHandle
import com.example.ui.screens.ReaderAppearanceSheet
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.ReflowablePagesReader
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ListayTheme
import com.example.ui.viewmodel.ReaderViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReaderModesRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun `SettingsScreen switches between Scroll and Pages reading modes`() {
    var selectedMode = ReaderReadingMode.SCROLL

    composeTestRule.setContent {
      ListayTheme {
        SettingsScreen(
          preferences = ReaderPreferences(readingMode = selectedMode),
          bookCount = 3,
          quoteCount = 1,
          onSelectTheme = {},
          onSelectReadingMode = { selectedMode = it },
          onToggleDynamicColor = {},
          onToggleVolumePaging = {},
          onToggleKeepScreenOn = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("settings_reading_mode_pages").performClick()
    composeTestRule.waitForIdle()
    assertEquals(ReaderReadingMode.PAGES, selectedMode)
  }

  @Test
  fun `ReaderAppearanceSheet switches reading mode and displays themed thin sliders`() {
    var selectedMode = ReaderReadingMode.SCROLL

    composeTestRule.setContent {
      ListayTheme {
        ReaderAppearanceSheet(
          preferences = ReaderPreferences(readingMode = selectedMode),
          onDismiss = {},
          onThemeSelected = {},
          onReadingModeSelected = { selectedMode = it },
          onFontSelected = {},
          onFontSizeChanged = {},
          onLineHeightChanged = {},
          onMarginChanged = {},
          onJustifyChanged = {},
          onHyphenationChanged = {},
          onVolumeNavChanged = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("appearance_mode_pages").performClick()
    composeTestRule.waitForIdle()
    assertEquals(ReaderReadingMode.PAGES, selectedMode)

    composeTestRule.onNodeWithTag("appearance_slider_font_size").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("appearance_slider_line_height").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("appearance_slider_margin").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `ReflowablePagesReader renders paginated content cleanly`() {
    val content = ChapterMetricsBuilder.buildReflowable(
      rawChapters = listOf(
        BookChapter(
          index = 0,
          title = "Глава 1",
          paragraphs = listOf(
            BookParagraph(0, "Глава 1. Вечер", ParagraphKind.HEADING),
            BookParagraph(1, "Тёплый свет лампы падал на страницы старой книги.", ParagraphKind.BODY),
          ),
        )
      ),
      rawToc = listOf(TocEntry("Глава 1", 0)),
    )
    val paginator = ChapterPaginator()
    val navHandle = PageNavigationHandle()

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
          paginator = paginator,
          pageNavHandle = navHandle,
          onPageChanged = { _, _ -> },
          onPrevChapterToEnd = {},
          onNextChapter = {},
          onToggleBars = {},
          onLongPressParagraph = { _, _ -> },
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("reader_pages_container").assertIsDisplayed()
    composeTestRule.waitUntil(timeoutMillis = 5000L) {
      composeTestRule.onAllNodesWithTag("reader_horizontal_pager").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("reader_horizontal_pager").assertIsDisplayed()
    composeTestRule.onNodeWithText("Глава 1. Вечер").assertIsDisplayed()
  }

  @Test
  fun `ReaderScreen bottom bar has thin slider, labels without arrows, and 2dp progress line when hidden`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val bookRepository = BookRepository(context, db)
      val preferencesRepository = PreferencesRepository(context)

      val content = ChapterMetricsBuilder.buildReflowable(
        rawChapters = listOf(
          BookChapter(
            index = 0,
            title = "Глава 1",
            paragraphs = listOf(
              BookParagraph(0, "Первый абзац для проверки читалки и переключения панелей.", ParagraphKind.BODY),
            ),
          ),
          BookChapter(
            index = 1,
            title = "Глава 2",
            paragraphs = listOf(
              BookParagraph(0, "Вторая глава книги.", ParagraphKind.BODY),
            ),
          ),
        ),
        rawToc = listOf(TocEntry("Глава 1", 0), TocEntry("Глава 2", 1)),
      )
      val cacheFile = tempFolder.newFile("ui_reader_test.lcache")
      BookCacheStorage.write(cacheFile, content)

      val bookId = runBlocking {
        db.bookDao().insertBook(
          BookEntity(
            title = "Тестовая книга",
            author = "Автор",
            format = "TXT",
            localFilePath = cacheFile.absolutePath,
            contentHash = "hash_ui_test",
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
        composeTestRule.onAllNodesWithTag("reader_bottom_bar").fetchSemanticsNodes().isNotEmpty()
      }

      composeTestRule.onNodeWithTag("reader_bottom_bar").assertIsDisplayed()
      composeTestRule.onNodeWithTag("reader_bottom_chapter_label").assertIsDisplayed()
      composeTestRule.onNodeWithTag("reader_bottom_percent_label").assertIsDisplayed()
      composeTestRule.onNodeWithTag("reader_bottom_slider").assertIsDisplayed()

      // Arrow buttons were removed from bottom bar
      composeTestRule.onNodeWithTag("reader_prev_unit").assertDoesNotExist()
      composeTestRule.onNodeWithTag("reader_next_unit").assertDoesNotExist()
      composeTestRule.onNodeWithTag("reader_hidden_progress_line").assertDoesNotExist()

      // Tap paragraph text to hide top/bottom bars -> only 2 dp progress line remains at bottom
      composeTestRule.onNodeWithText("Первый абзац для проверки читалки и переключения панелей.").performClick()
      composeTestRule.waitForIdle()
      composeTestRule.mainClock.advanceTimeBy(500L)
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("reader_hidden_progress_line").assertIsDisplayed()
      composeTestRule.onNodeWithTag("reader_bottom_bar").assertDoesNotExist()
    } finally {
      db.close()
    }
  }
}
