package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
  @Query("SELECT * FROM books ORDER BY CASE WHEN lastReadAt > 0 THEN lastReadAt ELSE addedAt END DESC")
  fun observeAllBooks(): Flow<List<BookEntity>>

  @Query("SELECT * FROM books WHERE id = :bookId")
  fun observeBookById(bookId: Long): Flow<BookEntity?>

  @Query("SELECT * FROM books WHERE id = :bookId")
  suspend fun getBookById(bookId: Long): BookEntity?

  @Query("SELECT * FROM books WHERE contentHash = :hash LIMIT 1")
  suspend fun getBookByHash(hash: String): BookEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBook(book: BookEntity): Long

  @Query(
    """
    UPDATE books
    SET currentUnitIndex = :unitIndex,
        currentParagraphIndex = :paragraphIndex,
        progressPercent = :progressPercent,
        totalUnits = :totalUnits,
        lastReadAt = :lastReadAt
    WHERE id = :bookId
    """
  )
  suspend fun updateProgress(
    bookId: Long,
    unitIndex: Int,
    paragraphIndex: Int,
    progressPercent: Float,
    totalUnits: Int,
    lastReadAt: Long,
  )

  @Query("DELETE FROM books WHERE id = :bookId")
  suspend fun deleteBookById(bookId: Long)
}

@Dao
interface BookmarkDao {
  @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY unitIndex ASC, paragraphIndex ASC")
  fun observeBookmarksForBook(bookId: Long): Flow<List<BookmarkEntity>>

  @Query("SELECT * FROM bookmarks WHERE bookId = :bookId AND unitIndex = :unitIndex LIMIT 1")
  suspend fun findBookmarkInUnit(bookId: Long, unitIndex: Int): BookmarkEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBookmark(bookmark: BookmarkEntity): Long

  @Query("DELETE FROM bookmarks WHERE id = :id")
  suspend fun deleteBookmarkById(id: Long)
}

@Dao
interface QuoteDao {
  @Query("SELECT * FROM quotes ORDER BY createdAt DESC")
  fun observeAllQuotes(): Flow<List<QuoteEntity>>

  @Query("SELECT * FROM quotes WHERE bookId = :bookId ORDER BY unitIndex ASC, paragraphIndex ASC")
  fun observeQuotesForBook(bookId: Long): Flow<List<QuoteEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertQuote(quote: QuoteEntity): Long

  @Query("UPDATE quotes SET note = :note, colorTag = :colorTag WHERE id = :id")
  suspend fun updateQuoteNote(id: Long, note: String, colorTag: String)

  @Query("DELETE FROM quotes WHERE id = :id")
  suspend fun deleteQuoteById(id: Long)
}
