package com.example.reader.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest

data class ExtractedImage(
  val file: File,
  val width: Int,
  val height: Int,
)

object BookImageExtractor {
  fun probeDimensions(bytes: ByteArray): Pair<Int, Int>? {
    if (bytes.isEmpty()) return null
    val head = String(bytes, 0, bytes.size.coerceAtMost(64), Charsets.ISO_8859_1).trimStart().lowercase()
    if (head.startsWith("<svg") || head.startsWith("<?xml")) return null
    return runCatching {
      val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
      if (opts.outWidth > 0 && opts.outHeight > 0) {
        opts.outWidth to opts.outHeight
      } else {
        null
      }
    }.getOrNull()
  }

  fun saveExtractedImage(
    outputDir: File,
    key: String,
    bytes: ByteArray,
    preferredExt: String? = null,
  ): ExtractedImage? {
    val dims = probeDimensions(bytes) ?: return null
    if (!outputDir.exists()) outputDir.mkdirs()

    val ext = resolveExtension(preferredExt, bytes)
    val hash = sha256Hex(key).take(20)
    val outFile = File(outputDir, "img_$hash.$ext")
    if (!outFile.exists() || outFile.length() == 0L) {
      val tmp = File(outputDir, "img_$hash.$ext.tmp")
      runCatching {
        tmp.writeBytes(bytes)
        if (outFile.exists()) outFile.delete()
        if (!tmp.renameTo(outFile)) {
          tmp.copyTo(outFile, overwrite = true)
          tmp.delete()
        }
      }.getOrElse {
        runCatching { tmp.delete() }
        return null
      }
    }
    return ExtractedImage(
      file = outFile,
      width = dims.first,
      height = dims.second,
    )
  }

  fun decodeSampledBitmap(
    path: String,
    maxWidthPx: Int = 1440,
    maxHeightPx: Int = 1800,
  ): Bitmap? {
    val file = File(path)
    if (!file.exists() || file.length() == 0L) return null
    return runCatching {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(path, bounds)
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

      var sample = 1
      while (
        bounds.outWidth / sample > maxWidthPx ||
        bounds.outHeight / sample > maxHeightPx
      ) {
        sample *= 2
      }
      val decodeOpts = BitmapFactory.Options().apply {
        inSampleSize = sample
      }
      BitmapFactory.decodeFile(path, decodeOpts)
    }.getOrNull()
  }

  private fun resolveExtension(preferredExt: String?, bytes: ByteArray): String {
    val cleaned = preferredExt?.lowercase()?.trim()?.trimStart('.')
    if (cleaned in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")) {
      return if (cleaned == "jpeg") "jpg" else cleaned!!
    }
    if (bytes.size >= 8 &&
      bytes[0] == 0x89.toByte() &&
      bytes[1] == 0x50.toByte() &&
      bytes[2] == 0x4E.toByte() &&
      bytes[3] == 0x47.toByte()
    ) {
      return "png"
    }
    if (bytes.size >= 3 &&
      bytes[0] == 0x47.toByte() &&
      bytes[1] == 0x49.toByte() &&
      bytes[2] == 0x46.toByte()
    ) {
      return "gif"
    }
    if (bytes.size >= 12 &&
      bytes[0] == 0x52.toByte() &&
      bytes[1] == 0x49.toByte() &&
      bytes[2] == 0x46.toByte() &&
      bytes[3] == 0x46.toByte() &&
      bytes[8] == 0x57.toByte() &&
      bytes[9] == 0x45.toByte() &&
      bytes[10] == 0x42.toByte() &&
      bytes[11] == 0x50.toByte()
    ) {
      return "webp"
    }
    return "jpg"
  }

  private fun sha256Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
  }
}
