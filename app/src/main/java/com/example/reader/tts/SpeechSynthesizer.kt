package com.example.reader.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.text.BreakIterator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class TtsVoiceOption(
  val name: String,
  val displayName: String,
  val localeTag: String,
  val isNetworkRequired: Boolean,
  val quality: Int,
) {
  val qualityLabel: String
    get() = when {
      quality >= Voice.QUALITY_VERY_HIGH -> "Очень высокое"
      quality >= Voice.QUALITY_HIGH -> "Высокое"
      quality >= Voice.QUALITY_NORMAL -> "Обычное"
      quality >= Voice.QUALITY_LOW -> "Низкое"
      else -> "Базовое"
    }
}

interface SpeechSynthesizer {
  interface Listener {
    fun onUtteranceStart(utteranceId: String)
    fun onUtteranceDone(utteranceId: String)
    fun onUtteranceError(utteranceId: String, message: String)
  }

  fun setListener(listener: Listener?)
  fun initialize(onReady: (Boolean) -> Unit)
  fun speak(
    text: String,
    utteranceId: String,
    speechRate: Float,
    pitch: Float = 1.0f,
    voiceName: String? = null,
  )
  fun setSpeechRate(speechRate: Float)
  fun setPitch(pitch: Float)
  fun setVoice(voiceName: String?)
  fun getRussianVoices(): List<TtsVoiceOption>
  fun stop()
  fun shutdown()
}

interface TtsVoiceGateway {
  fun loadRussianVoices(onResult: (Result<List<TtsVoiceOption>>) -> Unit)
  fun speakSample(
    text: String,
    speechRate: Float,
    pitch: Float,
    voiceName: String?,
    onFinished: (Result<Unit>) -> Unit,
  )
  fun stopSample()
  fun release()
}

class AndroidTtsVoiceGateway(
  context: Context,
) : TtsVoiceGateway {
  private val synth = AndroidSpeechSynthesizer(context.applicationContext)
  private val sampleCounter = AtomicInteger(0)

  @Volatile
  private var activeSampleId: String? = null

  @Volatile
  private var activeCallback: ((Result<Unit>) -> Unit)? = null

  init {
    synth.setListener(object : SpeechSynthesizer.Listener {
      override fun onUtteranceStart(utteranceId: String) = Unit

      override fun onUtteranceDone(utteranceId: String) {
        if (utteranceId == activeSampleId) {
          val cb = activeCallback
          activeSampleId = null
          activeCallback = null
          cb?.invoke(Result.success(Unit))
        }
      }

      override fun onUtteranceError(utteranceId: String, message: String) {
        if (utteranceId == activeSampleId) {
          val cb = activeCallback
          activeSampleId = null
          activeCallback = null
          cb?.invoke(Result.failure(IllegalStateException(message)))
        }
      }
    })
  }

  override fun loadRussianVoices(onResult: (Result<List<TtsVoiceOption>>) -> Unit) {
    synth.initialize { ready ->
      if (!ready) {
        onResult(Result.failure(IllegalStateException(MSG_ENGINE_UNAVAILABLE)))
      } else {
        onResult(Result.success(synth.getRussianVoices()))
      }
    }
  }

  override fun speakSample(
    text: String,
    speechRate: Float,
    pitch: Float,
    voiceName: String?,
    onFinished: (Result<Unit>) -> Unit,
  ) {
    stopSample()
    val sampleId = "sample_${sampleCounter.incrementAndGet()}"
    activeSampleId = sampleId
    activeCallback = onFinished
    synth.initialize { ready ->
      if (activeSampleId != sampleId) return@initialize
      if (!ready) {
        val cb = activeCallback
        activeSampleId = null
        activeCallback = null
        cb?.invoke(Result.failure(IllegalStateException(MSG_ENGINE_UNAVAILABLE)))
      } else {
        synth.speak(
          text = text,
          utteranceId = sampleId,
          speechRate = speechRate,
          pitch = pitch,
          voiceName = voiceName,
        )
      }
    }
  }

  override fun stopSample() {
    activeSampleId = null
    activeCallback = null
    synth.stop()
  }

  override fun release() {
    stopSample()
    synth.shutdown()
  }

  companion object {
    const val MSG_ENGINE_UNAVAILABLE =
      "Выбранный движок синтеза речи не отвечает или не установлен. Проверьте системные настройки синтеза речи."
  }
}

