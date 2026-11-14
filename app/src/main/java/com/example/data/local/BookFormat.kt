package com.example.data.local

enum class BookFormat(val label: String) {
  EPUB("EPUB"),
  FB2("FB2"),
  TXT("TXT"),
  PDF("PDF");

  companion object {
    fun fromExtensionOrMime(fileName: String, mimeType: String?): BookFormat? {
      val lower = fileName.lowercase()
      return when {
        lower.endsWith(".epub") -> EPUB
        lower.endsWith(".fb2.zip") || lower.endsWith(".fb2") -> FB2
        lower.endsWith(".pdf") -> PDF
        lower.endsWith(".txt") -> TXT
        mimeType == "application/epub+zip" -> EPUB
        mimeType == "application/pdf" -> PDF
        mimeType == "application/x-fictionbook+xml" || mimeType == "application/fb2+zip" -> FB2
        mimeType == "text/plain" -> TXT
        else -> null
      }
    }
  }
}
