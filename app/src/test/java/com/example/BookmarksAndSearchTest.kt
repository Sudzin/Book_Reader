package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.BookEntity
import com.example.data.repository.BookRepository
import com.example.reader.engine.BookCacheStorage
import com.example.reader.engine.BookChapter
import com.example.reader.engine.BookParagraph
import com.example.reader.engine.BookSearchEngine
import com.example.reader.engine.ChapterMetricsBuilder
import com.example.reader.engine.Locator
import com.example.reader.engine.ParagraphKind
import com.example.reader.engine.TocEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BookmarksAndSearchTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var repository: BookRepository

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repository = BookRepository(context, db)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `multiple bookmarks can be placed in the same chapter on different Locators`() = runTest {
    val bookId = db.bookDao().insertBook(
      BookEntity(
        title = "Повести",
        author = "А. С. Пушкин",
        format = "FB2",
        localFilePath = "/tmp/dummy.fb2",
        contentHash = "hash_1",
      )
    )

    val loc1 = Locator(chapterIndex = 0, paragraphIndex = 2, charOffset = 0)
    val loc2 = Locator(chapterIndex = 0, paragraphIndex = 7, charOffset = 15)
    val loc3 = Locator(chapterIndex = 0, paragraphIndex = 12, charOffset = 40)

    assertTrue(repository.toggleBookmark(bookId, loc1, "Глава 1", "Первая закладка"))
    assertTrue(repository.toggleBookmark(bookId, loc2, "Глава 1", "Вторая закладка"))
    assertTrue(repository.toggleBookmark(bookId, loc3, "Глава 1", "Третья закладка"))

    val saved = repository.observeBookmarksForBook(bookId).first()
    assertEquals(3, saved.size)
    assertEquals(listOf(loc1, loc2, loc3), saved.map { it.locator })

    // Toggling off the second bookmark removes only loc2, keeping loc1 and loc3
    assertFalse(repository.toggleBookmark(bookId, loc2, "Глава 1", "Вторая закладка"))
    val afterRemove = repository.observeBookmarksForBook(bookId).first()
    assertEquals(listOf(loc1, loc3), afterRemove.map { it.locator })
  }

  @Test
  fun `searchBookText searches cached lcache file and returns exact Locators`() = runTest {
    val content = ChapterMetricsBuilder.buildReflowable(
      rawChapters = listOf(
        BookChapter(
          index = 0,
          title = "Глава 1",
          paragraphs = listOf(
            BookParagraph(0, "Утро в сосновом лесу.", ParagraphKind.BODY),
            BookParagraph(1, "На столе горел старый фонарь и отбрасывал мягкий свет.", ParagraphKind.BODY),
          ),
        ),
        BookChapter(
          index = 1,
          title = "Глава 2",
          paragraphs = listOf(
            BookParagraph(0, "Фонарь погас под порывом северного ветра, но другой фонарь ещё светился.", ParagraphKind.BODY),
          ),
        ),
      ),
      rawToc = listOf(TocEntry("Глава 1", 0), TocEntry("Глава 2", 1)),
    )

    val cacheFile = tempFolder.newFile("cached_book.lcache")
    BookCacheStorage.write(cacheFile, content)

    val book = BookEntity(
      id = 1L,
      title = "Рассказ",
      author = "Автор",
      format = "TXT",
      localFilePath = "/tmp/missing.txt",
      contentHash = "hash_search",
      cacheFilePath = cacheFile.absolutePath,
    )

    val results = repository.searchBookText(
      book = book,
      fallbackContent = null,
      query = "фонарь",
    )

    assertEquals(3, results.size)
    assertEquals(Locator(0, 1, 22), results[0].locator)
    assertEquals(Locator(1, 0, 0), results[1].locator)
    assertEquals(Locator(1, 0, 52), results[2].locator)
    assertEquals("Глава 1", results[0].chapterTitle)
    assertEquals("Глава 2", results[1].chapterTitle)

    val directCacheResults = BookSearchEngine.searchFromCache(cacheFile, "северного")
    assertEquals(1, directCacheResults.size)
    assertEquals(Locator(1, 0, 25), directCacheResults[0].locator)
  }
}
