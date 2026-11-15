package com.example.reader.tts

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.view.KeyEvent
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.repository.BookRepository
import java.io.File

class TtsPlaybackService : Service(), TtsServiceHost {

  private lateinit var playbackController: TtsPlaybackController
  private lateinit var notificationManager: NotificationManager
  private lateinit var audioManager: AudioManager
  private lateinit var mediaSession: MediaSession
  private var wakeLock: PowerManager.WakeLock? = null

  private var audioFocusRequest: AudioFocusRequest? = null
  private var hasAudioFocus: Boolean = false
  private var noisyReceiverRegistered: Boolean = false
  private var modeListenerRegistered: Boolean = false
  private var cachedCoverPath: String? = null
  private var cachedCoverBitmap: Bitmap? = null

  private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
    when (focusChange) {
      AudioManager.AUDIOFOCUS_LOSS,
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
        hasAudioFocus = false
        playbackController.onIncomingCallOrFocusLost()
      }
      AudioManager.AUDIOFOCUS_GAIN -> {
        hasAudioFocus = true
      }
    }
  }

  private val becomingNoisyReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
        playbackController.onBecomingNoisy()
      }
    }
  }

  private val audioModeListener: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    AudioManager.OnModeChangedListener { mode ->
      if (mode == AudioManager.MODE_RINGTONE || mode == AudioManager.MODE_IN_CALL) {
        playbackController.onIncomingCallOrFocusLost()
      }
    }
  } else {
    null
  }

  override fun onCreate() {
    super.onCreate()
    notificationManager = getSystemService(NotificationManager::class.java)
    audioManager = getSystemService(AudioManager::class.java)
    val powerManager = getSystemService(PowerManager::class.java)
    wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Listay:TtsWakeLock")?.apply {
      setReferenceCounted(false)
    }

    ensureNotificationChannel()

    val bookRepository = BookRepository(applicationContext, AppDatabase.getInstance(applicationContext))
    playbackController = TtsPlaybackManager.getInstance(applicationContext, bookRepository)

    mediaSession = MediaSession(this, "ListayTtsMediaSession").apply {
      @Suppress("DEPRECATION")
      setFlags(
        MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
          MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
      )
      setCallback(object : MediaSession.Callback() {
        override fun onPlay() {
          playbackController.resume()
        }

        override fun onPause() {
          playbackController.pause()
        }

        override fun onSkipToPrevious() {
          playbackController.previousParagraph()
        }

        override fun onSkipToNext() {
          playbackController.nextParagraph()
        }

        override fun onStop() {
          playbackController.stop()
        }

        override fun onCustomAction(action: String, extras: Bundle?) {
          if (action == CUSTOM_ACTION_STOP) {
            playbackController.stop()
          }
        }

        override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
          val keyEvent = extractKeyEvent(mediaButtonIntent)
          if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount == 0) {
            when (keyEvent.keyCode) {
              KeyEvent.KEYCODE_MEDIA_PLAY -> {
                playbackController.resume()
                return true
              }
              KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                playbackController.pause()
                return true
              }
              KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
              KeyEvent.KEYCODE_HEADSETHOOK -> {
                playbackController.togglePlayPause()
                return true
              }
              KeyEvent.KEYCODE_MEDIA_NEXT -> {
                playbackController.nextParagraph()
                return true
              }
              KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                playbackController.previousParagraph()
                return true
              }
              KeyEvent.KEYCODE_MEDIA_STOP -> {
                playbackController.stop()
                return true
              }
            }
          }
          return super.onMediaButtonEvent(mediaButtonIntent)
        }
      })
    }

    registerNoisyAndModeListeners()
    playbackController.attachServiceHost(this)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val action = intent?.action ?: ACTION_START_OR_SYNC
    val currentState = playbackController.state.value

    if (!currentState.isActive && action != ACTION_STOP) {
      promoteToForeground(currentState)
      onPlaybackStopped()
      return START_NOT_STICKY
    }

    when (action) {
      ACTION_START_OR_SYNC -> {
        promoteToForeground(currentState)
        updateMediaSession(currentState)
      }
      ACTION_TOGGLE_PLAY_PAUSE -> {
        promoteToForeground(currentState)
        playbackController.togglePlayPause()
      }
      ACTION_PLAY -> {
        promoteToForeground(currentState)
        playbackController.resume()
      }
      ACTION_PAUSE -> {
        promoteToForeground(currentState)
        playbackController.pause()
      }
      ACTION_PREV_PARAGRAPH -> {
        promoteToForeground(currentState)
        playbackController.previousParagraph()
      }
      ACTION_NEXT_PARAGRAPH -> {
        promoteToForeground(currentState)
        playbackController.nextParagraph()
      }
      ACTION_STOP -> {
        promoteToForeground(currentState)
        playbackController.stop()
        return START_NOT_STICKY
      }
    }
    return START_NOT_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onDestroy() {
    releaseWakeLock()
    abandonAudioFocus()
    unregisterNoisyAndModeListeners()
    playbackController.attachServiceHost(null)
    runCatching {
      mediaSession.isActive = false
      mediaSession.release()
    }
    super.onDestroy()
  }

  override fun requestAudioFocus(): Boolean {
    if (hasAudioFocus) return true
    val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        )
        .setAcceptsDelayedFocusGain(false)
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener(audioFocusListener)
        .build()
      audioFocusRequest = req
      audioManager.requestAudioFocus(req)
    } else {
      @Suppress("DEPRECATION")
      audioManager.requestAudioFocus(
        audioFocusListener,
        AudioManager.STREAM_MUSIC,
        AudioManager.AUDIOFOCUS_GAIN,
      )
    }
    hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    return hasAudioFocus
  }

  override fun abandonAudioFocus() {
    hasAudioFocus = false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      audioFocusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
      audioFocusRequest = null
    } else {
      @Suppress("DEPRECATION")
      runCatching { audioManager.abandonAudioFocus(audioFocusListener) }
    }
  }

  override fun onPlaybackStateUpdated(state: TtsPlaybackState) {
    if (!state.isActive) {
      onPlaybackStopped()
      return
    }
    if (state.isPlaying) {
      acquireWakeLock()
    } else {
      releaseWakeLock()
    }
    updateMediaSession(state)
    val notification = buildNotification(state)
    notificationManager.notify(NOTIFICATION_ID, notification)
  }

  override fun onPlaybackStopped() {
    releaseWakeLock()
    runCatching {
      val stoppedState = PlaybackState.Builder()
        .setState(PlaybackState.STATE_STOPPED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
        .setActions(0L)
        .build()
      mediaSession.setPlaybackState(stoppedState)
      mediaSession.isActive = false
    }
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    notificationManager.cancel(NOTIFICATION_ID)
    stopSelf()
  }

  private fun promoteToForeground(state: TtsPlaybackState) {
    if (state.isPlaying) {
      acquireWakeLock()
    }
    updateMediaSession(state)
    val notification = buildNotification(state)
    val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
    } else {
      0
    }
    try {
      ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
    } catch (e: Exception) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
        playbackController.stop()
      }
    }
  }

  private fun updateMediaSession(state: TtsPlaybackState) {
    if (!state.isActive) {
      mediaSession.isActive = false
      return
    }
    val pbStateCode = if (state.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
    val actions = PlaybackState.ACTION_PLAY or
      PlaybackState.ACTION_PAUSE or
      PlaybackState.ACTION_PLAY_PAUSE or
      PlaybackState.ACTION_SKIP_TO_PREVIOUS or
      PlaybackState.ACTION_SKIP_TO_NEXT or
      PlaybackState.ACTION_STOP

    val stopCustomAction = PlaybackState.CustomAction.Builder(
      CUSTOM_ACTION_STOP,
      getString(R.string.tts_action_stop),
      R.drawable.ic_tts_stop,
    ).build()

    val pbState = PlaybackState.Builder()
      .setState(pbStateCode, PlaybackState.PLAYBACK_POSITION_UNKNOWN, state.speed)
      .setActions(actions)
      .addCustomAction(stopCustomAction)
      .build()

    val cover = loadCoverBitmap(state.coverPath)
    val metadataBuilder = MediaMetadata.Builder()
      .putString(MediaMetadata.METADATA_KEY_TITLE, state.bookTitle.ifBlank { getString(R.string.app_name) })
      .putString(
        MediaMetadata.METADATA_KEY_ARTIST,
        buildString {
          if (state.bookAuthor.isNotBlank()) {
            append(state.bookAuthor)
            if (state.chapterTitle.isNotBlank()) append(" • ")
          }
          if (state.chapterTitle.isNotBlank()) {
            append(state.chapterTitle)
          }
        }.ifBlank { getString(R.string.tts_channel_name) },
      )
      .putString(MediaMetadata.METADATA_KEY_ALBUM, state.chapterTitle)
    if (cover != null) {
      metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, cover)
      metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, cover)
    }

    mediaSession.setPlaybackState(pbState)
    mediaSession.setMetadata(metadataBuilder.build())
    if (!mediaSession.isActive) {
      mediaSession.isActive = true
    }
  }

  internal fun buildNotification(state: TtsPlaybackState): Notification {
    val contentIntent = PendingIntent.getActivity(
      this,
      100,
      Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        if (state.bookId > 0L) {
          putExtra(EXTRA_BOOK_ID, state.bookId)
        }
      },
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val prevIntent = serviceActionIntent(ACTION_PREV_PARAGRAPH, 101)
    val playPauseIntent = serviceActionIntent(ACTION_TOGGLE_PLAY_PAUSE, 102)
    val nextIntent = serviceActionIntent(ACTION_NEXT_PARAGRAPH, 103)
    val stopIntent = serviceActionIntent(ACTION_STOP, 104)

    val playPauseIcon = if (state.isPlaying) R.drawable.ic_tts_pause else R.drawable.ic_tts_play
    val playPauseLabel = getString(
      if (state.isPlaying) R.string.tts_action_pause else R.string.tts_action_resume,
    )

    val prevAction = Notification.Action.Builder(
      Icon.createWithResource(this, R.drawable.ic_tts_prev),
      getString(R.string.tts_action_prev),
      prevIntent,
    ).build()

    val toggleAction = Notification.Action.Builder(
      Icon.createWithResource(this, playPauseIcon),
      playPauseLabel,
      playPauseIntent,
    ).build()

    val nextAction = Notification.Action.Builder(
      Icon.createWithResource(this, R.drawable.ic_tts_next),
      getString(R.string.tts_action_next),
      nextIntent,
    ).build()

    val stopAction = Notification.Action.Builder(
      Icon.createWithResource(this, R.drawable.ic_tts_stop),
      getString(R.string.tts_action_stop),
      stopIntent,
    ).build()

    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Notification.Builder(this, CHANNEL_ID)
    } else {
      @Suppress("DEPRECATION")
      Notification.Builder(this)
    }

    val subtitle = buildString {
      if (state.chapterTitle.isNotBlank()) {
        append(state.chapterTitle)
      }
      if (state.activeParagraphIndex >= 0) {
        if (isNotEmpty()) append(" • ")
        append("абз. ${state.activeParagraphIndex + 1}")
      }
    }.ifBlank { getString(R.string.tts_channel_name) }

    builder
      .setSmallIcon(R.drawable.ic_tts_notification)
      .setContentTitle(state.bookTitle.ifBlank { getString(R.string.app_name) })
      .setContentText(subtitle)
      .setSubText(String.format("%.1fx", state.speed))
      .setContentIntent(contentIntent)
      .setDeleteIntent(stopIntent)
      .setVisibility(Notification.VISIBILITY_PUBLIC)
      .setOnlyAlertOnce(true)
      .setOngoing(state.isPlaying)
      .addAction(prevAction)
      .addAction(toggleAction)
      .addAction(nextAction)
      .addAction(stopAction)
      .setStyle(
        Notification.MediaStyle()
          .setMediaSession(mediaSession.sessionToken)
          .setShowActionsInCompactView(0, 1, 2),
      )

    loadCoverBitmap(state.coverPath)?.let { cover ->
      builder.setLargeIcon(cover)
    }

    return builder.build()
  }

  private fun serviceActionIntent(action: String, requestCode: Int): PendingIntent {
    val intent = Intent(this, TtsPlaybackService::class.java).apply {
      this.action = action
    }
    return PendingIntent.getService(
      this,
      requestCode,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun ensureNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        getString(R.string.tts_channel_name),
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = getString(R.string.tts_channel_description)
        setShowBadge(false)
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
      }
      notificationManager.createNotificationChannel(channel)
    }
  }

  private fun registerNoisyAndModeListeners() {
    if (!noisyReceiverRegistered) {
      val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
      ContextCompat.registerReceiver(
        this,
        becomingNoisyReceiver,
        filter,
        ContextCompat.RECEIVER_NOT_EXPORTED,
      )
      noisyReceiverRegistered = true
    }
    if (!modeListenerRegistered && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val listener = audioModeListener as? AudioManager.OnModeChangedListener
      if (listener != null) {
        runCatching {
          audioManager.addOnModeChangedListener(ContextCompat.getMainExecutor(this), listener)
          modeListenerRegistered = true
        }
      }
    }
  }

  private fun unregisterNoisyAndModeListeners() {
    if (noisyReceiverRegistered) {
      runCatching { unregisterReceiver(becomingNoisyReceiver) }
      noisyReceiverRegistered = false
    }
    if (modeListenerRegistered && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val listener = audioModeListener as? AudioManager.OnModeChangedListener
      if (listener != null) {
        runCatching { audioManager.removeOnModeChangedListener(listener) }
      }
      modeListenerRegistered = false
    }
  }

  private fun acquireWakeLock() {
    runCatching {
      if (wakeLock?.isHeld == false) {
        wakeLock?.acquire(60 * 60 * 1000L)
      }
    }
  }

  private fun releaseWakeLock() {
    runCatching {
      if (wakeLock?.isHeld == true) {
        wakeLock?.release()
      }
    }
  }

  private fun loadCoverBitmap(coverPath: String?): Bitmap? {
    if (coverPath.isNullOrBlank()) {
      cachedCoverPath = null
      cachedCoverBitmap = null
      return null
    }
    if (coverPath == cachedCoverPath && cachedCoverBitmap != null) {
      return cachedCoverBitmap
    }
    val file = File(coverPath)
    if (!file.exists()) return null
    val decoded = runCatching {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(coverPath, bounds)
      var sample = 1
      val maxDim = 512
      while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
        sample *= 2
      }
      BitmapFactory.Options().apply { inSampleSize = sample }.let { opts ->
        BitmapFactory.decodeFile(coverPath, opts)
      }
    }.getOrNull()
    cachedCoverPath = coverPath
    cachedCoverBitmap = decoded
    return decoded
  }

  @Suppress("DEPRECATION")
  private fun extractKeyEvent(intent: Intent): KeyEvent? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
    } else {
      intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
    }
  }

  companion object {
    const val CHANNEL_ID = "listay_tts_playback"
    const val NOTIFICATION_ID = 4101
    const val EXTRA_BOOK_ID = "extra_tts_book_id"
    const val CUSTOM_ACTION_STOP = "com.example.reader.tts.CUSTOM_STOP"

    const val ACTION_START_OR_SYNC = "com.example.reader.tts.ACTION_START_OR_SYNC"
    const val ACTION_TOGGLE_PLAY_PAUSE = "com.example.reader.tts.ACTION_TOGGLE_PLAY_PAUSE"
    const val ACTION_PLAY = "com.example.reader.tts.ACTION_PLAY"
    const val ACTION_PAUSE = "com.example.reader.tts.ACTION_PAUSE"
    const val ACTION_PREV_PARAGRAPH = "com.example.reader.tts.ACTION_PREV_PARAGRAPH"
    const val ACTION_NEXT_PARAGRAPH = "com.example.reader.tts.ACTION_NEXT_PARAGRAPH"
    const val ACTION_STOP = "com.example.reader.tts.ACTION_STOP"
  }
}
