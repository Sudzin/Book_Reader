package com.example.reader.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.File

object PdfParser {

  fun parseForImport(file: File, fallbackTitle: String): Pair<ParsedBookMetadata, LoadedBookContent.PdfDocumentContent> {
    if (!file.exists() || file.length() == 0L) {
      throw BookParseException("PDF-файл пуст или недоступен.")
    }
    return try {
      ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { renderer ->
          val pageCount = renderer.pageCount
          if (pageCount <= 0) {
            throw BookParseException("PDF-файл не содержит страниц.")
          }
          val coverBytes = runCatching {
            renderer.openPage(0).use { page ->
              val targetW = 420
              val targetH = ((page.height.toFloat() / page.width.coerceAtLeast(1)) * targetW)
                .toInt()
                .coerceIn(200, 720)
              val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
              Canvas(bmp).drawColor(Color.WHITE)
              page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
              ByteArrayOutputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                bmp.recycle()
                out.toByteArray()
              }
            }
          }.getOrNull()
          val content = buildDescriptor(file, pageCount)
          val meta = ParsedBookMetadata(
            title = fallbackTitle,
            author = "PDF документ • $pageCount стр.",
            coverBytes = coverBytes,
            totalUnits = pageCount,
            totalChars = 0L,
          )
          meta to content
        }
      }
    } catch (e: BookParseException) {
      throw e
    } catch (e: SecurityException) {
      throw BookParseException("PDF-файл защищён паролем и не может быть открыт.", e)
    } catch (t: Throwable) {
      throw BookParseException("PDF-файл повреждён или имеет неподдерживаемый формат.", t)
    }
  }

  fun loadDescriptor(file: File): LoadedBookContent.PdfDocumentContent {
    if (!file.exists() || file.length() == 0L) {
      throw BookParseException("PDF-файл пуст или недоступен.")
    }
    val count = try {
      ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { renderer ->
          if (renderer.pageCount <= 0) throw BookParseException("PDF-файл не содержит страниц.")
          renderer.pageCount
        }
      }
    } catch (e: BookParseException) {
      throw e
    } catch (e: SecurityException) {
      throw BookParseException("PDF-файл защищён паролем и не может быть открыт.", e)
    } catch (t: Throwable) {
      throw BookParseException("PDF-файл повреждён или имеет неподдерживаемый формат.", t)
    }
    return buildDescriptor(file, count)
  }

  private fun buildDescriptor(file: File, count: Int): LoadedBookContent.PdfDocumentContent {
    val step = when {
      count <= 20 -> 1
      count <= 100 -> 5
      else -> 15
    }
    val toc = (0 until count step step).map { idx ->
      TocEntry(title = "Страница ${idx + 1}", unitIndex = idx)
    }
    return LoadedBookContent.PdfDocumentContent(
      filePath = file.absolutePath,
      pageCount = count,
      toc = toc,
    )
  }

  fun renderPageBitmap(filePath: String, pageIndex: Int, targetWidthPx: Int): Bitmap? {
    val file = File(filePath)
    if (!file.exists()) return null
    return runCatching {
      ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { renderer ->
          val safeIdx = pageIndex.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))
          renderer.openPage(safeIdx).use { page ->
            val width = targetWidthPx.coerceIn(400, 2200)
            val height = ((page.height.toFloat() / page.width.coerceAtLeast(1)) * width)
              .toInt()
              .coerceIn(300, 3200)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
          }
        }
      }
    }.getOrNull()
  }
}
