package com.example.reader.engine

enum class ParagraphKind {
  HEADING,
  SUBHEADING,
  BODY,
  QUOTE,
  POEM,
  CODE,
}

data class BookParagraph(
  val index: Int,
  val text: String,
  val kind: ParagraphKind = ParagraphKind.BODY,
)

data class BookChapter(
  val index: Int,
  val title: String,
  val paragraphs: List<BookParagraph>,
)

data class TocEntry(
  val title: String,
  val unitIndex: Int,
)

data class ParsedBookMetadata(
  val title: String,
  val author: String,
  val coverBytes: ByteArray?,
  val totalUnits: Int,
)

sealed interface LoadedBookContent {
  data class Reflowable(
    val chapters: List<BookChapter>,
    val toc: List<TocEntry>,
  ) : LoadedBookContent

  data class PdfDocumentContent(
    val filePath: String,
    val pageCount: Int,
    val toc: List<TocEntry>,
  ) : LoadedBookContent
}
