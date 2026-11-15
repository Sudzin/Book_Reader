package com.example.reader.engine

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

object BookCacheStorage {
  private const val MAGIC = 0x4C535432 // "LST2"
  private const val VERSION = 1
  private const val TYPE_REFLOWABLE = 1
  private const val TYPE_PDF = 2

  fun write(file: File, content: LoadedBookContent) {
    file.parentFile?.let { if (!it.exists()) it.mkdirs() }
    val tmp = File(file.parentFile, "${file.name}.tmp")
    DataOutputStream(BufferedOutputStream(tmp.outputStream(), 16 * 1024)).use { out ->
      out.writeInt(MAGIC)
      out.writeInt(VERSION)
      when (content) {
        is LoadedBookContent.Reflowable -> {
          out.writeByte(TYPE_REFLOWABLE)
          out.writeLong(content.totalChars)
          out.writeInt(content.chapters.size)
          for (ch in content.chapters) {
            out.writeInt(ch.index)
            out.writeUTFString(ch.title)
            out.writeLong(ch.charCount)
            out.writeLong(ch.startCharOffset)
            out.writeInt(ch.paragraphs.size)
            for (p in ch.paragraphs) {
              out.writeInt(p.index)
              out.writeByte(p.kind.ordinal)
              out.writeInt(p.charOffsetInChapter)
              out.writeUTFString(p.text)
            }
          }
          out.writeInt(content.toc.size)
          for (t in content.toc) {
            out.writeUTFString(t.title)
            out.writeInt(t.unitIndex)
          }
        }

        is LoadedBookContent.PdfDocumentContent -> {
          out.writeByte(TYPE_PDF)
          out.writeUTFString(content.filePath)
          out.writeInt(content.pageCount)
          out.writeInt(content.toc.size)
          for (t in content.toc) {
            out.writeUTFString(t.title)
            out.writeInt(t.unitIndex)
          }
        }
      }
      out.flush()
    }
    if (file.exists()) file.delete()
    if (!tmp.renameTo(file)) {
      tmp.copyTo(file, overwrite = true)
      tmp.delete()
    }
  }

  fun read(file: File): LoadedBookContent? {
    if (!file.exists() || file.length() < 9L) return null
    return runCatching {
      DataInputStream(BufferedInputStream(file.inputStream(), 16 * 1024)).use { input ->
        val magic = input.readInt()
        val version = input.readInt()
        if (magic != MAGIC || version != VERSION) return null
        when (input.readByte().toInt()) {
          TYPE_REFLOWABLE -> {
            val totalChars = input.readLong()
            val chapterCount = input.readInt()
            if (chapterCount < 0 || chapterCount > 50_000) return null
            val chapters = ArrayList<BookChapter>(chapterCount.coerceAtMost(256))
            for (i in 0 until chapterCount) {
              val index = input.readInt()
              val title = input.readUTFString()
              val charCount = input.readLong()
              val startCharOffset = input.readLong()
              val paraCount = input.readInt()
              if (paraCount < 0 || paraCount > 500_000) return null
              val paragraphs = ArrayList<BookParagraph>(paraCount.coerceAtMost(512))
              for (j in 0 until paraCount) {
                val pIndex = input.readInt()
                val kindOrd = input.readByte().toInt().coerceIn(0, ParagraphKind.entries.lastIndex)
                val charOffsetInChapter = input.readInt()
                val text = input.readUTFString()
                paragraphs.add(
                  BookParagraph(
                    index = pIndex,
                    text = text,
                    kind = ParagraphKind.entries[kindOrd],
                    charOffsetInChapter = charOffsetInChapter,
                  )
                )
              }
              chapters.add(
                BookChapter(
                  index = index,
                  title = title,
                  paragraphs = paragraphs,
                  charCount = charCount,
                  startCharOffset = startCharOffset,
                )
              )
            }
            val tocCount = input.readInt()
            if (tocCount < 0 || tocCount > 50_000) return null
            val toc = ArrayList<TocEntry>(tocCount.coerceAtMost(256))
            for (k in 0 until tocCount) {
              val tTitle = input.readUTFString()
              val tUnit = input.readInt()
              toc.add(TocEntry(tTitle, tUnit))
            }
            LoadedBookContent.Reflowable(
              chapters = chapters,
              toc = toc,
              totalChars = totalChars,
            )
          }

          TYPE_PDF -> {
            val path = input.readUTFString()
            val pageCount = input.readInt()
            val tocCount = input.readInt()
            if (tocCount < 0 || tocCount > 50_000) return null
            val toc = ArrayList<TocEntry>(tocCount.coerceAtMost(256))
            for (k in 0 until tocCount) {
              val tTitle = input.readUTFString()
              val tUnit = input.readInt()
              toc.add(TocEntry(tTitle, tUnit))
            }
            LoadedBookContent.PdfDocumentContent(
              filePath = path,
              pageCount = pageCount,
              toc = toc,
            )
          }

          else -> null
        }
      }
    }.getOrNull()
  }

  private fun DataOutputStream.writeUTFString(value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    writeInt(bytes.size)
    write(bytes)
  }

  private fun DataInputStream.readUTFString(): String {
    val len = readInt()
    if (len == 0) return ""
    if (len < 0 || len > 20_000_000) throw IllegalStateException("Corrupt string length: $len")
    val bytes = ByteArray(len)
    readFully(bytes)
    return String(bytes, Charsets.UTF_8)
  }
}
