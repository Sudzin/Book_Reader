package com.example.reader.tts

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.data.local.BookEntity
import com.example.data.local.BookFormat
import com.example.data.repository.BookRepository
import com.example.reader.engine.BookChapter
import com.example.reader.engine.LoadedBookContent
import com.example.reader.engine.Locator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TtsPlaybackState(
  val isActive: Boolean = false,
  val isPlaying: Boolean = false,
  val isPaused: Boolean = false,
  val isInitializing: Boolean = false,
  val bookId: Long = -1L,
  val bookTitle: String = "",
  val bookAuthor: String = "",
  val coverPath: String? = null,
  val chapterIndex: Int = 0,
  val chapterTitle: String = "",
  val activeParagraphIndex: Int = -1,
  val totalChapters: Int = 0,
  val speed: Float = 1.0f,
  val pitch: Float = 1.0f,
  val voiceName: String? = null,
  val errorMessage: String? = null,
)

interface TtsProgressSink {
  fun saveProgress(
    bookId: Long,
    locator: Locator,
    progressPercent: Float,
    totalUnits: Int,
    totalChars: Long,
  )
}

interface TtsServiceHost {
  fun requestAudioFocus(): Boolean
  fun abandonAudioFocus()
  fun onPlaybackStateUpdated(state: TtsPlaybackState)
  fun onPlaybackStopped()
}

