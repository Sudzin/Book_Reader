package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.local.ReaderPreferences
import com.example.data.local.ReaderReadingMode
import com.example.reader.engine.BookChapter
import com.example.reader.engine.BookParagraph
import com.example.reader.engine.ChapterMetricsBuilder
import com.example.reader.engine.ChapterPaginator
import com.example.reader.engine.Locator
import com.example.reader.engine.ParagraphKind
import com.example.reader.engine.TocEntry
import com.example.ui.screens.PageNavigationHandle
import com.example.ui.screens.ReaderAppearanceSheet
import com.example.ui.screens.ReflowablePagesReader
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ListayTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReaderModesRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

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
  fun `ReaderAppearanceSheet switches reading mode`() {
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
}
