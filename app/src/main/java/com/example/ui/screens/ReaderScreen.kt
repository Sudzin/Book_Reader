package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.ReaderPreferences
import com.example.data.local.ReaderReadingMode
import com.example.reader.engine.BookParagraph
import com.example.reader.engine.ChapterPaginator
import com.example.reader.engine.LoadedBookContent
import com.example.reader.engine.Locator
import com.example.reader.engine.ParagraphKind
import com.example.reader.engine.PdfParser
import com.example.reader.engine.ReaderParagraphStyleResolver
import com.example.ui.components.BookInlineImage
import com.example.ui.components.ThemedThinSlider
import com.example.ui.components.inactiveTrackColor
import com.example.ui.theme.AnimatedReaderColors
import com.example.ui.theme.ListayTheme
import com.example.ui.theme.LocalAnimationsEnabled
import com.example.ui.theme.rememberAnimatedReaderColors
import com.example.ui.theme.rememberSystemAnimationsEnabled
import com.example.ui.viewmodel.ReaderLoadState
import com.example.ui.viewmodel.ReaderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ReaderScreen(
  viewModel: ReaderViewModel,
  onNavigateBack: () -> Unit,
  registerVolumeKeyHandler: (((Boolean) -> Boolean)?) -> Unit,
) {
  ReaderScreenWithSharedTransition(
    viewModel = viewModel,
    onNavigateBack = onNavigateBack,
    registerVolumeKeyHandler = registerVolumeKeyHandler,
    sharedTransitionScope = null,
    animatedVisibilityScope = null,
  )
}

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun ReaderScreen(
  viewModel: ReaderViewModel,
  onNavigateBack: () -> Unit,
  registerVolumeKeyHandler: (((Boolean) -> Boolean)?) -> Unit,
  sharedTransitionScope: SharedTransitionScope?,
  animatedVisibilityScope: AnimatedVisibilityScope?,
) {
  ReaderScreenWithSharedTransition(
    viewModel = viewModel,
    onNavigateBack = onNavigateBack,
    registerVolumeKeyHandler = registerVolumeKeyHandler,
    sharedTransitionScope = sharedTransitionScope,
    animatedVisibilityScope = animatedVisibilityScope,
  )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ReaderScreenWithSharedTransition(
  viewModel: ReaderViewModel,
  onNavigateBack: () -> Unit,
  registerVolumeKeyHandler: (((Boolean) -> Boolean)?) -> Unit,
  sharedTransitionScope: SharedTransitionScope?,
  animatedVisibilityScope: AnimatedVisibilityScope?,
) {
  val loadState by viewModel.loadState.collectAsStateWithLifecycle()
  val preferences by viewModel.preferences.collectAsStateWithLifecycle()
  val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
  val quotes by viewModel.quotes.collectAsStateWithLifecycle()
  val isBookmarked by viewModel.isCurrentUnitBookmarked.collectAsStateWithLifecycle()
  val progressPercent by viewModel.progressPercent.collectAsStateWithLifecycle()
  val currentLocator by viewModel.currentLocator.collectAsStateWithLifecycle()
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
  val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()

  val context = LocalContext.current
  val view = LocalView.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val haptics = LocalHapticFeedback.current
  val coroutineScope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  val animationsEnabled = LocalAnimationsEnabled.current && rememberSystemAnimationsEnabled()

  var barsVisible by remember { mutableStateOf(true) }
  var barsBackProgress by remember { mutableFloatStateOf(0f) }
  val animatedBarsBackProgress by animateFloatAsState(
    targetValue = barsBackProgress,
    animationSpec = if (animationsEnabled) spring() else snap(),
    label = "reader_bars_back_progress",
  )
  var showAppearanceSheet by remember { mutableStateOf(false) }
  var showNavSheet by remember { mutableStateOf(false) }
  var showSearchSheet by remember { mutableStateOf(false) }
  var selectedParagraphForQuote by remember { mutableStateOf<Pair<String, BookParagraph>?>(null) }

  val isContentReady = loadState is ReaderLoadState.Ready
  val anyOverlayOpen = showAppearanceSheet ||
    showNavSheet ||
    showSearchSheet ||
    selectedParagraphForQuote != null

  PredictiveBackHandler(enabled = isContentReady && barsVisible && !anyOverlayOpen) { progress ->
    try {
      progress.collect { backEvent ->
        barsBackProgress = backEvent.progress.coerceIn(0f, 1f)
      }
      barsBackProgress = 0f
      barsVisible = false
    } catch (e: CancellationException) {
      barsBackProgress = 0f
      throw e
    }
  }

  val ttsController = remember(context, viewModel) { viewModel.ttsController(context) }
  val ttsState by ttsController.state.collectAsStateWithLifecycle()

  var pendingTtsStartParagraph by remember { mutableStateOf<Int?>(null) }
  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    val startPara = pendingTtsStartParagraph
    pendingTtsStartParagraph = null
    if (!granted) {
      coroutineScope.launch {
        snackbarHostState.showSnackbar("Уведомления отключены — управление озвучкой остаётся в приложении")
      }
    }
    val readyState = loadState as? ReaderLoadState.Ready
    val readyContent = readyState?.content as? LoadedBookContent.Reflowable
    if (startPara != null && readyState != null && readyContent != null) {
      ttsController.startBookPlayback(
        book = readyState.book,
        content = readyContent,
        startChapterIndex = readyState.currentUnitIndex,
        startParagraphIndex = startPara,
        speechSpeed = preferences.ttsSpeed,
        pitch = preferences.ttsPitch,
        voiceName = preferences.ttsVoiceName,
      )
    }
  }

  DisposableEffect(lifecycleOwner, viewModel) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_STOP) {
        viewModel.flushProgressNow()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      viewModel.flushProgressNow()
    }
  }

  DisposableEffect(preferences.keepScreenOn) {
    val window = (context as? Activity)?.window
    if (preferences.keepScreenOn) {
      window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
      window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    onDispose {
      window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
  }

  LaunchedEffect(Unit) {
    viewModel.toastMessage.collect { msg ->
      snackbarHostState.showSnackbar(msg)
    }
  }

  val themeMode = preferences.readerTheme

  ListayTheme(
    readerTheme = themeMode,
    dynamicColor = false,
    animationsEnabled = animationsEnabled,
  ) {
    val theme = rememberAnimatedReaderColors(themeMode, animationsEnabled)
    val barsDurationMs = if (animationsEnabled) 200 else 0
    val sharedContainerModifier = if (animationsEnabled && sharedTransitionScope != null && animatedVisibilityScope != null) {
      with(sharedTransitionScope) {
        Modifier.sharedBounds(
          sharedContentState = rememberSharedContentState(key = "book_cover_${viewModel.currentBookId}"),
          animatedVisibilityScope = animatedVisibilityScope,
          boundsTransform = { _, _ -> tween(durationMillis = 280, easing = FastOutSlowInEasing) },
          resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(),
        )
      }
    } else {
      Modifier
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .then(sharedContainerModifier)
        .background(theme.background)
        .testTag("reader_root_container"),
    ) {
      when (val state = loadState) {
        is ReaderLoadState.Loading -> {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              CircularProgressIndicator(color = theme.accent)
              Spacer(modifier = Modifier.height(12.dp))
              Text("Открываем книгу...", color = theme.mutedText)
            }
          }
        }

        is ReaderLoadState.Error -> {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .windowInsetsPadding(WindowInsets.safeDrawing)
              .padding(24.dp),
            contentAlignment = Alignment.Center,
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = state.message,
                style = MaterialTheme.typography.titleMedium,
                color = theme.text,
                textAlign = TextAlign.Center,
              )
              Spacer(modifier = Modifier.height(16.dp))
              Button(
                onClick = onNavigateBack,
                colors = ButtonDefaults.buttonColors(
                  containerColor = theme.accent,
                  contentColor = theme.onAccent,
                ),
              ) {
                Text("Вернуться на полку")
              }
            }
          }
        }

      is ReaderLoadState.Ready -> {
        val content = state.content
        val unitIdx = state.currentUnitIndex
        val ttsForBook = ttsState.takeIf { it.isActive && it.bookId == state.book.id }
        val paginator = remember(state.book.id) { ChapterPaginator(maxCachedChapters = 64) }
        val pageNavHandle = remember(state.book.id) { PageNavigationHandle() }
        val isPagesMode = content is LoadedBookContent.Reflowable &&
          preferences.readingMode == ReaderReadingMode.PAGES

        LaunchedEffect(
          ttsForBook?.isActive,
          ttsForBook?.chapterIndex,
          ttsForBook?.activeParagraphIndex,
        ) {
          val active = ttsForBook ?: return@LaunchedEffect
          if (active.activeParagraphIndex >= 0) {
            viewModel.syncFromTts(
              Locator(
                chapterIndex = active.chapterIndex,
                paragraphIndex = active.activeParagraphIndex,
                charOffset = 0,
              )
            )
          }
        }

        LaunchedEffect(ttsState.errorMessage) {
          val err = ttsState.errorMessage
          val relevant = !ttsState.isActive || ttsState.bookId == state.book.id
          if (relevant && !err.isNullOrBlank()) {
            snackbarHostState.showSnackbar(err)
          }
        }

        val launchTtsFromCurrent: () -> Unit = {
          val reflowable = content as? LoadedBookContent.Reflowable
          if (reflowable != null) {
            val startPara = currentLocator.paragraphIndex.coerceAtLeast(0)
            val needsPermissionPrompt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
              ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
              ) != PackageManager.PERMISSION_GRANTED &&
              !preferences.ttsNotificationPrompted
            if (needsPermissionPrompt) {
              pendingTtsStartParagraph = startPara
              viewModel.markTtsNotificationPrompted()
              notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
              ttsController.startBookPlayback(
                book = state.book,
                content = reflowable,
                startChapterIndex = unitIdx,
                startParagraphIndex = startPara,
                speechSpeed = preferences.ttsSpeed,
                pitch = preferences.ttsPitch,
                voiceName = preferences.ttsVoiceName,
              )
            }
          }
        }

        when (content) {
          is LoadedBookContent.Reflowable -> {
            val chapter = content.chapters.getOrNull(unitIdx) ?: content.chapters.first()
            val chapterQuotedParagraphs = remember(quotes, unitIdx) {
              quotes.filter { it.locator.chapterIndex == unitIdx }.map { it.locator.paragraphIndex }.toSet()
            }
            val activeTtsParagraphInChapter = ttsForBook
              ?.takeIf { it.chapterIndex == unitIdx && it.activeParagraphIndex >= 0 }
              ?.activeParagraphIndex

            if (preferences.readingMode == ReaderReadingMode.PAGES) {
              DisposableEffect(preferences.volumeKeyNavigation, unitIdx, content.chapters.size) {
                if (preferences.volumeKeyNavigation) {
                  registerVolumeKeyHandler { isVolumeDown ->
                    if (isVolumeDown) pageNavHandle.nextPage() else pageNavHandle.previousPage()
                    true
                  }
                } else {
                  registerVolumeKeyHandler(null)
                }
                onDispose { registerVolumeKeyHandler(null) }
              }

              ReflowablePagesReader(
                bookId = state.book.id,
                content = content,
                unitIdx = unitIdx,
                initialLocator = state.initialLocator,
                currentLocatorProvider = { viewModel.currentLocator.value },
                preferences = preferences,
                quotedParagraphIndices = chapterQuotedParagraphs,
                ttsActiveParagraphIndex = activeTtsParagraphInChapter,
                paginator = paginator,
                pageNavHandle = pageNavHandle,
                onPageChanged = { page, isLastPageOfBook ->
                  viewModel.onPageChanged(page, isLastPageOfBook)
                },
                onPrevChapterToEnd = { viewModel.previousUnitToEnd() },
                onNextChapter = { viewModel.nextUnit() },
                onToggleBars = { barsVisible = !barsVisible },
                onLongPressParagraph = { chapterTitle, paraIdx ->
                  chapter.paragraphs.getOrNull(paraIdx)?.let { para ->
                    selectedParagraphForQuote = chapterTitle to para
                  }
                },
              )
            } else {
              val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = state.initialLocator.paragraphIndex
                  .coerceIn(0, (chapter.paragraphs.size - 1).coerceAtLeast(0)),
              )

              LaunchedEffect(unitIdx, state.initialLocator) {
                val targetIdx = state.initialLocator.paragraphIndex.coerceIn(
                  0,
                  (chapter.paragraphs.size - 1).coerceAtLeast(0),
                )
                listState.scrollToItem(targetIdx)
                val targetCharOffset = state.initialLocator.charOffset
                val paraLen = chapter.paragraphs.getOrNull(targetIdx)?.text?.length ?: 0
                if (targetCharOffset > 0 && paraLen > 0) {
                  withFrameNanos { }
                  val itemSize = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == targetIdx }?.size ?: 0
                  if (itemSize > 0) {
                    val pxOffset = ((targetCharOffset.toFloat() / paraLen.toFloat()) * itemSize)
                      .roundToInt()
                      .coerceIn(0, (itemSize - 1).coerceAtLeast(0))
                    if (pxOffset > 0) {
                      listState.scrollToItem(targetIdx, pxOffset)
                    }
                  }
                }
              }

              LaunchedEffect(activeTtsParagraphInChapter, chapter) {
                val paraIdx = activeTtsParagraphInChapter ?: return@LaunchedEffect
                val itemIdx = chapter.paragraphs.indexOfFirst { it.index == paraIdx }
                if (itemIdx >= 0) {
                  val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == itemIdx }
                  if (!isVisible) {
                    listState.animateScrollToItem(itemIdx)
                  }
                }
              }

              LaunchedEffect(listState, unitIdx, chapter) {
                snapshotFlow {
                  val firstIdx = listState.firstVisibleItemIndex
                  val firstOffset = listState.firstVisibleItemScrollOffset
                  val atEnd = !listState.canScrollForward &&
                    listState.canScrollBackward &&
                    unitIdx == content.chapters.lastIndex
                  Triple(firstIdx, firstOffset / 24, atEnd)
                }
                  .distinctUntilChanged()
                  .collect { (firstIdx, _, atEnd) ->
                    if (chapter.paragraphs.isEmpty()) return@collect
                    if (atEnd) {
                      val lastIdx = chapter.paragraphs.lastIndex
                      val lastLen = chapter.paragraphs[lastIdx].text.length
                      viewModel.onScrollPositionChanged(lastIdx, lastLen)
                    } else {
                      val safeIdx = firstIdx.coerceIn(0, chapter.paragraphs.lastIndex)
                      val para = chapter.paragraphs[safeIdx]
                      val itemSize = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == safeIdx }?.size ?: 0
                      val scrollPx = listState.firstVisibleItemScrollOffset.coerceAtLeast(0)
                      val estimatedChar = if (itemSize > 0 && para.text.isNotEmpty()) {
                        ((scrollPx.toFloat() / itemSize.toFloat()) * para.text.length)
                          .toInt()
                          .coerceIn(0, para.text.length)
                      } else {
                        0
                      }
                      viewModel.onScrollPositionChanged(safeIdx, estimatedChar)
                    }
                  }
              }

              DisposableEffect(preferences.volumeKeyNavigation, unitIdx, content.chapters.size) {
                if (preferences.volumeKeyNavigation) {
                  registerVolumeKeyHandler { isVolumeDown ->
                    coroutineScope.launch {
                      if (isVolumeDown) {
                        if (listState.canScrollForward) {
                          val nextIdx = (listState.firstVisibleItemIndex + 4)
                            .coerceAtMost((chapter.paragraphs.size - 1).coerceAtLeast(0))
                          listState.animateScrollToItem(nextIdx)
                        } else {
                          viewModel.nextUnit()
                        }
                      } else {
                        if (listState.canScrollBackward) {
                          val prevIdx = (listState.firstVisibleItemIndex - 4).coerceAtLeast(0)
                          listState.animateScrollToItem(prevIdx)
                        } else {
                          viewModel.previousUnit()
                        }
                      }
                    }
                    true
                  }
                } else {
                  registerVolumeKeyHandler(null)
                }
                onDispose { registerVolumeKeyHandler(null) }
              }

              val needsSyntheticHeader = chapter.title.isNotBlank() &&
                chapter.paragraphs.firstOrNull()?.kind != ParagraphKind.HEADING
              val maxTextColumnDp = ReaderParagraphStyleResolver.maxLineColumnWidthDp(preferences)
              val maxContentWidthDp = maxTextColumnDp + (preferences.horizontalMarginDp * 2).dp

              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                  ) { barsVisible = !barsVisible },
                contentAlignment = Alignment.TopCenter,
              ) {
                LazyColumn(
                  state = listState,
                  modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = maxContentWidthDp),
                  contentPadding = PaddingValues(
                    start = preferences.horizontalMarginDp.dp,
                    end = preferences.horizontalMarginDp.dp,
                    top = 92.dp,
                    bottom = 112.dp,
                  ),
                  verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                  if (chapter.paragraphs.isEmpty() && needsSyntheticHeader) {
                    item(key = "chapter_empty_header_$unitIdx") {
                      ChapterHeroHeading(
                        title = chapter.title,
                        preferences = preferences,
                        theme = theme,
                      )
                    }
                  }

                  items(
                    items = chapter.paragraphs,
                    key = { "${unitIdx}_${it.index}" },
                  ) { paragraph ->
                    val isQuoted = chapterQuotedParagraphs.contains(paragraph.index)
                    val isTtsActive = activeTtsParagraphInChapter == paragraph.index
                    Column(modifier = Modifier.fillMaxWidth()) {
                      if (needsSyntheticHeader && paragraph.index == 0) {
                        ChapterHeroHeading(
                          title = chapter.title,
                          preferences = preferences,
                          theme = theme,
                        )
                      }
                      ParagraphView(
                        paragraph = paragraph,
                        preferences = preferences,
                        theme = theme,
                        isQuoted = isQuoted,
                        isTtsActive = isTtsActive,
                        onToggleBars = { barsVisible = !barsVisible },
                        onLongClickParagraph = {
                          if (paragraph.text.length > 8) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedParagraphForQuote = chapter.title to paragraph
                          }
                        },
                      )
                    }
                  }

                  item(key = "chapter_footer_$unitIdx") {
                    ChapterFooterControls(
                      hasPrevious = unitIdx > 0,
                      hasNext = unitIdx + 1 < content.chapters.size,
                      textColor = theme.text,
                      outlineColor = theme.outline,
                      accentColor = theme.accent,
                      onAccentColor = theme.onAccent,
                      onPrevious = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.previousUnit()
                        if (ttsForBook != null) {
                          ttsController.seekTo((unitIdx - 1).coerceAtLeast(0), 0)
                        }
                      },
                      onNext = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.nextUnit()
                        if (ttsForBook != null) {
                          ttsController.seekTo((unitIdx + 1).coerceAtMost(content.chapters.lastIndex), 0)
                        }
                      },
                    )
                  }
                }
              }
            }
          }

          is LoadedBookContent.PdfDocumentContent -> {
            DisposableEffect(preferences.volumeKeyNavigation, unitIdx, content.pageCount) {
              if (preferences.volumeKeyNavigation) {
                registerVolumeKeyHandler { isVolumeDown ->
                  if (isVolumeDown) viewModel.nextUnit() else viewModel.previousUnit()
                  true
                }
              } else {
                registerVolumeKeyHandler(null)
              }
              onDispose { registerVolumeKeyHandler(null) }
            }

            PdfPageViewer(
              filePath = content.filePath,
              pageIndex = unitIdx,
              invertForNight = theme.isDark,
              accentColor = theme.accent,
              mutedColor = theme.mutedText,
              onToggleBars = { barsVisible = !barsVisible },
            )
          }
        }

        val totalUnits = when (content) {
          is LoadedBookContent.Reflowable -> content.chapters.size
          is LoadedBookContent.PdfDocumentContent -> content.pageCount
        }.coerceAtLeast(1)

        val unitSubtitle = when (content) {
          is LoadedBookContent.Reflowable -> {
            val ch = content.chapters.getOrNull(unitIdx)
            val title = ch?.title ?: "Часть ${unitIdx + 1}"
            if (isPagesMode && pageNavHandle.totalPagesInChapter > 0) {
              "$title • стр. ${pageNavHandle.currentPageInChapter + 1}/${pageNavHandle.totalPagesInChapter}"
            } else {
              "$title • ${unitIdx + 1}/$totalUnits"
            }
          }
          is LoadedBookContent.PdfDocumentContent -> "Страница ${unitIdx + 1} из $totalUnits"
        }

        val bookmarkScale = remember { Animatable(1f) }
        var prevBookmarked by remember { mutableStateOf(isBookmarked) }
        LaunchedEffect(isBookmarked, animationsEnabled) {
          if (isBookmarked != prevBookmarked) {
            prevBookmarked = isBookmarked
            if (animationsEnabled) {
              bookmarkScale.snapTo(1f)
              bookmarkScale.animateTo(
                targetValue = 1.28f,
                animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing),
              )
              bookmarkScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.45f, stiffness = 420f),
              )
            } else {
              bookmarkScale.snapTo(1f)
            }
          }
        }
        val bookmarkTint by animateColorAsState(
          targetValue = if (isBookmarked) theme.accent else theme.text,
          animationSpec = tween(durationMillis = if (animationsEnabled) 200 else 0),
          label = "reader_bookmark_tint",
        )

        AnimatedVisibility(
          visible = barsVisible,
          enter = fadeIn(tween(barsDurationMs)) +
            slideInVertically(tween(barsDurationMs, easing = FastOutSlowInEasing)) { -it },
          exit = fadeOut(tween(barsDurationMs)) +
            slideOutVertically(tween(barsDurationMs, easing = FastOutSlowInEasing)) { -it },
          modifier = Modifier.align(Alignment.TopCenter),
        ) {
          Surface(
            color = theme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier
              .fillMaxWidth()
              .graphicsLayer {
                alpha = 1f - animatedBarsBackProgress * 0.45f
                translationY = -animatedBarsBackProgress * 28f
              },
          ) {
            Column(modifier = Modifier.fillMaxWidth()) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .statusBarsPadding()
                  .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                IconButton(
                  onClick = {
                    viewModel.flushProgressNow()
                    onNavigateBack()
                  },
                  modifier = Modifier.testTag("reader_back_button"),
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = theme.text,
                  )
                }

                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = state.book.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text = unitSubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }

                if (content is LoadedBookContent.Reflowable) {
                  IconButton(
                    onClick = { showSearchSheet = true },
                    modifier = Modifier.testTag("reader_search_button"),
                  ) {
                    Icon(
                      imageVector = Icons.Default.Search,
                      contentDescription = stringResource(R.string.cd_search_in_book),
                      tint = theme.text,
                    )
                  }

                  IconButton(
                    onClick = {
                      if (ttsForBook != null) {
                        ttsController.togglePlayPause()
                      } else {
                        launchTtsFromCurrent()
                      }
                    },
                    modifier = Modifier.testTag("reader_tts_button"),
                  ) {
                    Icon(
                      imageVector = when {
                        ttsForBook?.isPlaying == true -> Icons.Default.Pause
                        ttsForBook?.isPaused == true -> Icons.Default.PlayArrow
                        else -> Icons.Default.RecordVoiceOver
                      },
                      contentDescription = stringResource(R.string.cd_tts),
                      tint = if (ttsForBook != null) theme.accent else theme.text,
                    )
                  }
                }

                IconButton(
                  onClick = {
                    val performed = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    if (!performed) {
                      haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    viewModel.toggleBookmarkForCurrentUnit()
                  },
                  modifier = Modifier.testTag("reader_bookmark_button"),
                ) {
                  AnimatedContent(
                    targetState = isBookmarked,
                    transitionSpec = {
                      if (animationsEnabled) {
                        (fadeIn(tween(150)) + scaleIn(initialScale = 0.72f, animationSpec = tween(180))) togetherWith
                          (fadeOut(tween(120)) + scaleOut(targetScale = 0.72f, animationSpec = tween(120)))
                      } else {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                      }
                    },
                    label = "reader_bookmark_icon",
                  ) { bookmarked ->
                    Icon(
                      imageVector = if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                      contentDescription = stringResource(R.string.cd_bookmark),
                      tint = bookmarkTint,
                      modifier = Modifier.graphicsLayer {
                        scaleX = bookmarkScale.value
                        scaleY = bookmarkScale.value
                      },
                    )
                  }
                }

                IconButton(
                  onClick = { showNavSheet = true },
                  modifier = Modifier.testTag("reader_toc_button"),
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                    contentDescription = stringResource(R.string.cd_toc),
                    tint = theme.text,
                  )
                }

                IconButton(
                  onClick = { showAppearanceSheet = true },
                  modifier = Modifier.testTag("reader_appearance_button"),
                ) {
                  Icon(
                    imageVector = Icons.Default.FormatSize,
                    contentDescription = stringResource(R.string.cd_appearance),
                    tint = theme.text,
                  )
                }
              }
              HorizontalDivider(
                thickness = 0.5.dp,
                color = theme.mutedText.copy(alpha = 0.28f),
              )
            }
          }
        }

        val animatedProgress by animateFloatAsState(
          targetValue = progressPercent.coerceIn(0f, 1f),
          animationSpec = if (animationsEnabled) spring() else snap(),
          label = "reader_hidden_progress",
        )

        AnimatedVisibility(
          visible = !barsVisible,
          enter = fadeIn(tween(barsDurationMs)),
          exit = fadeOut(tween(barsDurationMs)),
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding(),
        ) {
          val trackBg = theme.inactiveTrackColor(theme.background)
          Canvas(
            modifier = Modifier
              .fillMaxWidth()
              .height(2.dp)
              .testTag("reader_hidden_progress_line"),
          ) {
            drawRect(color = trackBg)
            val activeWidth = size.width * animatedProgress
            if (activeWidth > 0f) {
              drawRect(
                color = theme.accent,
                size = Size(activeWidth, size.height),
              )
            }
          }
        }

        AnimatedVisibility(
          visible = ttsForBook != null && !barsVisible,
          enter = fadeIn(tween(barsDurationMs)),
          exit = fadeOut(tween(barsDurationMs)),
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        ) {
          TtsMiniPill(
            isPlaying = ttsForBook?.isPlaying == true,
            speed = preferences.ttsSpeed,
            surfaceColor = theme.surface,
            textColor = theme.text,
            accentColor = theme.accent,
            onPrevParagraph = { ttsController.previousParagraph() },
            onTogglePlayPause = { ttsController.togglePlayPause() },
            onNextParagraph = { ttsController.nextParagraph() },
            onSpeedStep = {
              val nextSpeed = when {
                preferences.ttsSpeed < 0.95f -> 1.0f
                preferences.ttsSpeed < 1.12f -> 1.15f
                preferences.ttsSpeed < 1.27f -> 1.3f
                else -> 0.85f
              }
              viewModel.setTtsSpeed(nextSpeed)
              ttsController.setSpeed(nextSpeed)
            },
            onStop = { ttsController.stop() },
          )
        }

        AnimatedVisibility(
          visible = barsVisible,
          enter = fadeIn(tween(barsDurationMs)) +
            slideInVertically(tween(barsDurationMs, easing = FastOutSlowInEasing)) { it },
          exit = fadeOut(tween(barsDurationMs)) +
            slideOutVertically(tween(barsDurationMs, easing = FastOutSlowInEasing)) { it },
          modifier = Modifier.align(Alignment.BottomCenter),
        ) {
          val inactiveBarTrackColor = theme.inactiveTrackColor(theme.surface)
          val pct = (progressPercent * 100f).roundToInt().coerceIn(0, 100)
          val bottomChapterPageText = when (content) {
            is LoadedBookContent.Reflowable -> {
              val chTitle = content.chapters.getOrNull(unitIdx)?.title?.takeIf { it.isNotBlank() }
                ?: "Глава ${unitIdx + 1}"
              if (isPagesMode && pageNavHandle.totalPagesInChapter > 0) {
                "$chTitle • стр. ${pageNavHandle.currentPageInChapter + 1}/${pageNavHandle.totalPagesInChapter}"
              } else {
                "$chTitle • гл. ${unitIdx + 1}/$totalUnits"
              }
            }
            is LoadedBookContent.PdfDocumentContent -> "Стр. ${unitIdx + 1}/$totalUnits"
          }

          Surface(
            color = theme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
              .fillMaxWidth()
              .graphicsLayer {
                alpha = 1f - animatedBarsBackProgress * 0.45f
                translationY = animatedBarsBackProgress * 28f
              }
              .testTag("reader_bottom_bar")
              .pointerInput(Unit) {
                detectTapGestures { }
              },
          ) {
            Column(modifier = Modifier.fillMaxWidth()) {
              HorizontalDivider(
                thickness = 0.5.dp,
                color = theme.mutedText.copy(alpha = 0.28f),
              )
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .navigationBarsPadding()
                  .padding(horizontal = 16.dp, vertical = 8.dp),
              ) {
                if (ttsForBook != null) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    IconButton(
                      onClick = { ttsController.previousParagraph() },
                      modifier = Modifier.testTag("reader_tts_prev"),
                    ) {
                      Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.tts_action_prev),
                        tint = theme.text,
                      )
                    }
                    IconButton(
                      onClick = { ttsController.togglePlayPause() },
                      modifier = Modifier.testTag("reader_tts_toggle"),
                    ) {
                      Icon(
                        imageVector = if (ttsForBook.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                          if (ttsForBook.isPlaying) R.string.tts_action_pause else R.string.tts_action_resume,
                        ),
                        tint = theme.accent,
                      )
                    }
                    IconButton(
                      onClick = { ttsController.nextParagraph() },
                      modifier = Modifier.testTag("reader_tts_next"),
                    ) {
                      Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.tts_action_next),
                        tint = theme.text,
                      )
                    }
                    Text(
                      text = "${String.format("%.1fx", preferences.ttsSpeed)}",
                      style = MaterialTheme.typography.labelMedium,
                      color = theme.accent,
                      maxLines = 1,
                    )
                    ThemedThinSlider(
                      value = preferences.ttsSpeed,
                      onValueChange = {
                        val clamped = it.coerceIn(0.7f, 1.3f)
                        viewModel.setTtsSpeed(clamped)
                        ttsController.setSpeed(clamped)
                      },
                      valueRange = 0.7f..1.3f,
                      activeColor = theme.accent,
                      inactiveColor = inactiveBarTrackColor,
                      modifier = Modifier
                        .weight(1f)
                        .testTag("reader_tts_speed_slider"),
                    )
                    IconButton(
                      onClick = { ttsController.stop() },
                      modifier = Modifier.testTag("reader_tts_stop"),
                    ) {
                      Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = stringResource(R.string.tts_action_stop),
                        tint = theme.text,
                      )
                    }
                  }
                }

                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = bottomChapterPageText,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                      .weight(1f)
                      .padding(end = 12.dp)
                      .testTag("reader_bottom_chapter_label"),
                  )
                  Text(
                    text = "$pct%",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = theme.accent,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.End,
                    modifier = Modifier.testTag("reader_bottom_percent_label"),
                  )
                }

                when {
                  isPagesMode && pageNavHandle.totalPagesInChapter > 1 -> {
                    ThemedThinSlider(
                      value = pageNavHandle.currentPageInChapter.toFloat(),
                      onValueChange = { pageNavHandle.goToPage(it.roundToInt()) },
                      valueRange = 0f..(pageNavHandle.totalPagesInChapter - 1).toFloat(),
                      activeColor = theme.accent,
                      inactiveColor = inactiveBarTrackColor,
                      modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reader_bottom_slider"),
                    )
                  }
                  totalUnits > 1 -> {
                    ThemedThinSlider(
                      value = unitIdx.toFloat(),
                      onValueChange = { viewModel.selectUnit(it.roundToInt(), 0) },
                      valueRange = 0f..(totalUnits - 1).toFloat(),
                      activeColor = theme.accent,
                      inactiveColor = inactiveBarTrackColor,
                      modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reader_bottom_slider"),
                    )
                  }
                  else -> {
                    ThemedThinSlider(
                      value = progressPercent.coerceIn(0f, 1f),
                      onValueChange = {},
                      valueRange = 0f..1f,
                      enabled = false,
                      activeColor = theme.accent,
                      inactiveColor = inactiveBarTrackColor,
                      modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reader_bottom_slider"),
                    )
                  }
                }
              }
            }
          }
        }

        if (showAppearanceSheet) {
          ReaderAppearanceSheet(
            preferences = preferences,
            onDismiss = { showAppearanceSheet = false },
            onThemeSelected = viewModel::setTheme,
            onReadingModeSelected = viewModel::setReadingMode,
            onFontSelected = viewModel::setFont,
            onFontSizeChanged = viewModel::setFontSize,
            onLineHeightChanged = viewModel::setLineHeight,
            onMarginChanged = viewModel::setHorizontalMargin,
            onJustifyChanged = viewModel::setJustifyText,
            onHyphenationChanged = viewModel::setHyphenationEnabled,
            onVolumeNavChanged = viewModel::setVolumeKeyNavigation,
          )
        }

        if (showNavSheet) {
          val toc = when (content) {
            is LoadedBookContent.Reflowable -> content.toc
            is LoadedBookContent.PdfDocumentContent -> content.toc
          }
          BookNavigationSheet(
            toc = toc,
            currentUnitIndex = unitIdx,
            bookmarks = bookmarks,
            quotes = quotes,
            onDismiss = { showNavSheet = false },
            onSelectUnit = { targetUnit, targetPara, targetChar ->
              viewModel.selectUnit(targetUnit, targetPara, targetChar)
              if (ttsForBook != null) {
                ttsController.seekTo(targetUnit, targetPara)
              }
            },
            onDeleteBookmark = viewModel::deleteBookmark,
            onDeleteQuote = viewModel::deleteQuote,
          )
        }

        if (showSearchSheet && content is LoadedBookContent.Reflowable) {
          ReaderSearchSheet(
            query = searchQuery,
            results = searchResults,
            isSearching = isSearching,
            onQueryChange = viewModel::updateSearchQuery,
            onSelectResult = { locator ->
              viewModel.selectUnit(
                unitIndex = locator.chapterIndex,
                paragraphIndex = locator.paragraphIndex,
                charOffset = locator.charOffset,
              )
              if (ttsForBook != null) {
                ttsController.seekTo(locator.chapterIndex, locator.paragraphIndex)
              }
            },
            onDismiss = { showSearchSheet = false },
          )
        }

        selectedParagraphForQuote?.let { (chapterTitle, paragraph) ->
          SaveQuoteDialog(
            paragraphText = paragraph.text,
            onDismiss = { selectedParagraphForQuote = null },
            onSave = { note, colorTag ->
              viewModel.saveQuote(
                locator = Locator(
                  chapterIndex = unitIdx,
                  paragraphIndex = paragraph.index,
                  charOffset = 0,
                ),
                unitTitle = chapterTitle,
                text = paragraph.text,
                note = note,
                colorTag = colorTag,
              )
              selectedParagraphForQuote = null
            },
          )
        }
      }
    }

      SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .navigationBarsPadding()
          .padding(bottom = 96.dp),
        snackbar = { data ->
          Snackbar(
            snackbarData = data,
            containerColor = theme.surfaceContainerHigh,
            contentColor = theme.text,
            actionColor = theme.accent,
          )
        },
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ParagraphView(
  paragraph: BookParagraph,
  preferences: ReaderPreferences,
  theme: AnimatedReaderColors,
  isQuoted: Boolean,
  isTtsActive: Boolean,
  onToggleBars: () -> Unit,
  onLongClickParagraph: () -> Unit,
) {
  val spec = remember(paragraph.kind, paragraph.text, preferences) {
    ReaderParagraphStyleResolver.resolve(paragraph.kind, paragraph.text, preferences)
  }

  val highlightBg = when {
    isTtsActive -> theme.accent.copy(alpha = 0.18f)
    isQuoted -> theme.accent.copy(alpha = 0.11f)
    else -> Color.Transparent
  }

  val modifier = Modifier
    .fillMaxWidth()
    .clip(RoundedCornerShape(8.dp))
    .background(highlightBg)
    .combinedClickable(
      interactionSource = remember { MutableInteractionSource() },
      indication = null,
      onClick = onToggleBars,
      onLongClick = onLongClickParagraph,
    )
    .padding(
      horizontal = if (isQuoted || isTtsActive) 8.dp else 0.dp,
      vertical = 0.dp,
    )

  when (paragraph.kind) {
    ParagraphKind.HEADING -> {
      Column(
        modifier = modifier.padding(
          top = spec.extraTopPaddingDp,
          bottom = spec.extraBottomPaddingDp,
        ),
      ) {
        Text(
          text = paragraph.text,
          style = spec.textStyle,
          color = theme.text,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
          modifier = Modifier
            .width(36.dp)
            .height(2.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(theme.accent.copy(alpha = 0.5f)),
        )
      }
    }

    ParagraphKind.SUBHEADING -> {
      Text(
        text = paragraph.text,
        style = spec.textStyle,
        color = theme.mutedText,
        modifier = modifier.padding(
          top = spec.extraTopPaddingDp,
          bottom = spec.extraBottomPaddingDp,
        ),
      )
    }

    ParagraphKind.QUOTE -> {
      Row(
        modifier = modifier
          .height(IntrinsicSize.Min)
          .padding(
            top = spec.extraTopPaddingDp,
            bottom = spec.extraBottomPaddingDp,
          ),
      ) {
        Box(
          modifier = Modifier
            .width(3.dp)
            .fillMaxHeight()
            .background(theme.accent.copy(alpha = 0.7f)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
          text = paragraph.text,
          style = spec.textStyle,
          color = theme.mutedText,
          modifier = Modifier.weight(1f),
        )
      }
    }

    ParagraphKind.POEM -> {
      Text(
        text = paragraph.text,
        style = spec.textStyle,
        color = theme.text,
        modifier = modifier.padding(
          start = spec.extraStartPaddingDp,
          top = spec.extraTopPaddingDp,
          bottom = spec.extraBottomPaddingDp,
        ),
      )
    }

    ParagraphKind.CODE -> {
      Surface(
        color = theme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.padding(vertical = 4.dp),
      ) {
        Text(
          text = paragraph.text,
          style = spec.textStyle,
          color = theme.text,
          modifier = Modifier.padding(10.dp),
        )
      }
    }

    ParagraphKind.BODY -> {
      Text(
        text = paragraph.text,
        style = spec.textStyle,
        textAlign = spec.textAlign,
        color = theme.text,
        modifier = modifier,
      )
    }

    ParagraphKind.IMAGE -> {
      BookInlineImage(
        imagePath = paragraph.imagePath,
        imageWidth = paragraph.imageWidth,
        imageHeight = paragraph.imageHeight,
        imageAlt = paragraph.imageAlt,
        paragraphIndex = paragraph.index,
        theme = theme,
        modifier = modifier.padding(
          top = spec.extraTopPaddingDp,
          bottom = spec.extraBottomPaddingDp,
        ),
      )
    }
  }
}

@Composable
private fun ChapterFooterControls(
  hasPrevious: Boolean,
  hasNext: Boolean,
  textColor: Color,
  outlineColor: Color,
  accentColor: Color,
  onAccentColor: Color,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 24.dp, bottom = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (hasPrevious) {
      OutlinedButton(
        onClick = onPrevious,
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, outlineColor),
        colors = ButtonDefaults.outlinedButtonColors(
          contentColor = textColor,
        ),
      ) {
        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = null, tint = textColor)
        Text(
          text = "Назад",
          color = textColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    if (hasNext) {
      Button(
        onClick = onNext,
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = accentColor,
          contentColor = onAccentColor,
        ),
      ) {
        Text(
          text = "Дальше",
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
      }
    }
  }
}

@Composable
private fun PdfPageViewer(
  filePath: String,
  pageIndex: Int,
  invertForNight: Boolean,
  accentColor: Color,
  mutedColor: Color,
  onToggleBars: () -> Unit,
) {
  var scale by remember(pageIndex) { mutableFloatStateOf(1f) }
  var offsetX by remember(pageIndex) { mutableFloatStateOf(0f) }
  var offsetY by remember(pageIndex) { mutableFloatStateOf(0f) }

  val nightMatrix = remember {
    ColorMatrix(
      floatArrayOf(
        -0.9f, 0f, 0f, 0f, 235f,
        0f, -0.9f, 0f, 0f, 232f,
        0f, 0f, -0.9f, 0f, 224f,
        0f, 0f, 0f, 1f, 0f,
      )
    )
  }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .pointerInput(pageIndex) {
        detectTapGestures(
          onTap = { onToggleBars() },
          onDoubleTap = {
            if (scale > 1.1f) {
              scale = 1f
              offsetX = 0f
              offsetY = 0f
            } else {
              scale = 2.0f
            }
          },
        )
      }
      .pointerInput(pageIndex) {
        detectTransformGestures { _, pan, zoom, _ ->
          val newScale = (scale * zoom).coerceIn(1f, 3.5f)
          scale = newScale
          if (newScale > 1.05f) {
            offsetX += pan.x
            offsetY += pan.y
          } else {
            offsetX = 0f
            offsetY = 0f
          }
        }
      },
    contentAlignment = Alignment.Center,
  ) {
    val targetWidthPx = with(LocalDensity.current) { maxWidth.roundToPx().coerceAtLeast(600) }
    val renderResult by produceState<Pair<Boolean, Bitmap?>>(
      initialValue = true to null,
      filePath,
      pageIndex,
      targetWidthPx,
    ) {
      val bmp = withContext(Dispatchers.IO) {
        PdfParser.renderPageBitmap(filePath, pageIndex, targetWidthPx)
      }
      value = false to bmp
    }

    val (isLoading, bmp) = renderResult
    when {
      isLoading -> CircularProgressIndicator(color = accentColor)
      bmp == null -> Text(
        text = "Не удалось отобразить страницу PDF.",
        style = MaterialTheme.typography.bodyMedium,
        color = mutedColor,
        modifier = Modifier.padding(24.dp),
      )
      else -> Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = "PDF страница ${pageIndex + 1}",
        contentScale = ContentScale.Fit,
        colorFilter = if (invertForNight) ColorFilter.colorMatrix(nightMatrix) else null,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp)
          .graphicsLayer(
            scaleX = scale,
            scaleY = scale,
            translationX = offsetX,
            translationY = offsetY,
          ),
      )
    }
  }
}

