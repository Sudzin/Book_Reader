package com.example

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.BookEntity
import com.example.data.local.PreferencesRepository
import com.example.data.repository.BookRepository
import com.example.reader.engine.BookCacheStorage
import com.example.reader.engine.BookChapter
import com.example.reader.engine.BookParagraph
import com.example.reader.engine.ChapterMetricsBuilder
import com.example.reader.engine.ParagraphKind
import com.example.reader.engine.TocEntry
import com.example.ui.screens.ReaderScreen
import com.example.ui.theme.ListayTheme
import com.example.ui.viewmodel.ReaderViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackNavigationAndManifestTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @get:Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun `MainActivity is locked to portrait orientation in manifest`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val activityInfo = context.packageManager.getActivityInfo(
      ComponentName(context, MainActivity::class.java),
      0,
    )
    assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activityInfo.screenOrientation)
  }

  @Test
  fun `back gesture sequentially closes sheet, reader bars, reader screen, and hands off to system app exit`() {
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
              BookParagraph(0, "Абзац для проверки пошагового возврата назад.", ParagraphKind.BODY),
            ),
          ),
        ),
        rawToc = listOf(TocEntry("Глава 1", 0)),
      )
      val cacheFile = tempFolder.newFile("back_nav_test.lcache")
      BookCacheStorage.write(cacheFile, content)

      val bookId = runBlocking {
        db.bookDao().insertBook(
          BookEntity(
            title = "Проверка Назад",
            author = "Автор",
            format = "TXT",
            localFilePath = cacheFile.absolutePath,
            contentHash = "hash_back_nav",
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
        ListayTheme {
          val navController = rememberNavController()
          NavHost(navController = navController, startDestination = "library") {
            composable("library") {
              Box(modifier = Modifier.fillMaxSize().testTag("test_library_root")) {
                Text("Полка")
              }
            }
            composable("reader") {
              ReaderScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                registerVolumeKeyHandler = {},
              )
            }
          }

          androidx.compose.runtime.LaunchedEffect(Unit) {
            navController.navigate("reader")
          }
        }
      }

      composeTestRule.waitUntil(timeoutMillis = 5000L) {
        composeTestRule.onAllNodesWithTag("reader_bottom_bar").fetchSemanticsNodes().isNotEmpty()
      }

      // Step 1: Open appearance bottom sheet, then press Back -> closes sheet only
      composeTestRule.onNodeWithTag("reader_appearance_button").performClick()
      composeTestRule.waitUntil(timeoutMillis = 3000L) {
        composeTestRule.onAllNodesWithText("Оформление страницы").fetchSemanticsNodes().isNotEmpty()
      }
      composeTestRule.onNodeWithText("Оформление страницы").assertIsDisplayed()

      val sheetDialog = ShadowDialog.getLatestDialog() as? ComponentDialog
      assertNotNull("Expected ModalBottomSheet ComponentDialog to be active", sheetDialog)
      composeTestRule.runOnUiThread {
        sheetDialog!!.onBackPressedDispatcher.onBackPressed()
      }
      composeTestRule.waitForIdle()
      composeTestRule.mainClock.advanceTimeBy(500L)
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithText("Оформление страницы").assertDoesNotExist()
      composeTestRule.onNodeWithTag("reader_bottom_bar").assertIsDisplayed()

      // Step 2: With bars visible, press Back -> hides reader bars, stays in reader
      composeTestRule.runOnUiThread {
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
      }
      composeTestRule.waitForIdle()
      composeTestRule.mainClock.advanceTimeBy(500L)
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("reader_bottom_bar").assertDoesNotExist()
      composeTestRule.onNodeWithTag("reader_hidden_progress_line").assertIsDisplayed()
      composeTestRule.onNodeWithTag("test_library_root").assertDoesNotExist()

      // Step 3: With bars hidden, press Back -> closes reader and returns to library root
      composeTestRule.runOnUiThread {
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
      }
      composeTestRule.waitUntil(timeoutMillis = 3000L) {
        composeTestRule.onAllNodesWithTag("test_library_root").fetchSemanticsNodes().isNotEmpty()
      }
      composeTestRule.onNodeWithTag("test_library_root").assertIsDisplayed()

      // Step 4: At root screen, no enabled back callback intercepts predictive back-to-home
      composeTestRule.runOnUiThread {
        assertFalse(composeTestRule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
      }
    } finally {
      db.close()
    }
  }
}
