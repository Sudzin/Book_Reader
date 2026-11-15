package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [BookEntity::class, BookmarkEntity::class, QuoteEntity::class],
  version = 2,
  exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun bookDao(): BookDao
  abstract fun bookmarkDao(): BookmarkDao
  abstract fun quoteDao(): QuoteDao

  companion object {
    @Volatile private var INSTANCE: AppDatabase? = null

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS books_new (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            title TEXT NOT NULL,
            author TEXT NOT NULL,
            format TEXT NOT NULL,
            localFilePath TEXT NOT NULL,
            coverPath TEXT,
            contentHash TEXT NOT NULL,
            totalUnits INTEGER NOT NULL,
            totalChars INTEGER NOT NULL,
            cacheFilePath TEXT,
            locator_chapterIndex INTEGER NOT NULL,
            locator_paragraphIndex INTEGER NOT NULL,
            locator_charOffset INTEGER NOT NULL,
            progressPercent REAL NOT NULL,
            lastReadAt INTEGER NOT NULL,
            addedAt INTEGER NOT NULL,
            accentColorHex INTEGER NOT NULL
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          INSERT INTO books_new (
            id, title, author, format, localFilePath, coverPath, contentHash,
            totalUnits, totalChars, cacheFilePath,
            locator_chapterIndex, locator_paragraphIndex, locator_charOffset,
            progressPercent, lastReadAt, addedAt, accentColorHex
          )
          SELECT
            id, title, author, format, localFilePath, coverPath, contentHash,
            totalUnits, 0, NULL,
            currentUnitIndex, currentParagraphIndex, 0,
            progressPercent, lastReadAt, addedAt, accentColorHex
          FROM books
          """.trimIndent()
        )
        db.execSQL("DROP TABLE books")
        db.execSQL("ALTER TABLE books_new RENAME TO books")

        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS bookmarks_new (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            bookId INTEGER NOT NULL,
            locator_chapterIndex INTEGER NOT NULL,
            locator_paragraphIndex INTEGER NOT NULL,
            locator_charOffset INTEGER NOT NULL,
            unitTitle TEXT NOT NULL,
            previewText TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          INSERT INTO bookmarks_new (
            id, bookId, locator_chapterIndex, locator_paragraphIndex, locator_charOffset,
            unitTitle, previewText, createdAt
          )
          SELECT
            id, bookId, unitIndex, paragraphIndex, 0,
            unitTitle, previewText, createdAt
          FROM bookmarks
          """.trimIndent()
        )
        db.execSQL("DROP TABLE bookmarks")
        db.execSQL("ALTER TABLE bookmarks_new RENAME TO bookmarks")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_bookId ON bookmarks(bookId)")

        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS quotes_new (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            bookId INTEGER NOT NULL,
            bookTitle TEXT NOT NULL,
            bookAuthor TEXT NOT NULL,
            locator_chapterIndex INTEGER NOT NULL,
            locator_paragraphIndex INTEGER NOT NULL,
            locator_charOffset INTEGER NOT NULL,
            unitTitle TEXT NOT NULL,
            text TEXT NOT NULL,
            note TEXT NOT NULL,
            colorTag TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          INSERT INTO quotes_new (
            id, bookId, bookTitle, bookAuthor,
            locator_chapterIndex, locator_paragraphIndex, locator_charOffset,
            unitTitle, text, note, colorTag, createdAt
          )
          SELECT
            id, bookId, bookTitle, bookAuthor,
            unitIndex, paragraphIndex, 0,
            unitTitle, text, note, colorTag, createdAt
          FROM quotes
          """.trimIndent()
        )
        db.execSQL("DROP TABLE quotes")
        db.execSQL("ALTER TABLE quotes_new RENAME TO quotes")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_quotes_bookId ON quotes(bookId)")
      }
    }

    fun getInstance(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "listay_reader.db",
        )
          .addMigrations(MIGRATION_1_2)
          .build()
          .also { INSTANCE = it }
      }
    }
  }
}
