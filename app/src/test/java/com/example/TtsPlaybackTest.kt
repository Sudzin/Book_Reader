package com.example

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.BookEntity
import com.example.data.local.ReaderPreferences
import com.example.reader.engine.BookChapter
import com.example.reader.engine.BookParagraph
import com.example.reader.engine.ChapterMetricsBuilder
import com.example.reader.engine.Locator
import com.example.reader.engine.ParagraphKind
import com.example.reader.engine.TocEntry
import com.example.reader.tts.SpeechSynthesizer
import com.example.reader.tts.TtsPlaybackController
import com.example.reader.tts.TtsPlaybackService
import com.example.reader.tts.TtsPlaybackState
import com.example.reader.tts.TtsProgressSink
import com.example.reader.tts.TtsServiceHost
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ListayTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TtsPlaybackTest {

  @get:Rule
  val composeRule = createComposeRule()

  private class FakeSynthesizer : SpeechSynthesizer {
    private var activeListener: SpeechSynthesizer.Listener? = null
    val spokenUtterances = mutableListOf<Triple<String, String, Float>>()
    var stopCount = 0
    var lastSpeed = 1.0f

    override fun setListener(listener: SpeechSynthesizer.Listener?) {
      this.activeListener = listener
    }

    override fun initialize(onReady: (Boolean) -> Unit) {
      onReady(true)
    }

    override fun speak(text: String, utteranceId: String, speechRate: Float) {
      lastSpeed = speechRate
      spokenUtterances.add(Triple(text, utteranceId, speechRate))
      activeListener?.onUtteranceStart(utteranceId)
    }

    override fun setSpeechRate(speechRate: Float) {
      lastSpeed = speechRate
    }

    override fun stop() {
      stopCount++
    }

    override fun shutdown() = Unit

    fun finishLastUtterance() {
      val last = spokenUtterances.lastOrNull() ?: return
      activeListener?.onUtteranceDone(last.second)
    }
  }

  private fun sampleContent() = ChapterMetricsBuilder.buildReflowable(
    rawChapters = listOf(
      BookChapter(
        index = 0,
        title = "Глава 1",
        paragraphs = listOf(
          BookParagraph(0, "Первый абзац первой главы.", ParagraphKind.BODY),
          BookParagraph(1, "   ", ParagraphKind.BODY),
          BookParagraph(2, "Второй читаемый абзац первой главы.", ParagraphKind.BODY),
        ),
      ),
      BookChapter(
        index = 1,
        title = "Глава 2",
        paragraphs = listOf(
          BookParagraph(0, "Первый абзац второй главы.", ParagraphKind.BODY),
          BookParagraph(1, "Финальный абзац книги.", ParagraphKind.BODY),
        ),
      ),
    ),
    rawToc = listOf(TocEntry("Глава 1", 0), TocEntry("Глава 2", 1)),
  )

  private fun sampleBook() = BookEntity(
    id = 7L,
    title = "Тестовая книга",
    author = "Автор Теста",
    format = "EPUB",
    localFilePath = "/tmp/book.epub",
    contentHash = "hash7",
  )

  @Test
  fun `manifest declares mediaPlayback service, notification permission, and zero network permissions`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val pkgInfo = context.packageManager.getPackageInfo(
      context.packageName,
      PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES,
    )
    val permissions = pkgInfo.requestedPermissions?.toSet().orEmpty()
    assertTrue(Manifest.permission.FOREGROUND_SERVICE in permissions)
    assertTrue(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK in permissions)
    assertTrue(Manifest.permission.POST_NOTIFICATIONS in permissions)
    assertFalse("INTERNET must not be requested", Manifest.permission.INTERNET in permissions)
    assertFalse("ACCESS_NETWORK_STATE must not be requested", Manifest.permission.ACCESS_NETWORK_STATE in permissions)

    val serviceInfo = context.packageManager.getServiceInfo(
      ComponentName(context, TtsPlaybackService::class.java),
      0,
    )
    assertFalse(serviceInfo.exported)
    assertTrue((serviceInfo.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) != 0)
  }

  @Test
  fun `controller auto-advances across paragraphs and chapters while persisting locators`() {
    val synth = FakeSynthesizer()
    val savedLocators = mutableListOf<Locator>()
    var serviceStarted = 0
    var stoppedCallbacks = 0

    val controller = TtsPlaybackController(
      synthesizer = synth,
      progressSink = object : TtsProgressSink {
        override fun saveProgress(
          bookId: Long,
          locator: Locator,
          progressPercent: Float,
          totalUnits: Int,
          totalChars: Long,
        ) {
          savedLocators.add(locator)
        }
      },
      startForegroundServiceAction = { serviceStarted++ },
    )
    controller.attachServiceHost(object : TtsServiceHost {
      override fun requestAudioFocus(): Boolean = true
      override fun abandonAudioFocus() = Unit
      override fun onPlaybackStateUpdated(state: TtsPlaybackState) = Unit
      override fun onPlaybackStopped() {
        stoppedCallbacks++
      }
    })

    val started = controller.startBookPlayback(
      book = sampleBook(),
      content = sampleContent(),
      startChapterIndex = 0,
      startParagraphIndex = 0,
      speechSpeed = 1.2f,
    )
    assertTrue(started)
    assertEquals(1, serviceStarted)
    assertEquals(1.2f, synth.lastSpeed, 0.01f)
    assertEquals(0, controller.state.value.chapterIndex)
    assertEquals(0, controller.state.value.activeParagraphIndex)

    // Utterance 0 done -> skips blank paragraph 1 -> jumps to paragraph 2
    synth.finishLastUtterance()
    assertEquals(0, controller.state.value.chapterIndex)
    assertEquals(2, controller.state.value.activeParagraphIndex)

    // End of chapter 0 -> automatically advances to chapter 1 paragraph 0
    synth.finishLastUtterance()
    assertEquals(1, controller.state.value.chapterIndex)
    assertEquals(0, controller.state.value.activeParagraphIndex)

    // Previous paragraph crosses chapter boundary back to chapter 0 paragraph 2
    assertTrue(controller.previousParagraph())
    assertEquals(0, controller.state.value.chapterIndex)
    assertEquals(2, controller.state.value.activeParagraphIndex)

    // Next paragraph moves back to chapter 1 paragraph 0
    assertTrue(controller.nextParagraph())
    assertEquals(1, controller.state.value.chapterIndex)
    assertEquals(0, controller.state.value.activeParagraphIndex)

    // Incoming call pauses immediately and keeps current locator
    controller.onIncomingCallOrFocusLost()
    assertTrue(controller.state.value.isActive)
    assertFalse(controller.state.value.isPlaying)
    assertTrue(controller.state.value.isPaused)
    assertEquals(1, controller.state.value.chapterIndex)
    assertEquals(0, controller.state.value.activeParagraphIndex)

    // Resume continues from saved position and stops at end of book
    controller.resume()
    assertTrue(controller.state.value.isPlaying)
    synth.finishLastUtterance() // -> ch1 para1
    assertEquals(1, controller.state.value.activeParagraphIndex)
    synth.finishLastUtterance() // -> end of book -> stops cleanly
    assertFalse(controller.state.value.isActive)
    assertTrue(stoppedCallbacks >= 1)
    assertTrue(savedLocators.contains(Locator(0, 2, 0)))
    assertTrue(savedLocators.contains(Locator(1, 0, 0)))
    assertTrue(savedLocators.contains(Locator(1, 1, 0)))
  }

  @Test
  fun `TtsPlaybackService builds notification with prev, play-pause, next, and stop actions`() {
    val controller = Robolectric.buildService(TtsPlaybackService::class.java).create()
    val service = controller.get()
    try {
      val notification = service.buildNotification(
        TtsPlaybackState(
          isActive = true,
          isPlaying = true,
          isPaused = false,
          bookId = 1L,
          bookTitle = "Война и мир",
          bookAuthor = "Лев Толстой",
          chapterTitle = "Том 1 • Глава 1",
          activeParagraphIndex = 4,
          speed = 1.25f,
        )
      )
      assertNotNull(notification)
      assertEquals(4, notification.actions.size)
      val titles = notification.actions.map { it.title.toString() }
      assertEquals(
        listOf("Назад на абзац", "Пауза", "Вперёд на абзац", "Стоп"),
        titles,
      )

      val pausedNotification = service.buildNotification(
        TtsPlaybackState(
          isActive = true,
          isPlaying = false,
          isPaused = true,
          bookId = 1L,
          bookTitle = "Война и мир",
          chapterTitle = "Том 1 • Глава 1",
          activeParagraphIndex = 4,
          speed = 1.0f,
        )
      )
      assertEquals("Продолжить", pausedNotification.actions[1].title.toString())
    } finally {
      controller.destroy()
    }
  }

  @Test
  fun `SettingsScreen displays TTS speech speed section and triggers callback`() {
    var selectedSpeed = 1.0f
    composeRule.setContent {
      ListayTheme {
        SettingsScreen(
          preferences = ReaderPreferences(ttsSpeed = 1.0f),
          bookCount = 2,
          quoteCount = 1,
          onSelectTheme = {},
          onSelectReadingMode = {},
          onToggleDynamicColor = {},
          onToggleVolumePaging = {},
          onToggleKeepScreenOn = {},
          onTtsSpeedChanged = { selectedSpeed = it },
        )
      }
    }

    composeRule.onNodeWithTag("settings_tts_speed_slider").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("Скорость речи").assertIsDisplayed()
    composeRule.onNodeWithTag("settings_tts_preset_15").performScrollTo().performClick()
    assertEquals(1.5f, selectedSpeed, 0.01f)
  }
}
