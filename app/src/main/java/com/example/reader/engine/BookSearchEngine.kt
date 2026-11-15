package com.example.reader.engine

import java.io.File

data class BookSearchResult(
  val locator: Locator,
  val chapterTitle: String,
  val snippet: String,
  val matchStartInSnippet: Int,
  val matchLength: Int,
)

object BookSearchEngine {

  fun searchFromCache(
    cacheFile: File,
    query: String,
    maxResults: Int = 200,
  ): List<BookSearchResult> {
    val content = BookCacheStorage.read(cacheFile) as? LoadedBookContent.Reflowable ?: return emptyList()
    return search(content = content, query = query, maxResults = maxResults)
  }

  fun search(
    content: LoadedBookContent.Reflowable,
    query: String,
    maxResults: Int = 200,
  ): List<BookSearchResult> {
    val trimmed = query.trim()
    if (trimmed.isEmpty() || maxResults <= 0) return emptyList()

    val results = mutableListOf<BookSearchResult>()
    for (chapter in content.chapters) {
      for (paragraph in chapter.paragraphs) {
        val text = paragraph.text
        var searchFrom = 0
        while (searchFrom <= text.length - trimmed.length) {
          val matchIdx = text.indexOf(trimmed, startIndex = searchFrom, ignoreCase = true)
          if (matchIdx < 0) break

          val (snippet, matchStart) = buildSnippet(text, matchIdx, trimmed.length)
          results.add(
            BookSearchResult(
              locator = Locator(
                chapterIndex = chapter.index,
                paragraphIndex = paragraph.index,
                charOffset = matchIdx,
              ),
              chapterTitle = chapter.title,
              snippet = snippet,
              matchStartInSnippet = matchStart,
              matchLength = trimmed.length,
            )
          )
          if (results.size >= maxResults) return results
          searchFrom = matchIdx + trimmed.length.coerceAtLeast(1)
        }
      }
    }
    return results
  }

  private fun buildSnippet(text: String, matchIdx: Int, matchLen: Int): Pair<String, Int> {
    val contextBefore = 42
    val contextAfter = 78
    val rawStart = (matchIdx - contextBefore).coerceAtLeast(0)
    val rawEnd = (matchIdx + matchLen + contextAfter).coerceAtMost(text.length)

    val prefix = if (rawStart > 0) "…" else ""
    val suffix = if (rawEnd < text.length) "…" else ""
    val snippet = prefix + text.substring(rawStart, rawEnd) + suffix
    val matchStartInSnippet = prefix.length + (matchIdx - rawStart)
    return snippet to matchStartInSnippet
  }
}