internal object SentenceSplitter {
  fun splitIntoSentences(text: String, maxChunkLen: Int = 3500): List<String> {
    val normalized = text.replace("\r\n", "\n").trim()
    if (normalized.isEmpty()) return emptyList()

    val paragraphs = normalized.split('\n')
    val rawSentences = mutableListOf<String>()

    for (para in paragraphs) {
      val trimmedPara = para.trim()
      if (trimmedPara.isEmpty()) continue
      val iterator = BreakIterator.getSentenceInstance(Locale.forLanguageTag("ru-RU"))
      iterator.setText(trimmedPara)
      var start = iterator.first()
      var end = iterator.next()
      while (end != BreakIterator.DONE) {
        val sentence = trimmedPara.substring(start, end).trim()
        if (sentence.isNotEmpty()) {
          if (sentence.any { it.isLetterOrDigit() } || rawSentences.isEmpty()) {
            rawSentences.add(sentence)
          } else {
            val prev = rawSentences.removeAt(rawSentences.lastIndex)
            rawSentences.add("$prev $sentence")
          }
        }
        start = end
        end = iterator.next()
      }
    }

    if (rawSentences.isEmpty()) return listOf(normalized.take(maxChunkLen))

    val bounded = mutableListOf<String>()
    for (sentence in rawSentences) {
      if (sentence.length <= maxChunkLen) {
        bounded.add(sentence)
      } else {
        bounded.addAll(splitLongSentence(sentence, maxChunkLen))
      }
    }
    return bounded.ifEmpty { listOf(normalized.take(maxChunkLen)) }
  }

  private fun splitLongSentence(sentence: String, maxLen: Int): List<String> {
    val result = mutableListOf<String>()
    var cursor = 0
    while (cursor < sentence.length) {
      val endLimit = (cursor + maxLen).coerceAtMost(sentence.length)
      if (endLimit == sentence.length) {
        val tail = sentence.substring(cursor).trim()
        if (tail.isNotEmpty()) result.add(tail)
        break
      }
      var splitAt = -1
      for (i in endLimit downTo (cursor + maxLen / 2)) {
        val ch = sentence[i - 1]
        if (ch == ';' || ch == ',' || ch == ':' || ch == '—' || ch == '-') {
          splitAt = i
          break
        }
      }
      if (splitAt <= cursor) {
        splitAt = sentence.lastIndexOf(' ', endLimit - 1)
      }
      if (splitAt <= cursor) {
        splitAt = endLimit
      }
      val slice = sentence.substring(cursor, splitAt).trim()
      if (slice.isNotEmpty()) {
        result.add(slice)
      }
      cursor = splitAt
      while (cursor < sentence.length && sentence[cursor].isWhitespace()) cursor++
    }
    return result
  }
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

  @Volatile
  private var currentSpeechRate: Float = 1.0f

  @Volatile
  private var currentPitch: Float = 1.0f

  @Volatile
  private var currentVoiceName: String? = null

  private val readyCallbacks = mutableListOf<(Boolean) -> Unit>()
  private val chunkFirstIds = ConcurrentHashMap<String, String>()
  private val chunkFinalIds = ConcurrentHashMap<String, String>()
  private val activeBaseUtterance = AtomicInteger(0)

  @Volatile
  private var pendingUtteranceId: String? = null

  private val mainHandler = Handler(Looper.getMainLooper())
  private val lock = Any()

  private var initTimeoutRunnable: Runnable? = null
  private var responseTimeoutRunnable: Runnable? = null

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

    scheduleInitTimeout()