@Composable
private fun TtsMiniPill(
  isPlaying: Boolean,
  speed: Float,
  surfaceColor: Color,
  textColor: Color,
  accentColor: Color,
  onPrevParagraph: () -> Unit,
  onTogglePlayPause: () -> Unit,
  onNextParagraph: () -> Unit,
  onSpeedStep: () -> Unit,
  onStop: () -> Unit,
) {
  Surface(
    color = surfaceColor,
    shape = RoundedCornerShape(24.dp),
    shadowElevation = 6.dp,
    modifier = Modifier.testTag("reader_tts_mini_pill"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      IconButton(onClick = onPrevParagraph, modifier = Modifier.size(36.dp)) {
        Icon(
          imageVector = Icons.Default.SkipPrevious,
          contentDescription = stringResource(R.string.tts_action_prev),
          tint = textColor,
          modifier = Modifier.size(20.dp),
        )
      }
      IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(36.dp)) {
        Icon(
          imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
          contentDescription = stringResource(
            if (isPlaying) R.string.tts_action_pause else R.string.tts_action_resume,
          ),
          tint = accentColor,
          modifier = Modifier.size(20.dp),
        )
      }
      IconButton(onClick = onNextParagraph, modifier = Modifier.size(36.dp)) {
        Icon(
          imageVector = Icons.Default.SkipNext,
          contentDescription = stringResource(R.string.tts_action_next),
          tint = textColor,
          modifier = Modifier.size(20.dp),
        )
      }
      Text(
        text = String.format("%.1fx", speed),
        style = MaterialTheme.typography.labelMedium,
        color = textColor,
        maxLines = 1,
        modifier = Modifier
          .clickable(onClick = onSpeedStep)
          .padding(horizontal = 6.dp),
      )
      IconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
        Icon(
          imageVector = Icons.Default.Stop,
          contentDescription = stringResource(R.string.tts_action_stop),
          tint = textColor,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}