class TtsPlaybackController(
  private val synthesizer: SpeechSynthesizer,
  private val progressSink: TtsProgressSink? = null,
  private val startForegroundServiceAction: (() -> Unit)? = null,
) {

  private val _state = MutableStateFlow(TtsPlaybackState())
  val state: StateFlow<TtsPlaybackState> = _state.asStateFlow()

  private val lock = Any()
  private var activeBook: BookEntity? = null
  private var activeContent: LoadedBookContent.Reflowable? = null
  private var cursorChapterIdx: Int = 0
  private var cursorListIdx: Int = 0
  private var currentSpeed: Float = 1.0f
  private var currentPitch: Float = 1.0f
  private var currentVoiceName: String? = null
  private var utteranceGeneration: Long = 0L
  private var isSynthesizerReady: Boolean = false

  @Volatile
  private var serviceHost: TtsServiceHost? = null

  init {
    synthesizer.setListener(object : SpeechSynthesizer.Listener {
      override fun onUtteranceStart(utteranceId: String) {
        val parts = parseUtteranceId(utteranceId) ?: return
        synchronized(lock) {
          if (parts.generation != utteranceGeneration) return
          val content = activeContent ?: return
          val chapter = content.chapters.getOrNull(parts.chapterIdx) ?: return
          val paragraph = chapter.paragraphs.getOrNull(parts.listIdx) ?: return
          persistCurrentLocked(content, chapter.index, paragraph.index)
        }
      }

      override fun onUtteranceDone(utteranceId: String) {
        val parts = parseUtteranceId(utteranceId) ?: return
        var hostToNotify: TtsServiceHost? = null
        var stoppedHost: TtsServiceHost? = null
        var nextText: String? = null
        var nextUtteranceId: String? = null
        var nextSpeed = 1.0f
        var nextPitch = 1.0f
        var nextVoice: String? = null

        synchronized(lock) {
          if (parts.generation != utteranceGeneration || !_state.value.isPlaying) return
          val content = activeContent ?: return
          val nextPos = findNextSpeakable(content.chapters, parts.chapterIdx, parts.listIdx)
          if (nextPos == null) {
            utteranceGeneration++
            activeContent = null
            activeBook = null
            _state.value = TtsPlaybackState(
              speed = currentSpeed,
              pitch = currentPitch,
              voiceName = currentVoiceName,
            )
            stoppedHost = serviceHost
          } else {
            cursorChapterIdx = nextPos.first
            cursorListIdx = nextPos.second
            val chapter = content.chapters[cursorChapterIdx]
            val paragraph = chapter.paragraphs[cursorListIdx]
            utteranceGeneration++
            nextUtteranceId = formatUtteranceId(utteranceGeneration, cursorChapterIdx, cursorListIdx)
            nextText = paragraph.text
            nextSpeed = currentSpeed
            nextPitch = currentPitch
            nextVoice = currentVoiceName
            publishStateLocked(
              content = content,
              chapter = chapter,
              paragraphIndex = paragraph.index,
              isPlaying = true,
              isPaused = false,
              isInitializing = false,
              error = null,
            )
            persistCurrentLocked(content, chapter.index, paragraph.index)
            hostToNotify = serviceHost
          }
        }

        if (stoppedHost != null) {
          stoppedHost?.abandonAudioFocus()
          stoppedHost?.onPlaybackStopped()
        } else if (nextText != null && nextUtteranceId != null) {
          hostToNotify?.onPlaybackStateUpdated(_state.value)
          synthesizer.speak(nextText!!, nextUtteranceId!!, nextSpeed, nextPitch, nextVoice)
        }
      }

      override fun onUtteranceError(utteranceId: String, message: String) {
        val parts = parseUtteranceId(utteranceId) ?: return
        var hostToNotify: TtsServiceHost? = null
        synchronized(lock) {
          if (parts.generation != utteranceGeneration) return
          utteranceGeneration++
          _state.value = _state.value.copy(
            isPlaying = false,
            isPaused = _state.value.isActive,
            isInitializing = false,
            errorMessage = message.ifBlank { AndroidSpeechSynthesizer.MSG_ENGINE_UNRESPONSIVE },
          )
          hostToNotify = serviceHost
        }
        hostToNotify?.abandonAudioFocus()
        hostToNotify?.onPlaybackStateUpdated(_state.value)
      }
    })
  }

  fun attachServiceHost(host: TtsServiceHost?) {
    val currentState: TtsPlaybackState
    synchronized(lock) {
      serviceHost = host
      currentState = _state.value
    }
    if (host != null && currentState.isActive) {
      host.onPlaybackStateUpdated(currentState)
    }
  }

  fun startBookPlayback(
    book: BookEntity,
    content: LoadedBookContent.Reflowable,
    startChapterIndex: Int,
    startParagraphIndex: Int,
    speechSpeed: Float,
    pitch: Float = currentPitch,
    voiceName: String? = currentVoiceName,
  ): Boolean {
    val startPos = findSpeakableAtOrAfter(
      chapters = content.chapters,
      chapterIdx = startChapterIndex.coerceIn(0, (content.chapters.size - 1).coerceAtLeast(0)),
      minParagraphIdx = startParagraphIndex.coerceAtLeast(0),
    ) ?: return false

    var textToSpeak: String? = null
    var utteranceId: String? = null
    val speedToUse = speechSpeed.coerceIn(0.7f, 1.3f)
    val pitchToUse = pitch.coerceIn(0.7f, 1.3f)
    var needInit = false

    synthesizer.stop()

    synchronized(lock) {
      activeBook = book
      activeContent = content
      cursorChapterIdx = startPos.first
      cursorListIdx = startPos.second
      currentSpeed = speedToUse
      currentPitch = pitchToUse
      currentVoiceName = voiceName
      utteranceGeneration++

      val chapter = content.chapters[cursorChapterIdx]
      val paragraph = chapter.paragraphs[cursorListIdx]

      publishStateLocked(
        content = content,
        chapter = chapter,
        paragraphIndex = paragraph.index,
        isPlaying = true,
        isPaused = false,
        isInitializing = !isSynthesizerReady,
        error = null,
      )
      persistCurrentLocked(content, chapter.index, paragraph.index)

      if (isSynthesizerReady) {
        textToSpeak = paragraph.text
        utteranceId = formatUtteranceId(utteranceGeneration, cursorChapterIdx, cursorListIdx)
      } else {
        needInit = true
      }
    }

    startForegroundServiceAction?.invoke()
    serviceHost?.onPlaybackStateUpdated(_state.value)

    if (needInit) {
      val capturedGen = synchronized(lock) { utteranceGeneration }
      synthesizer.initialize { ready ->
        var speakNow: Pair<String, String>? = null
        var hostToNotify: TtsServiceHost? = null
        var failedHost: TtsServiceHost? = null

        synchronized(lock) {
          isSynthesizerReady = ready
          if (utteranceGeneration == capturedGen && _state.value.isActive) {
            if (ready) {
              val active = activeContent
              val chapter = active?.chapters?.getOrNull(cursorChapterIdx)
              val paragraph = chapter?.paragraphs?.getOrNull(cursorListIdx)
              if (_state.value.isPlaying && active != null && chapter != null && paragraph != null) {
                val id = formatUtteranceId(utteranceGeneration, cursorChapterIdx, cursorListIdx)
                speakNow = paragraph.text to id
                _state.value = _state.value.copy(isInitializing = false, errorMessage = null)
              } else {
                _state.value = _state.value.copy(isInitializing = false)
              }
              hostToNotify = serviceHost
            } else {
              activeContent = null
              activeBook = null
              _state.value = TtsPlaybackState(
                speed = currentSpeed,
                pitch = currentPitch,
                voiceName = currentVoiceName,
                errorMessage = AndroidTtsVoiceGateway.MSG_ENGINE_UNAVAILABLE,
              )
              failedHost = serviceHost
            }
          }
        }

        if (failedHost != null) {
          failedHost?.abandonAudioFocus()
          failedHost?.onPlaybackStopped()
        } else {
          if (speakNow != null) {
            val host = synchronized(lock) { serviceHost }
            val focusOk = host?.requestAudioFocus() ?: true
            if (focusOk) {
              host?.onPlaybackStateUpdated(_state.value)
              synthesizer.speak(speakNow!!.first, speakNow!!.second, currentSpeed, currentPitch, currentVoiceName)
            } else {
              pause()
            }
          } else {
            hostToNotify?.onPlaybackStateUpdated(_state.value)
          }
        }
      }
    } else if (textToSpeak != null && utteranceId != null) {
      val host = synchronized(lock) { serviceHost }
      val focusOk = host?.requestAudioFocus() ?: true
      if (focusOk) {
        synthesizer.speak(textToSpeak!!, utteranceId!!, speedToUse, pitchToUse, voiceName)
      } else {
        pause()
      }
    }

    return true
  }

  fun pause() {
    val hostToNotify: TtsServiceHost?
    val updatedState: TtsPlaybackState
    synchronized(lock) {
      if (!_state.value.isActive || _state.value.isPaused) return
      utteranceGeneration++
      _state.value = _state.value.copy(
        isPlaying = false,
        isPaused = true,
        isInitializing = false,
      )
      updatedState = _state.value
      hostToNotify = serviceHost
    }
    synthesizer.stop()
    hostToNotify?.abandonAudioFocus()
    hostToNotify?.onPlaybackStateUpdated(updatedState)
  }

  fun resume() {
    val host: TtsServiceHost?
    val text: String
    val utteranceId: String
    val speedToUse: Float
    val pitchToUse: Float
    val voiceToUse: String?
    val needInit: Boolean

    synchronized(lock) {
      val content = activeContent ?: return
      if (!_state.value.isActive) return
      val chapter = content.chapters.getOrNull(cursorChapterIdx) ?: return
      val paragraph = chapter.paragraphs.getOrNull(cursorListIdx) ?: return

      utteranceGeneration++
      _state.value = _state.value.copy(
        isPlaying = true,
        isPaused = false,
        isInitializing = !isSynthesizerReady,
        errorMessage = null,
      )
      host = serviceHost
      text = paragraph.text
      utteranceId = formatUtteranceId(utteranceGeneration, cursorChapterIdx, cursorListIdx)
      speedToUse = currentSpeed
      pitchToUse = currentPitch
      voiceToUse = currentVoiceName
      needInit = !isSynthesizerReady
    }

    startForegroundServiceAction?.invoke()

    if (needInit) {
      val capturedGen = synchronized(lock) { utteranceGeneration }
      synthesizer.initialize { ready ->
        isSynthesizerReady = ready
        var shouldSpeak = false
        var failedHost: TtsServiceHost? = null
        synchronized(lock) {
          if (utteranceGeneration == capturedGen && _state.value.isActive) {
            if (ready && _state.value.isPlaying) {
              shouldSpeak = true
            } else if (!ready) {
              activeContent = null
              activeBook = null
              _state.value = TtsPlaybackState(
                speed = currentSpeed,
                pitch = currentPitch,
                voiceName = currentVoiceName,
                errorMessage = AndroidTtsVoiceGateway.MSG_ENGINE_UNAVAILABLE,
              )
              failedHost = serviceHost
            }
          }
        }
        if (failedHost != null) {
          failedHost?.abandonAudioFocus()
          failedHost?.onPlaybackStopped()
        } else if (shouldSpeak) {
          val focusOk = host?.requestAudioFocus() ?: true
          if (focusOk) {
            synchronized(lock) {
              _state.value = _state.value.copy(isInitializing = false)
            }
            host?.onPlaybackStateUpdated(_state.value)
            synthesizer.speak(text, utteranceId, speedToUse, pitchToUse, voiceToUse)
          } else {
            pause()
          }
        }
      }
    } else {
      val focusOk = host?.requestAudioFocus() ?: true
      if (focusOk) {
        host?.onPlaybackStateUpdated(_state.value)
        synthesizer.speak(text, utteranceId, speedToUse, pitchToUse, voiceToUse)
      } else {
        pause()
      }
    }
  }

  fun togglePlayPause() {
    val isPlaying = synchronized(lock) { _state.value.isPlaying }
    if (isPlaying) pause() else resume()
  }

  fun nextParagraph(): Boolean = stepParagraph(forward = true)

  fun previousParagraph(): Boolean = stepParagraph(forward = false)

  private fun stepParagraph(forward: Boolean): Boolean {
    var hostToNotify: TtsServiceHost?
    var textToSpeak: String? = null
    var utteranceId: String? = null
    var speedToUse = 1.0f
    var pitchToUse = 1.0f
    var voiceToUse: String? = null
    var shouldPlay = false

    synthesizer.stop()

    synchronized(lock) {
      val content = activeContent ?: return false
      if (!_state.value.isActive) return false
      val target = if (forward) {
        findNextSpeakable(content.chapters, cursorChapterIdx, cursorListIdx)
      } else {
        findPrevSpeakable(content.chapters, cursorChapterIdx, cursorListIdx)
      } ?: return false

      cursorChapterIdx = target.first
      cursorListIdx = target.second
      val chapter = content.chapters[cursorChapterIdx]
      val paragraph = chapter.paragraphs[cursorListIdx]
      utteranceGeneration++
      shouldPlay = _state.value.isPlaying

      publishStateLocked(
        content = content,
        chapter = chapter,
        paragraphIndex = paragraph.index,
        isPlaying = shouldPlay,
        isPaused = !shouldPlay,
        isInitializing = shouldPlay && !isSynthesizerReady,
        error = null,
      )
      persistCurrentLocked(content, chapter.index, paragraph.index)
      hostToNotify = serviceHost
      if (shouldPlay && isSynthesizerReady) {
        textToSpeak = paragraph.text
        utteranceId = formatUtteranceId(utteranceGeneration, cursorChapterIdx, cursorListIdx)
        speedToUse = currentSpeed
        pitchToUse = currentPitch
        voiceToUse = currentVoiceName
      }
    }

    hostToNotify?.onPlaybackStateUpdated(_state.value)
    if (shouldPlay && textToSpeak != null && utteranceId != null) {
      val focusOk = hostToNotify?.requestAudioFocus() ?: true
      if (focusOk) {
        synthesizer.speak(textToSpeak!!, utteranceId!!, speedToUse, pitchToUse, voiceToUse)
      } else {
        pause()
      }
    }
    return true
  }

  fun seekTo(chapterIndex: Int, paragraphIndex: Int): Boolean {
    val book: BookEntity
    val content: LoadedBookContent.Reflowable
    val wasPlaying: Boolean
    synchronized(lock) {
      if (!_state.value.isActive) return false
      book = activeBook ?: return false
      content = activeContent ?: return false
      wasPlaying = _state.value.isPlaying
    }
    val startPos = findSpeakableAtOrAfter(
      chapters = content.chapters,
      chapterIdx = chapterIndex.coerceIn(0, (content.chapters.size - 1).coerceAtLeast(0)),
      minParagraphIdx = paragraphIndex.coerceAtLeast(0),
    ) ?: return false

    if (wasPlaying) {
      return startBookPlayback(
        book = book,
        content = content,
        startChapterIndex = content.chapters[startPos.first].index,
        startParagraphIndex = content.chapters[startPos.first].paragraphs[startPos.second].index,
        speechSpeed = _state.value.speed,
        pitch = _state.value.pitch,
        voiceName = _state.value.voiceName,
      )
    }

    synthesizer.stop()
    val hostToNotify: TtsServiceHost?
    synchronized(lock) {
      cursorChapterIdx = startPos.first
      cursorListIdx = startPos.second
      val chapter = content.chapters[cursorChapterIdx]
      val paragraph = chapter.paragraphs[cursorListIdx]
      utteranceGeneration++
      publishStateLocked(
        content = content,
        chapter = chapter,
        paragraphIndex = paragraph.index,
        isPlaying = false,
        isPaused = true,
        isInitializing = false,
        error = null,
      )
      persistCurrentLocked(content, chapter.index, paragraph.index)
      hostToNotify = serviceHost
    }
    hostToNotify?.onPlaybackStateUpdated(_state.value)
    return true
  }

  fun setSpeed(newSpeed: Float) {
    val clamped = newSpeed.coerceIn(0.7f, 1.3f)
    val hostToNotify: TtsServiceHost?
    val updated: TtsPlaybackState
    synchronized(lock) {
      currentSpeed = clamped
      _state.value = _state.value.copy(speed = clamped)
      updated = _state.value
      hostToNotify = serviceHost
    }
    synthesizer.setSpeechRate(clamped)
    if (updated.isActive) {
      hostToNotify?.onPlaybackStateUpdated(updated)
    }
  }

  fun setPitch(newPitch: Float) {
    val clamped = newPitch.coerceIn(0.7f, 1.3f)
    val hostToNotify: TtsServiceHost?
    val updated: TtsPlaybackState
    synchronized(lock) {
      currentPitch = clamped
      _state.value = _state.value.copy(pitch = clamped)
      updated = _state.value
      hostToNotify = serviceHost
    }
    synthesizer.setPitch(clamped)
    if (updated.isActive) {
      hostToNotify?.onPlaybackStateUpdated(updated)
    }
  }

  fun setVoiceName(voiceName: String?) {
    val clean = voiceName?.trim()?.takeIf { it.isNotEmpty() }
    val hostToNotify: TtsServiceHost?
    val updated: TtsPlaybackState
    synchronized(lock) {
      currentVoiceName = clean
      _state.value = _state.value.copy(voiceName = clean)
      updated = _state.value
      hostToNotify = serviceHost
    }
    synthesizer.setVoice(clean)
    if (updated.isActive) {
      hostToNotify?.onPlaybackStateUpdated(updated)
    }
  }

  fun onIncomingCallOrFocusLost() {
    if (synchronized(lock) { _state.value.isPlaying }) {
      pause()
    }
  }

  fun onBecomingNoisy() {
    if (synchronized(lock) { _state.value.isPlaying }) {
      pause()
    }
  }

  fun stop() {
    val hostToStop: TtsServiceHost?
    synchronized(lock) {
      utteranceGeneration++
      activeContent = null
      activeBook = null
      _state.value = TtsPlaybackState(
        speed = currentSpeed,
        pitch = currentPitch,
        voiceName = currentVoiceName,
      )
      hostToStop = serviceHost
    }
    synthesizer.stop()
    hostToStop?.abandonAudioFocus()
    hostToStop?.onPlaybackStopped()
  }

  fun release() {
    stop()
    synthesizer.shutdown()
    synchronized(lock) {
      isSynthesizerReady = false
      serviceHost = null
    }
  }

  private fun publishStateLocked(
    content: LoadedBookContent.Reflowable,
    chapter: BookChapter,
    paragraphIndex: Int,
    isPlaying: Boolean,
    isPaused: Boolean,
    isInitializing: Boolean,
    error: String?,
  ) {
    val book = activeBook
    _state.value = TtsPlaybackState(
      isActive = true,
      isPlaying = isPlaying,
      isPaused = isPaused,
      isInitializing = isInitializing,
      bookId = book?.id ?: -1L,
      bookTitle = book?.title.orEmpty(),
      bookAuthor = book?.author.orEmpty(),
      coverPath = book?.coverPath,
      chapterIndex = chapter.index,
      chapterTitle = chapter.title.ifBlank { "Глава ${chapter.index + 1}" },
      activeParagraphIndex = paragraphIndex,
      totalChapters = content.chapters.size,
      speed = currentSpeed,
      pitch = currentPitch,
      voiceName = currentVoiceName,
      errorMessage = error,
    )
  }

  private fun persistCurrentLocked(
    content: LoadedBookContent.Reflowable,
    chapterIdx: Int,
    paragraphIdx: Int,
  ) {
    val book = activeBook ?: return
    val locator = Locator(
      chapterIndex = chapterIdx,
      paragraphIndex = paragraphIdx,
      charOffset = 0,
    )
    progressSink?.saveProgress(
      bookId = book.id,
      locator = locator,
      progressPercent = content.progressFor(locator),
      totalUnits = content.chapters.size.coerceAtLeast(1),
      totalChars = content.totalChars,
    )
  }

  private fun findSpeakableAtOrAfter(
    chapters: List<BookChapter>,
    chapterIdx: Int,
    minParagraphIdx: Int,
  ): Pair<Int, Int>? {
    if (chapters.isEmpty()) return null
    val startCh = chapters.indexOfFirst { it.index >= chapterIdx }.takeIf { it >= 0 } ?: 0
    for (c in startCh until chapters.size) {
      val paras = chapters[c].paragraphs
      val minIdx = if (c == startCh) minParagraphIdx else 0
      val p = paras.indexOfFirst { it.index >= minIdx && it.text.isNotBlank() }
      if (p >= 0) return c to p
    }
    return null
  }

  private fun findNextSpeakable(
    chapters: List<BookChapter>,
    currentChapterListIdx: Int,
    currentParagraphListIdx: Int,
  ): Pair<Int, Int>? {
    if (chapters.isEmpty()) return null
    val currentParas = chapters.getOrNull(currentChapterListIdx)?.paragraphs.orEmpty()
    for (p in (currentParagraphListIdx + 1) until currentParas.size) {
      if (currentParas[p].text.isNotBlank()) {
        return currentChapterListIdx to p
      }
    }
    for (c in (currentChapterListIdx + 1) until chapters.size) {
      val p = chapters[c].paragraphs.indexOfFirst { it.text.isNotBlank() }
      if (p >= 0) return c to p
    }
    return null
  }

  private fun findPrevSpeakable(
    chapters: List<BookChapter>,
    currentChapterListIdx: Int,
    currentParagraphListIdx: Int,
  ): Pair<Int, Int>? {
    if (chapters.isEmpty()) return null
    val currentParas = chapters.getOrNull(currentChapterListIdx)?.paragraphs.orEmpty()
    for (p in (currentParagraphListIdx - 1) downTo 0) {
      if (currentParas[p].text.isNotBlank()) {
        return currentChapterListIdx to p
      }
    }
    for (c in (currentChapterListIdx - 1) downTo 0) {
      val paras = chapters[c].paragraphs
      for (p in paras.lastIndex downTo 0) {
        if (paras[p].text.isNotBlank()) {
          return c to p
        }
      }
    }
    return null
  }

  private data class UtteranceParts(
    val generation: Long,
    val chapterIdx: Int,
    val listIdx: Int,
  )

  private fun formatUtteranceId(generation: Long, chapterIdx: Int, listIdx: Int): String =
    "$generation:$chapterIdx:$listIdx"

  private fun parseUtteranceId(raw: String): UtteranceParts? {
    val parts = raw.split(':')
    if (parts.size != 3) return null
    val gen = parts[0].toLongOrNull() ?: return null
    val ch = parts[1].toIntOrNull() ?: return null
    val p = parts[2].toIntOrNull() ?: return null
    return UtteranceParts(gen, ch, p)
  }
}