    val created = runCatching {
      TextToSpeech(context.applicationContext) { status ->
        cancelInitTimeout()
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
            val baseId = extractBaseId(id)
            if (baseId == pendingUtteranceId) {
              cancelResponseTimeout()
            }
            val firstBaseId = chunkFirstIds.remove(id) ?: return
            listener?.onUtteranceStart(firstBaseId)
          }

          override fun onDone(utteranceId: String?) {
            val id = utteranceId ?: return
            val baseId = extractBaseId(id)
            val finalBaseId = chunkFinalIds.remove(id)
            if (finalBaseId != null) {
              if (finalBaseId == pendingUtteranceId) {
                pendingUtteranceId = null
                cancelResponseTimeout()
              }
              listener?.onUtteranceDone(finalBaseId)
            } else if (baseId == pendingUtteranceId && !id.contains("#p")) {
              // Arm watchdog between sentences until the next sentence starts.
              scheduleResponseTimeout(baseId)
            }
          }

          @Deprecated("Deprecated in Java")
          override fun onError(utteranceId: String?) {
            val id = utteranceId ?: return
            handleUtteranceFailure(extractBaseId(id), MSG_SYNTHESIS_ERROR)
          }

          override fun onError(utteranceId: String?, errorCode: Int) {
            val id = utteranceId ?: return
            val detail = when (errorCode) {
              TextToSpeech.ERROR_NETWORK, TextToSpeech.ERROR_NETWORK_TIMEOUT ->
                "Онлайн-голос не отвечает. Проверьте сеть или выберите офлайн-голос."
              TextToSpeech.ERROR_NOT_INSTALLED_YET ->
                "Выбранный голос ещё не загружен на устройство."
              TextToSpeech.ERROR_SERVICE ->
                "Выбранный движок синтеза речи не отвечает. Попробуйте сменить движок в системных настройках."
              else -> MSG_SYNTHESIS_ERROR
            }
            handleUtteranceFailure(extractBaseId(id), detail)
          }
        })
      }
    }.getOrNull()

    if (created == null) {
      cancelInitTimeout()
      val callbacks = synchronized(lock) {
        isInitializing = false
        isReady = false
        val copy = readyCallbacks.toList()
        readyCallbacks.clear()
        copy
      }
      callbacks.forEach { it(false) }
    } else {
      tts = created
    }
  }

  override fun speak(
    text: String,
    utteranceId: String,
    speechRate: Float,
    pitch: Float,
    voiceName: String?,
  ) {
    val engine = tts
    if (engine == null || !isReady) {
      listener?.onUtteranceError(utteranceId, MSG_ENGINE_UNRESPONSIVE)
      return
    }

    val cleanText = text.trim()
    if (cleanText.isEmpty()) {
      listener?.onUtteranceStart(utteranceId)
      listener?.onUtteranceDone(utteranceId)
      return
    }

    currentSpeechRate = speechRate.coerceIn(0.7f, 1.3f)
    currentPitch = pitch.coerceIn(0.7f, 1.3f)
    currentVoiceName = voiceName

    val hasCyrillic = cleanText.any { it in '\u0400'..'\u04FF' }
    applyEngineVoiceAndLocale(engine, voiceName, preferRussian = hasCyrillic)
    runCatching {
      engine.setSpeechRate(currentSpeechRate)
      engine.setPitch(currentPitch)
    }

    val maxInput = runCatching { TextToSpeech.getMaxSpeechInputLength() }
      .getOrDefault(3500)
      .coerceIn(500, 3800)
    val sentences = SentenceSplitter.splitIntoSentences(cleanText, maxInput)
    if (sentences.isEmpty()) {
      listener?.onUtteranceStart(utteranceId)
      listener?.onUtteranceDone(utteranceId)
      return
    }

    chunkFirstIds.clear()
    chunkFinalIds.clear()
    val firstSentenceId = "$utteranceId#s0"
    val lastSentenceId = "$utteranceId#s${sentences.lastIndex}"
    chunkFirstIds[firstSentenceId] = utteranceId
    chunkFinalIds[lastSentenceId] = utteranceId

    activeBaseUtterance.incrementAndGet()
    pendingUtteranceId = utteranceId
    scheduleResponseTimeout(utteranceId)

    sentences.forEachIndexed { idx, sentence ->
      val mode = if (idx == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
      val sentenceId = "$utteranceId#s$idx"
      val status = runCatching {
        engine.speak(sentence, mode, Bundle(), sentenceId)
      }.getOrDefault(TextToSpeech.ERROR)

      if (status == TextToSpeech.ERROR) {
        handleUtteranceFailure(utteranceId, MSG_ENGINE_UNRESPONSIVE)
        return
      }

      if (idx < sentences.lastIndex) {
        runCatching {
          engine.playSilentUtterance(SENTENCE_PAUSE_MS, TextToSpeech.QUEUE_ADD, "$utteranceId#p$idx")
        }
      }
    }
  }

  override fun setSpeechRate(speechRate: Float) {
    currentSpeechRate = speechRate.coerceIn(0.7f, 1.3f)
    runCatching { tts?.setSpeechRate(currentSpeechRate) }
  }

  override fun setPitch(pitch: Float) {
    currentPitch = pitch.coerceIn(0.7f, 1.3f)
    runCatching { tts?.setPitch(currentPitch) }
  }

  override fun setVoice(voiceName: String?) {
    currentVoiceName = voiceName
    val engine = tts ?: return
    if (!isReady) return
    applyEngineVoiceAndLocale(engine, voiceName, preferRussian = true)
  }

  override fun getRussianVoices(): List<TtsVoiceOption> {
    val engine = tts ?: return emptyList()
    if (!isReady) return emptyList()
    val rawVoices = runCatching { engine.voices?.toList().orEmpty() }.getOrDefault(emptyList())
    return rawVoices
      .asSequence()
      .filter { voice -> voice.locale?.language.equals("ru", ignoreCase = true) }
      .map { voice ->
        TtsVoiceOption(
          name = voice.name,
          displayName = formatVoiceDisplayName(voice),
          localeTag = voice.locale?.toLanguageTag().orEmpty(),
          isNetworkRequired = voice.isNetworkConnectionRequired,
          quality = voice.quality,
        )
      }
      .sortedWith(
        compareBy<TtsVoiceOption> { it.isNetworkRequired }
          .thenByDescending { it.quality }
          .thenBy { it.displayName }
      )
      .toList()
  }

  override fun stop() {
    pendingUtteranceId = null
    cancelResponseTimeout()
    chunkFirstIds.clear()
    chunkFinalIds.clear()
    runCatching { tts?.stop() }
  }

  override fun shutdown() {
    cancelInitTimeout()
    cancelResponseTimeout()
    pendingUtteranceId = null
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

  private fun applyEngineVoiceAndLocale(
    engine: TextToSpeech,
    voiceName: String?,
    preferRussian: Boolean,
  ) {
    val voices = runCatching { engine.voices?.toList().orEmpty() }.getOrDefault(emptyList())
    if (!voiceName.isNullOrBlank()) {
      val selected = voices.firstOrNull { it.name == voiceName }
      if (selected != null) {
        val result = runCatching { engine.setVoice(selected) }.getOrDefault(TextToSpeech.ERROR)
        if (result != TextToSpeech.ERROR) return
      }
    }

    if (preferRussian) {
      val offlineRu = voices
        .filter {
          it.locale?.language.equals("ru", ignoreCase = true) &&
            !it.isNetworkConnectionRequired &&
            it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
        }
        .maxByOrNull { it.quality }
      if (offlineRu != null && runCatching { engine.setVoice(offlineRu) }.getOrDefault(TextToSpeech.ERROR) != TextToSpeech.ERROR) {
        return
      }
    }

    val targetLocale = if (preferRussian) Locale.forLanguageTag("ru-RU") else Locale.US
    val langSupport = runCatching { engine.isLanguageAvailable(targetLocale) }
      .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
    if (langSupport >= TextToSpeech.LANG_AVAILABLE) {
      runCatching { engine.language = targetLocale }
    }
  }

  private fun handleUtteranceFailure(baseId: String, message: String) {
    if (baseId == pendingUtteranceId) {
      pendingUtteranceId = null
    }
    cancelResponseTimeout()
    chunkFirstIds.entries.removeIf { it.value == baseId }
    chunkFinalIds.entries.removeIf { it.value == baseId }
    listener?.onUtteranceError(baseId, message)
  }

  private fun scheduleInitTimeout() {
    cancelInitTimeout()
    val runnable = Runnable {
      val timedOutCallbacks = synchronized(lock) {
        if (!isInitializing) return@Runnable
        isInitializing = false
        isReady = false
        val copy = readyCallbacks.toList()
        readyCallbacks.clear()
        copy
      }
      runCatching {
        tts?.stop()
        tts?.shutdown()
      }
      tts = null
      timedOutCallbacks.forEach { it(false) }
    }
    initTimeoutRunnable = runnable
    mainHandler.postDelayed(runnable, INIT_TIMEOUT_MS)
  }

  private fun cancelInitTimeout() {
    initTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
    initTimeoutRunnable = null
  }

  private fun scheduleResponseTimeout(utteranceId: String) {
    cancelResponseTimeout()
    val runnable = Runnable {
      if (pendingUtteranceId != utteranceId) return@Runnable
      pendingUtteranceId = null
      chunkFirstIds.entries.removeIf { it.value == utteranceId }
      chunkFinalIds.entries.removeIf { it.value == utteranceId }
      runCatching { tts?.stop() }
      listener?.onUtteranceError(utteranceId, MSG_ENGINE_UNRESPONSIVE)
    }
    responseTimeoutRunnable = runnable
    mainHandler.postDelayed(runnable, RESPONSE_TIMEOUT_MS)
  }

  private fun cancelResponseTimeout() {
    responseTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
    responseTimeoutRunnable = null
  }

  private fun extractBaseId(chunkUtteranceId: String): String {
    val sep = chunkUtteranceId.lastIndexOf('#')
    return if (sep > 0) chunkUtteranceId.substring(0, sep) else chunkUtteranceId
  }

  companion object {
    private const val SENTENCE_PAUSE_MS = 240L
    private const val INIT_TIMEOUT_MS = 5000L
    private const val RESPONSE_TIMEOUT_MS = 6000L

    const val MSG_ENGINE_UNRESPONSIVE =
      "Выбранный движок синтеза речи не отвечает. Попробуйте другой голос или движок в системных настройках."
    const val MSG_SYNTHESIS_ERROR =
      "Не удалось озвучить текст. Попробуйте другой голос или проверьте настройки синтеза речи."

    fun formatVoiceDisplayName(voice: Voice): String {
      val raw = voice.name.trim()
      val match = Regex("^ru[-_]ru[-_]x[-_]([a-z0-9]+)[-_](local|network)$", RegexOption.IGNORE_CASE)
        .find(raw)
      if (match != null) {
        val code = match.groupValues[1].uppercase(Locale.US)
        return "Русский ($code)"
      }
      return raw.ifBlank { "Русский голос" }
    }
  }
}
