package com.example.reader.engine

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class TtsState(
  val isReady: Boolean = false,
  val isSpeaking: Boolean = false,
  val activeParagraphIndex: Int = -1,
)

class TtsController(context: Context) {
  private val _state = MutableStateFlow(TtsState())
  val state: StateFlow<TtsState> = _state.asStateFlow()

  private var tts: TextToSpeech? = null
  private var paragraphs: List<BookParagraph> = emptyList()
  private var currentCursor: Int = 0
  private var speed: Float = 1.0f
  private var onChapterFinished: (() -> Unit)? = null

  init {
    tts = TextToSpeech(context.applicationContext) { status ->
      if (status == TextToSpeech.SUCCESS) {
        _state.value = _state.value.copy(isReady = true)
      }
    }.apply {
      setOnUtteranceProgressListener(object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
          val idx = utteranceId?.toIntOrNull() ?: return
          _state.value = _state.value.copy(isSpeaking = true, activeParagraphIndex = idx)
        }

        override fun onDone(utteranceId: String?) {
          val finishedIdx = utteranceId?.toIntOrNull() ?: return
          if (!_state.value.isSpeaking) return
          val nextPos = paragraphs.indexOfFirst { it.index > finishedIdx }
          if (nextPos >= 0) {
            currentCursor = nextPos
            speakCurrent()
          } else {
            _state.value = _state.value.copy(isSpeaking = false, activeParagraphIndex = -1)
            onChapterFinished?.invoke()
          }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
          _state.value = _state.value.copy(isSpeaking = false)
        }
      })
    }
  }

  fun setSpeed(newSpeed: Float) {
    speed = newSpeed.coerceIn(0.7f, 2.0f)
    tts?.setSpeechRate(speed)
  }

  fun startSpeaking(
    chapterParagraphs: List<BookParagraph>,
    startFromParagraphIndex: Int,
    speechSpeed: Float,
    onFinished: () -> Unit,
  ) {
    if (!_state.value.isReady || chapterParagraphs.isEmpty()) return
    paragraphs = chapterParagraphs
    speed = speechSpeed.coerceIn(0.7f, 2.0f)
    onChapterFinished = onFinished
    val startPos = chapterParagraphs.indexOfFirst { it.index >= startFromParagraphIndex }.coerceAtLeast(0)
    currentCursor = startPos
    _state.value = _state.value.copy(isSpeaking = true)
    speakCurrent()
  }

  fun stop() {
    _state.value = _state.value.copy(isSpeaking = false, activeParagraphIndex = -1)
    tts?.stop()
  }

  fun release() {
    stop()
    tts?.shutdown()
    tts = null
  }

  private fun speakCurrent() {
    val engine = tts ?: return
    val item = paragraphs.getOrNull(currentCursor) ?: return
    val hasCyrillic = item.text.any { it in '\u0400'..'\u04FF' }
    val targetLocale = if (hasCyrillic) Locale("ru", "RU") else Locale.US
    val langSupport = engine.isLanguageAvailable(targetLocale)
    if (langSupport >= TextToSpeech.LANG_AVAILABLE) {
      engine.language = targetLocale
    }
    engine.setSpeechRate(speed)
    val params = Bundle()
    engine.speak(item.text, TextToSpeech.QUEUE_FLUSH, params, item.index.toString())
  }
}