object TtsPlaybackManager {
  @Volatile
  private var instance: TtsPlaybackController? = null

  fun getInstance(
    context: Context,
    bookRepository: BookRepository,
  ): TtsPlaybackController {
    instance?.let { return it }
    return synchronized(this) {
      instance ?: createController(context.applicationContext, bookRepository).also {
        instance = it
      }
    }
  }

  fun peek(): TtsPlaybackController? = instance

  private fun createController(
    appContext: Context,
    bookRepository: BookRepository,
  ): TtsPlaybackController {
    val sink = object : TtsProgressSink {
      override fun saveProgress(
        bookId: Long,
        locator: Locator,
        progressPercent: Float,
        totalUnits: Int,
        totalChars: Long,
      ) {
        bookRepository.saveProgressAsync(
          bookId = bookId,
          locator = locator,
          progressPercent = progressPercent,
          totalUnits = totalUnits,
          totalChars = totalChars,
        )
      }
    }
    return TtsPlaybackController(
      synthesizer = AndroidSpeechSynthesizer(appContext),
      progressSink = sink,
      startForegroundServiceAction = {
        val intent = Intent(appContext, TtsPlaybackService::class.java).apply {
          action = TtsPlaybackService.ACTION_START_OR_SYNC
        }
        ContextCompat.startForegroundService(appContext, intent)
      },
    )
  }
}
