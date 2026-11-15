package com.example

import com.example.reader.engine.BookParseException
import com.example.reader.engine.EpubParser
import com.example.reader.engine.Fb2Parser
import com.example.reader.engine.ParagraphKind
import com.example.reader.engine.TxtParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BookParsersTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  private fun copyTestResource(resourceName: String): File {
    val outFile = tempFolder.newFile(resourceName)
    val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream(resourceName)) {
      "Missing test resource: $resourceName"
    }
    stream.use { input ->
      outFile.outputStream().use { output -> input.copyTo(output) }
    }
    return outFile
  }

  @Test
  fun `EpubParser parses small EPUB from test resources`() {
    val file = copyTestResource("sample.epub")
    val (meta, content) = EpubParser.parseForImport(file, "Fallback")

    assertEquals("Маленькая книга EPUB", meta.title)
    assertEquals("Иван Тургенев", meta.author)
    assertEquals(2, content.chapters.size)
    assertEquals("Глава 1. Лес", content.chapters[0].title)
    assertEquals("Глава 2. Поле", content.chapters[1].title)
    assertTrue(content.chapters[0].paragraphs.any { it.kind == ParagraphKind.QUOTE && it.text.contains("Небольшая цитата") })
    assertTrue(content.chapters[1].paragraphs.any { it.text.contains("фонарь") })
    assertTrue(content.totalChars > 50L)
    assertEquals(2, content.toc.size)
  }

  @Test
  fun `Fb2Parser parses small FB2 from test resources`() {
    val file = copyTestResource("sample.fb2")
    val (meta, content) = Fb2Parser.parseForImport(file, "Fallback")

    assertEquals("Тестовый рассказ", meta.title)
    assertEquals("Антон Чехов", meta.author)
    assertEquals(2, content.chapters.size)
    assertEquals("Глава 1. Утро", content.chapters[0].title)
    assertEquals("Глава 2. Вечер", content.chapters[1].title)
    assertTrue(content.chapters[0].paragraphs.any { it.kind == ParagraphKind.QUOTE && it.text.contains("Солнце взошло") })
    assertTrue(content.chapters[1].paragraphs.any { it.kind == ParagraphKind.QUOTE && it.text.contains("Цитата") })
    assertTrue(content.totalChars > 50L)
  }

  @Test
  fun `TxtParser parses small TXT from test resources`() {
    val file = copyTestResource("sample.txt")
    val (meta, content) = TxtParser.parseForImport(file, "Тестовый текст")

    assertEquals("Тестовый текст", meta.title)
    assertEquals(2, content.chapters.size)
    assertEquals("Глава 1. Начало пути", content.chapters[0].title)
    assertEquals("Глава 2. Встреча у моста", content.chapters[1].title)
    assertTrue(content.chapters[0].paragraphs.any { it.text.contains("саквояжем") })
    assertTrue(content.chapters[1].paragraphs.any { it.text.contains("мельник") })
    assertTrue(content.totalChars > 50L)
  }

  @Test
  fun `empty and corrupt files fail with clear BookParseException without crashing`() {
    val emptyEpub = tempFolder.newFile("empty.epub")
    assertThrowsParseError { EpubParser.parseForImport(emptyEpub, "Empty") }

    val garbageEpub = tempFolder.newFile("garbage.epub").apply { writeBytes(byteArrayOf(0x1, 0x2, 0x3, 0x4)) }
    assertThrowsParseError { EpubParser.parseForImport(garbageEpub, "Garbage") }

    val zipWithoutOpf = tempFolder.newFile("noopf.epub").apply {
      ZipOutputStream(outputStream()).use { zos ->
        zos.putNextEntry(ZipEntry("hello.txt"))
        zos.write("not an epub".toByteArray())
        zos.closeEntry()
      }
    }
    assertThrowsParseError { EpubParser.parseForImport(zipWithoutOpf, "NoOpf") }

    val emptyFb2 = tempFolder.newFile("empty.fb2")
    assertThrowsParseError { Fb2Parser.parseForImport(emptyFb2, "EmptyFb2") }

    val emptyBodyFb2 = tempFolder.newFile("nobody.fb2").apply {
      writeText("""<?xml version="1.0" encoding="utf-8"?><FictionBook><body/></FictionBook>""")
    }
    assertThrowsParseError { Fb2Parser.parseForImport(emptyBodyFb2, "NoBody") }

    val corruptFb2Zip = tempFolder.newFile("corrupt.fb2.zip").apply {
      writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x11, 0x22))
    }
    assertThrowsParseError { Fb2Parser.parseForImport(corruptFb2Zip, "CorruptZip") }

    val emptyTxt = tempFolder.newFile("empty.txt")
    assertThrowsParseError { TxtParser.parseForImport(emptyTxt, "EmptyTxt") }

    val blankTxt = tempFolder.newFile("blank.txt").apply { writeText("   \n\t\n  ") }
    assertThrowsParseError { TxtParser.parseForImport(blankTxt, "BlankTxt") }

    val binaryTxt = tempFolder.newFile("binary.txt").apply {
      writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x00, 0x00, 0x01, 0x02))
    }
    assertThrowsParseError { TxtParser.parseForImport(binaryTxt, "BinaryTxt") }
  }

  private fun assertThrowsParseError(block: () -> Unit) {
    try {
      block()
      throw AssertionError("Expected BookParseException to be thrown")
    } catch (e: BookParseException) {
      assertTrue("Error message must be non-blank", e.message.isNotBlank())
    }
  }
}
