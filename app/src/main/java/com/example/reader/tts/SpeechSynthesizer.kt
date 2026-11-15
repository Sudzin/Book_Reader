package com.example.reader.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

interface SpeechSynthesizer {
  interface Listener {
    fun onUtteranceStart(utteranceId: String)
    fun onUtteranceDone(utteranceId: String)
    fun onUtteranceError(utteranceId: String)
  }

  fun setListener(listener: Listener?)
  fun initialize(onReady: (Boolean) -> Unit)
  fun speak(text: String, utteranceId: String, speechRate: Float)
  fun setSpeechRate(speechRate: Float)
  fun stop()
  fun shutdown()
}

class AndroidSpeechSynthesizer(
  private val context: Context,
) : SpeechSynthesizer {

  @Volatile
  private var listener: SpeechSynthesizer.Listener? = null

  private var tts: TextToSpeech? = null

  @Volatile
  private var isReady: Boolean = false

  @Volatile
  private var isInitializing: Boolean = false

  private val readyCallbacks = mutableListOf<(Boolean) -> Unit>()
  private val chunkFinalIds = ConcurrentHashMap<String, String>()
  private val chunkFirstIds = ConcurrentHashMap<String, String>()
  private val lock = Any()

  override fun setListener(listener: SpeechSynthesizer.Listener?) {
    this.listener = listener
  }

  override fun initialize(onReady: (Boolean) -> Unit) {
    synchronized(lock) {
      if (isReady && tts != null) {
        onReady(true)
        return
      }
      readyCallbacks.add(onReady)
      if (isInitializing) return
      isInitializing = true
    }

    tts = TextToSpeech(context.applicationContext) { status ->
      val success = status == TextToSpeech.SUCCESS
      if (success) {
        tts?.setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        )
      }
      val callbacks = synchronized(lock) {
        isInitializing = false
        isReady = success
        val copy = readyCallbacks.toList()
        readyCallbacks.clear()
        copy
      }
      callbacks.forEach { it(success) }
    }.apply {
      setOnUtteranceProgressListener(object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
          val id = utteranceId ?: return
          val baseId = chunkFirstIds.remove(id) ?: return
          listener?.onUtteranceStart(baseId)
        }

        override fun onDone(utteranceId: String?) {
          val id = utteranceId ?: return
          val baseId = chunkFinalIds.remove(id) ?: return
          listener?.onUtteranceDone(baseId)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
          val id = utteranceId ?: return
          val baseId = extractBaseId(id)
          chunkFirstIds.entries.removeIf { it.value == baseId }
          chunkFinalIds.entries.removeIf { it.value == baseId }
          listener?.onUtteranceError(baseId)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
          val id = utteranceId ?: return
          val baseId = extractBaseId(id)
          chunkFirstIds.entries.removeIf { it.value == baseId }
          chunkFinalIds.entries.removeIf { it.value == baseId }
          listener?.onUtteranceError(baseId)
        }
      })
    }
  }

  override fun speak(text: String, utteranceId: String, speechRate: Float) {
    val engine = tts ?: return
    if (!isReady) return

    val cleanText = text.trim()
    if (cleanText.isEmpty()) {
      listener?.onUtteranceStart(utteranceId)
      listener?.onUtteranceDone(utteranceId)
      return
    }

    val hasCyrillic = cleanText.any { it in '\u0400'..'\u04FF' }
    val targetLocale = if (hasCyrillic) Locale("ru", "RU") else Locale.US
    val langSupport = runCatching { engine.isLanguageAvailable(targetLocale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
    if (langSupport >= TextToSpeech.LANG_AVAILABLE) {
      engine.language = targetLocale
    }
    engine.setSpeechRate(speechRate.coerceIn(0.7f, 2.0f))

    val maxInput = runCatching { TextToSpeech.getMaxSpeechInputLength() }.getOrDefault(3500).coerceIn(500, 3800)
    val chunks = splitIntoSpeechChunks(cleanText, maxInput)

    chunkFirstIds.clear()
    chunkFinalIds.clear()
    val firstChunkId = "$utteranceId#0"
    val lastChunkId = "$utteranceId#${chunks.lastIndex}"
    chunkFirstIds[firstChunkId] = utteranceId
    chunkFinalIds[lastChunkId] = utteranceId

    chunks.forEachIndexed { idx, chunk ->
      val mode = if (idx == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
      val chunkUtteranceId = "$utteranceId#$idx"
      val params = Bundle()
      engine.speak(chunk, mode, params, chunkUtteranceId)
    }
  }

  override fun setSpeechRate(speechRate: Float) {
    tts?.setSpeechRate(speechRate.coerceIn(0.7f, 2.0f))
  }

  override fun stop() {
    chunkFirstIds.clear()
    chunkFinalIds.clear()
    runCatching { tts?.stop() }
  }

  override fun shutdown() {
    chunkFirstIds.clear()
    chunkFinalIds.clear()
    synchronized(lock) {
      isReady = false
      isInitializing = false
      readyCallbacks.clear()
    }
    runCatching {
      tts?.stop()
      tts?.shutdown()
    }
    tts = null
  }

  private fun extractBaseId(chunkUtteranceId: String): String {
    val sep = chunkUtteranceId.lastIndexOf('#')
    return if (sep > 0) chunkUtteranceId.substring(0, sep) else chunkUtteranceId
  }

  private fun splitIntoSpeechChunks(text: String, maxLen: Int): List<String> {
    if (text.length <= maxLen) return listOf(text)
    val result = mutableListOf<String>()
    var cursor = 0
    while (cursor < text.length) {
      val endLimit = (cursor + maxLen).coerceAtMost(text.length)
      if (endLimit == text.length) {
        result.add(text.substring(cursor))
        break
      }
      var splitAt = -1
      for (i in endLimit downTo (cursor + maxLen / 2)) {
        val ch = text[i - 1]
        if (ch == '.' || ch == '!' || ch == '?' || ch == '\n') {
          splitAt = i
          break
        }
      }
      if (splitAt <= cursor) {
        splitAt = text.lastIndexOf(' ', endLimit - 1)
      }
      if (splitAt <= cursor) {
        splitAt = endLimit
      }
      val slice = text.substring(cursor, splitAt).trim()
      if (slice.isNotEmpty()) {
        result.add(slice)
      }
      cursor = splitAt
      while (cursor < text.length && text[cursor].isWhitespace()) cursor++
    }
    return result.ifEmpty { listOf(text.take(maxLen)) }
  }
}
