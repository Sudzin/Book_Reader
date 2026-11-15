package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.reader.engine.Locator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DatabaseMigrationTest {

  @Test
  fun `migration 1 to 2 preserves books, bookmarks, and quotes with Locator fields`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbName = "migration_test_${System.nanoTime()}.db"

    val v1Helper = object : SQLiteOpenHelper(context, dbName, null, 1) {
      override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE books (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            title TEXT NOT NULL,
            author TEXT NOT NULL,
            format TEXT NOT NULL,
            localFilePath TEXT NOT NULL,
            coverPath TEXT,
            contentHash TEXT NOT NULL,
            totalUnits INTEGER NOT NULL,
            currentUnitIndex INTEGER NOT NULL,
            currentParagraphIndex INTEGER NOT NULL,
            progressPercent REAL NOT NULL,
            lastReadAt INTEGER NOT NULL,
            addedAt INTEGER NOT NULL,
            accentColorHex INTEGER NOT NULL
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          CREATE TABLE bookmarks (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            bookId INTEGER NOT NULL,
            unitIndex INTEGER NOT NULL,
            paragraphIndex INTEGER NOT NULL,
            unitTitle TEXT NOT NULL,
            previewText TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE
          )
          """.trimIndent()
        )
        db.execSQL("CREATE INDEX index_bookmarks_bookId ON bookmarks(bookId)")
        db.execSQL(
          """
          CREATE TABLE quotes (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            bookId INTEGER NOT NULL,
            bookTitle TEXT NOT NULL,
            bookAuthor TEXT NOT NULL,
            unitIndex INTEGER NOT NULL,
            paragraphIndex INTEGER NOT NULL,
            unitTitle TEXT NOT NULL,
            text TEXT NOT NULL,
            note TEXT NOT NULL,
            colorTag TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE
          )
          """.trimIndent()
        )
        db.execSQL("CREATE INDEX index_quotes_bookId ON quotes(bookId)")

        db.execSQL(
          """
          INSERT INTO books (
            id, title, author, format, localFilePath, coverPath, contentHash,
            totalUnits, currentUnitIndex, currentParagraphIndex, progressPercent,
            lastReadAt, addedAt, accentColorHex
          ) VALUES (
            1, 'Мастер и Маргарита', 'Михаил Булгаков', 'FB2', '/tmp/book.fb2', NULL, 'abc123',
            32, 7, 14, 0.25, 1700000000000, 1699999000000, 123456
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          INSERT INTO bookmarks (
            id, bookId, unitIndex, paragraphIndex, unitTitle, previewText, createdAt
          ) VALUES (
            10, 1, 5, 3, 'Глава 6', 'Шизофрения, как и было сказано.', 1700000001000
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          INSERT INTO quotes (
            id, bookId, bookTitle, bookAuthor, unitIndex, paragraphIndex,
            unitTitle, text, note, colorTag, createdAt
          ) VALUES (
            20, 1, 'Мастер и Маргарита', 'Михаил Булгаков', 2, 9,
            'Глава 3', 'Рукописи не горят.', 'Избранное', 'TERRACOTTA', 1700000002000
          )
          """.trimIndent()
        )
      }

      override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    v1Helper.writableDatabase.close()
    v1Helper.close()

    val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
      .addMigrations(AppDatabase.MIGRATION_1_2)
      .allowMainThreadQueries()
      .build()

    try {
      val book = roomDb.bookDao().getBookById(1L)
      assertEquals("Мастер и Маргарита", book?.title)
      assertEquals(Locator(7, 14, 0), book?.locator)
      assertEquals(0L, book?.totalChars)
      assertNull(book?.cacheFilePath)
      assertEquals(0.25f, book?.progressPercent ?: 0f, 0.0001f)

      val bookmarks = roomDb.bookmarkDao().observeBookmarksForBook(1L).first()
      assertEquals(1, bookmarks.size)
      assertEquals(Locator(5, 3, 0), bookmarks[0].locator)
      assertEquals("Глава 6", bookmarks[0].unitTitle)

      val quotes = roomDb.quoteDao().observeAllQuotes().first()
      assertEquals(1, quotes.size)
      assertEquals(Locator(2, 9, 0), quotes[0].locator)
      assertEquals("Рукописи не горят.", quotes[0].text)
      assertEquals("Избранное", quotes[0].note)
    } finally {
      roomDb.close()
      context.deleteDatabase(dbName)
    }
  }
}
