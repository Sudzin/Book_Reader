package com.example

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.BookEntity
import com.example.data.local.PreferencesRepository
import com.example.data.local.ReaderThemeMode
import com.example.reader.engine.BookCacheStorage
import com.example.reader.engine.BookChapter
import com.example.reader.engine.BookParagraph
import com.example.reader.engine.ChapterMetricsBuilder
import com.example.reader.engine.ParagraphKind
import com.example.reader.engine.TocEntry
import com.example.data.repository.BookRepository
import com.example.ui.components.BookCoverArt
import com.example.ui.components.bookCoverInitial
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.theme.ListayTheme
import com.example.ui.theme.LocalAnimationsEnabled
import com.example.ui.theme.areSystemAnimationsEnabled
import com.example.ui.viewmodel.LibraryUiState
import com.example.ui.viewmodel.ReaderViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AnimationsAndTypographyTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun `cover without image draws themed monogram with uppercase first letter`() {
    assertEquals("М", bookCoverInitial("  мастер и Маргарита"))
    assertEquals("1", bookCoverInitial("1984"))
    assertEquals("К", bookCoverInitial("   "))

    val book = BookEntity(
      id = 42L,
      title = "Преступление и наказание",
      author = "Фёдор Достоевский",
      format = "EPUB",
      localFilePath = "/tmp/fake.epub",
      contentHash = "hash42",
      coverPath = null,
    )

    composeTestRule.setContent {
      ListayTheme(readerTheme = ReaderThemeMode.SEPIA, dynamicColor = false) {
        BookCoverArt(
          book = book,
          modifier = Modifier
            .width(80.dp)
            .aspectRatio(0.7f),
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("book_cover_monogram_42").assertIsDisplayed()
    composeTestRule.onNodeWithTag("book_cover_initial_42", useUnmergedTree = true).assertTextEquals("П")
  }

  @Test
  fun `areSystemAnimationsEnabled reflects animator duration scale setting`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prevScale = Settings.Global.getFloat(
      context.contentResolver,
      Settings.Global.ANIMATOR_DURATION_SCALE,
      1f,
    )
    try {
      Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
      assertFalse(context.areSystemAnimationsEnabled())

      Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
      assertTrue(context.areSystemAnimationsEnabled())
    } finally {
      Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, prevScale)
    }
  }

  @OptIn(ExperimentalSharedTransitionApi::class)
  @Test
  fun `library shelf items render inside SharedTransitionLayout with animateItem`() {
    val books = listOf(
      BookEntity(
        id = 1L,
        title = "Война и мир",
        author = "Лев Толстой",
        format = "FB2",
        localFilePath = "/tmp/1.fb2",
        contentHash = "h1",
        lastReadAt = 200L,
      ),
      BookEntity(
        id = 2L,
        title = "Евгений Онегин",
        author = "Александр Пушкин",
        format = "TXT",
        localFilePath = "/tmp/2.txt",
        contentHash = "h2",
        lastReadAt = 100L,
      ),
    )
    var openedBookId: Long? = null

    composeTestRule.setContent {
      ListayTheme(readerTheme = ReaderThemeMode.PAPER, dynamicColor = false) {
        SharedTransitionLayout {
          AnimatedVisibility(visible = true) {
            LibraryScreen(
              uiState = LibraryUiState(books = books),
              onSearchQueryChanged = {},
              onFormatSelected = {},
              onImportBookClick = {},
              onOpenBook = { openedBookId = it },
              onDeleteBook = {},
              sharedTransitionScope = this@SharedTransitionLayout,
              animatedVisibilityScope = this@AnimatedVisibility,
            )
          }
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("continue_reading_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("library_books_list")
      .performScrollToNode(hasTestTag("book_item_2"))
    composeTestRule.onNodeWithTag("book_item_2").assertIsDisplayed()
    composeTestRule.onNodeWithTag("book_item_2").performClick()
    composeTestRule.waitForIdle()
    assertEquals(2L, openedBookId)
  }

  @Test
  fun `reader toggles bookmark cleanly and displays synthetic hero heading when animations are disabled`() {
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
            title = "Часть первая",
            paragraphs = listOf(
              BookParagraph(0, "Начало главы без встроенного тега заголовка.", ParagraphKind.BODY),
            ),
          )
        ),
        rawToc = listOf(TocEntry("Часть первая", 0)),
      )
      val cacheFile = tempFolder.newFile("anim_typo.lcache")
      BookCacheStorage.write(cacheFile, content)

      val bookId = runBlocking {
        db.bookDao().insertBook(
          BookEntity(
            title = "Тестовый том",
            author = "Автор",
            format = "TXT",
            localFilePath = cacheFile.absolutePath,
            contentHash = "anim_hash",
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
        CompositionLocalProvider(LocalAnimationsEnabled provides false) {
          ReaderScreen(
            viewModel = viewModel,
            onNavigateBack = {},
            registerVolumeKeyHandler = {},
          )
        }
      }

      composeTestRule.waitUntil(timeoutMillis = 5000L) {
        composeTestRule.onAllNodesWithTag("reader_bookmark_button").fetchSemanticsNodes().isNotEmpty() &&
          composeTestRule.onAllNodesWithTag("reader_chapter_hero_heading").fetchSemanticsNodes().isNotEmpty()
      }
      composeTestRule.waitForIdle()
      composeTestRule.mainClock.advanceTimeBy(50L)
      composeTestRule.waitForIdle()

      composeTestRule.onNodeWithTag("reader_chapter_hero_heading").assertIsDisplayed()
      composeTestRule.onNodeWithText("Часть первая").assertIsDisplayed()
      composeTestRule.onNodeWithTag("reader_bookmark_button").performClick()
      composeTestRule.waitUntil(timeoutMillis = 3000L) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        viewModel.bookmarks.value.isNotEmpty() && viewModel.isCurrentUnitBookmarked.value
      }

      // Bars toggle instantaneously when system animations are disabled
      composeTestRule.onNodeWithText("Начало главы без встроенного тега заголовка.").performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("reader_hidden_progress_line").assertIsDisplayed()
    } finally {
      db.close()
    }
  }
}
