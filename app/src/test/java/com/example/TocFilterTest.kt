package com.example

import com.example.reader.engine.TocEntry
import com.example.reader.engine.TocFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class TocFilterTest {
  @Test
  fun `filterToc removes blank entries and title plus author entries`() {
    val raw = listOf(
      TocEntry("", 0),
      TocEntry("   ", 1),
      TocEntry("Мастер и Маргарита — Михаил Булгаков", 2),
      TocEntry("Михаил Булгаков. Мастер и Маргарита", 3),
      TocEntry("Часть 1", 4),
      TocEntry("Глава 1. Никогда не разговаривайте с неизвестными", 5),
    )

    val filtered = TocFilter.filterToc(
      entries = raw,
      bookTitle = "Мастер и Маргарита",
      bookAuthor = "Михаил Булгаков",
    )

    assertEquals(
      listOf(
        TocEntry("Часть 1", 4),
        TocEntry("Глава 1. Никогда не разговаривайте с неизвестными", 5),
      ),
      filtered,
    )
  }
}
