package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val title: String,
  val author: String,
  val format: String,
  val localFilePath: String,
  val coverPath: String? = null,
  val contentHash: String,
  val totalUnits: Int = 1,
  val currentUnitIndex: Int = 0,
  val currentParagraphIndex: Int = 0,
  val progressPercent: Float = 0f,
  val lastReadAt: Long = 0L,
  val addedAt: Long = System.currentTimeMillis(),
  val accentColorHex: Long = 0xFF9C422EL,
)

@Entity(
  tableName = "bookmarks",
  foreignKeys = [
    ForeignKey(
      entity = BookEntity::class,
      parentColumns = ["id"],
      childColumns = ["bookId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [Index("bookId")],
)
data class BookmarkEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val bookId: Long,
  val unitIndex: Int,
  val paragraphIndex: Int,
  val unitTitle: String,
  val previewText: String,
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "quotes",
  foreignKeys = [
    ForeignKey(
      entity = BookEntity::class,
      parentColumns = ["id"],
      childColumns = ["bookId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [Index("bookId")],
)
data class QuoteEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val bookId: Long,
  val bookTitle: String,
  val bookAuthor: String,
  val unitIndex: Int,
  val paragraphIndex: Int,
  val unitTitle: String,
  val text: String,
  val note: String = "",
  val colorTag: String = "TERRACOTTA",
  val createdAt: Long = System.currentTimeMillis(),
)
