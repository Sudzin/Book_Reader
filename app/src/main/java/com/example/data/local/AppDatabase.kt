package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [BookEntity::class, BookmarkEntity::class, QuoteEntity::class],
  version = 1,
  exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun bookDao(): BookDao
  abstract fun bookmarkDao(): BookmarkDao
  abstract fun quoteDao(): QuoteDao

  companion object {
    @Volatile private var INSTANCE: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "listay_reader.db",
        ).build().also { INSTANCE = it }
      }
    }
  }
}
