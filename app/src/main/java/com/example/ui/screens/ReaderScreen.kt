package com.example.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.ReaderPreferences
import com.example.reader.engine.BookChapter
import com.example.reader.engine.BookParagraph
import com.example.reader.engine.LoadedBookContent
import com.example.reader.engine.ParagraphKind
import com.example.reader.engine.PdfParser
import com.example.reader.engine.TtsController
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.ReaderLoadState
import com.example.ui.viewmodel.ReaderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun ReaderScreen(
  viewModel: ReaderViewModel,
  onNavigateBack: () -> Unit,
  registerVolumeKeyHandler: (((Boolean) -> Boolean)?) -> Unit,
) {
  val loadState by viewModel.loadState.collectAsStateWithLifecycle()
  val preferences by viewModel.preferences.collectAsStateWithLifecycle()
  val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
  val quotes by viewModel.quotes.collectAsStateWithLifecycle()
  val isBookmarked by viewModel.isCurrentUnitBookmarked.collectAsStateWithLifecycle()

  val context = LocalContext.current
  val haptics = LocalHapticFeedback.current
  val coroutineScope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  var barsVisible by remember { mutableStateOf(true) }
  var showAppearanceSheet by remember { mutableStateOf(false) }
  var showNavSheet by remember { mutableStateOf(false) }
  var selectedParagraphForQuote by remember { mutableStateOf<Pair<String, BookParagraph>?>(null) }

  val ttsController = remember(context) { TtsController(context) }
  val ttsState by ttsController.state.collectAsStateWithLifecycle()

  DisposableEffect(Unit) {
    onDispose { ttsController.release() }
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

  val theme = preferences.readerTheme

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(theme.background),
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
            Button(onClick = onNavigateBack) {
              Text("Вернуться на полку")
            }
          }
        }
      }

      is ReaderLoadState.Ready -> {
        val content = state.content
        val unitIdx = state.currentUnitIndex

        when (content) {
          is LoadedBookContent.Reflowable -> {
            val chapter = content.chapters.getOrNull(unitIdx) ?: content.chapters.first()
            val listState = rememberLazyListState(
              initialFirstVisibleItemIndex = state.initialParagraphIndex
                .coerceIn(0, (chapter.paragraphs.size - 1).coerceAtLeast(0)),
            )

            LaunchedEffect(unitIdx, state.initialParagraphIndex) {
              val targetIdx = state.initialParagraphIndex.coerceIn(
                0,
                (chapter.paragraphs.size - 1).coerceAtLeast(0),
              )
              listState.scrollToItem(targetIdx)
            }

            LaunchedEffect(listState, unitIdx) {
              snapshotFlow { listState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect { visibleIdx ->
                  viewModel.updateParagraphProgress(visibleIdx)
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

            val chapterQuotedParagraphs = remember(quotes, unitIdx) {
              quotes.filter { it.unitIndex == unitIdx }.map { it.paragraphIndex }.toSet()
            }

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
                  .widthIn(max = 680.dp),
                contentPadding = PaddingValues(
                  start = preferences.horizontalMarginDp.dp,
                  end = preferences.horizontalMarginDp.dp,
                  top = 92.dp,
                  bottom = 112.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                items(
                  items = chapter.paragraphs,
                  key = { "${unitIdx}_${it.index}" },
                ) { paragraph ->
                  val isQuoted = chapterQuotedParagraphs.contains(paragraph.index)
                  val isTtsActive = ttsState.isSpeaking && ttsState.activeParagraphIndex == paragraph.index
                  ParagraphView(
                    paragraph = paragraph,
                    preferences = preferences,
                    isQuoted = isQuoted,
                    isTtsActive = isTtsActive,
                    onClickParagraph = {
                      if (paragraph.text.length > 8) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedParagraphForQuote = chapter.title to paragraph
                      } else {
                        barsVisible = !barsVisible
                      }
                    },
                  )
                }

                item(key = "chapter_footer_$unitIdx") {
                  ChapterFooterControls(
                    hasPrevious = unitIdx > 0,
                    hasNext = unitIdx + 1 < content.chapters.size,
                    textColor = theme.text,
                    accentColor = theme.accent,
                    onPrevious = {
                      ttsController.stop()
                      haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                      viewModel.previousUnit()
                    },
                    onNext = {
                      ttsController.stop()
                      haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                      viewModel.nextUnit()
                    },
                  )
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
            "${ch?.title ?: "Глава ${unitIdx + 1}"} • ${unitIdx + 1}/$totalUnits"
          }
          is LoadedBookContent.PdfDocumentContent -> "Страница ${unitIdx + 1} из $totalUnits"
        }

        AnimatedVisibility(
          visible = barsVisible,
          enter = fadeIn() + slideInVertically { -it },
          exit = fadeOut() + slideOutVertically { -it },
          modifier = Modifier.align(Alignment.TopCenter),
        ) {
          Surface(
            color = theme.surface.copy(alpha = 0.96f),
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              IconButton(
                onClick = {
                  ttsController.stop()
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
                val chapter = content.chapters.getOrNull(unitIdx)
                IconButton(
                  onClick = {
                    if (ttsState.isSpeaking) {
                      ttsController.stop()
                    } else if (chapter != null) {
                      ttsController.startSpeaking(
                        chapterParagraphs = chapter.paragraphs,
                        startFromParagraphIndex = 0,
                        speechSpeed = preferences.ttsSpeed,
                        onFinished = {
                          if (unitIdx + 1 < content.chapters.size) {
                            viewModel.nextUnit()
                          }
                        },
                      )
                    }
                  },
                  modifier = Modifier.testTag("reader_tts_button"),
                ) {
                  Icon(
                    imageVector = if (ttsState.isSpeaking) Icons.Default.Pause else Icons.Default.RecordVoiceOver,
                    contentDescription = stringResource(R.string.cd_tts),
                    tint = if (ttsState.isSpeaking) theme.accent else theme.text,
                  )
                }
              }

              IconButton(
                onClick = {
                  haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                  viewModel.toggleBookmarkForCurrentUnit()
                },
                modifier = Modifier.testTag("reader_bookmark_button"),
              ) {
                Icon(
                  imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                  contentDescription = stringResource(R.string.cd_bookmark),
                  tint = if (isBookmarked) theme.accent else theme.text,
                )
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
          }
        }

        AnimatedVisibility(
          visible = ttsState.isSpeaking && !barsVisible,
          enter = fadeIn(),
          exit = fadeOut(),
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        ) {
          TtsMiniPill(
            speed = preferences.ttsSpeed,
            surfaceColor = theme.surface,
            textColor = theme.text,
            accentColor = theme.accent,
            onSpeedStep = {
              val nextSpeed = when {
                preferences.ttsSpeed < 1.15f -> 1.25f
                preferences.ttsSpeed < 1.45f -> 1.5f
                else -> 1.0f
              }
              viewModel.setTtsSpeed(nextSpeed)
              ttsController.setSpeed(nextSpeed)
            },
            onStop = { ttsController.stop() },
          )
        }

        AnimatedVisibility(
          visible = barsVisible,
          enter = fadeIn() + slideInVertically { it },
          exit = fadeOut() + slideOutVertically { it },
          modifier = Modifier.align(Alignment.BottomCenter),
        ) {
          Surface(
            color = theme.surface.copy(alpha = 0.96f),
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
              if (ttsState.isSpeaking) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = "Озвучка • ${String.format("%.1fx", preferences.ttsSpeed)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.accent,
                  )
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                      value = preferences.ttsSpeed,
                      onValueChange = {
                        viewModel.setTtsSpeed(it)
                        ttsController.setSpeed(it)
                      },
                      valueRange = 0.7f..2.0f,
                      modifier = Modifier.width(120.dp),
                    )
                    IconButton(onClick = { ttsController.stop() }) {
                      Icon(Icons.Default.Stop, contentDescription = "Остановить озвучку", tint = theme.text)
                    }
                  }
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                IconButton(
                  onClick = { viewModel.previousUnit() },
                  enabled = unitIdx > 0,
                  modifier = Modifier.testTag("reader_prev_unit"),
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                    contentDescription = "Назад",
                    tint = if (unitIdx > 0) theme.text else theme.mutedText.copy(alpha = 0.4f),
                  )
                }

                if (totalUnits > 1) {
                  Slider(
                    value = unitIdx.toFloat(),
                    onValueChange = { viewModel.selectUnit(it.roundToInt(), 0) },
                    valueRange = 0f..(totalUnits - 1).toFloat(),
                    modifier = Modifier.weight(1f),
                  )
                } else {
                  Spacer(modifier = Modifier.weight(1f))
                }

                IconButton(
                  onClick = { viewModel.nextUnit() },
                  enabled = unitIdx + 1 < totalUnits,
                  modifier = Modifier.testTag("reader_next_unit"),
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                    contentDescription = "Вперёд",
                    tint = if (unitIdx + 1 < totalUnits) theme.text else theme.mutedText.copy(alpha = 0.4f),
                  )
                }

                val pct = (((unitIdx + 1).toFloat() / totalUnits) * 100).roundToInt()
                Text(
                  text = "$pct%",
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                  color = theme.accent,
                  modifier = Modifier.padding(start = 6.dp),
                )
              }
            }
          }
        }

        if (showAppearanceSheet) {
          ReaderAppearanceSheet(
            preferences = preferences,
            onDismiss = { showAppearanceSheet = false },
            onThemeSelected = viewModel::setTheme,
            onFontSelected = viewModel::setFont,
            onFontSizeChanged = viewModel::setFontSize,
            onLineHeightChanged = viewModel::setLineHeight,
            onMarginChanged = viewModel::setHorizontalMargin,
            onJustifyChanged = viewModel::setJustifyText,
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
            onSelectUnit = { targetUnit, targetPara ->
              ttsController.stop()
              viewModel.selectUnit(targetUnit, targetPara)
            },
            onDeleteBookmark = viewModel::deleteBookmark,
            onDeleteQuote = viewModel::deleteQuote,
          )
        }

        selectedParagraphForQuote?.let { (chapterTitle, paragraph) ->
          SaveQuoteDialog(
            paragraphText = paragraph.text,
            onDismiss = { selectedParagraphForQuote = null },
            onSave = { note, colorTag ->
              viewModel.saveQuote(
                unitIndex = unitIdx,
                paragraphIndex = paragraph.index,
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
        .padding(bottom = 84.dp),
    )
  }
}

@Composable
private fun ParagraphView(
  paragraph: BookParagraph,
  preferences: ReaderPreferences,
  isQuoted: Boolean,
  isTtsActive: Boolean,
  onClickParagraph: () -> Unit,
) {
  val theme = preferences.readerTheme
  val baseSize = preferences.fontSizeSp
  val lineHeight = (baseSize * preferences.lineHeightMultiplier).sp

  val highlightBg = when {
    isTtsActive -> theme.accent.copy(alpha = 0.18f)
    isQuoted -> theme.accent.copy(alpha = 0.11f)
    else -> Color.Transparent
  }

  val modifier = Modifier
    .fillMaxWidth()
    .clip(RoundedCornerShape(8.dp))
    .background(highlightBg)
    .clickable(onClick = onClickParagraph)
    .padding(horizontal = if (isQuoted || isTtsActive) 8.dp else 0.dp, vertical = 4.dp)

  when (paragraph.kind) {
    ParagraphKind.HEADING -> {
      Text(
        text = paragraph.text,
        style = MaterialTheme.typography.headlineSmall.copy(
          fontFamily = preferences.fontOption.fontFamily,
          fontSize = (baseSize + 5f).sp,
          lineHeight = ((baseSize + 5f) * 1.28f).sp,
          fontWeight = FontWeight.SemiBold,
        ),
        color = theme.text,
        modifier = modifier.padding(top = 12.dp, bottom = 6.dp),
      )
    }

    ParagraphKind.SUBHEADING -> {
      Text(
        text = paragraph.text,
        style = MaterialTheme.typography.titleMedium.copy(
          fontFamily = preferences.fontOption.fontFamily,
          fontSize = (baseSize + 2f).sp,
          lineHeight = ((baseSize + 2f) * 1.35f).sp,
          fontWeight = FontWeight.Medium,
        ),
        color = theme.mutedText,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
      )
    }

    ParagraphKind.QUOTE -> {
      Row(
        modifier = modifier.padding(vertical = 4.dp),
      ) {
        Box(
          modifier = Modifier
            .width(3.dp)
            .height(28.dp)
            .background(theme.accent.copy(alpha = 0.7f)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
          text = paragraph.text,
          style = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = preferences.fontOption.fontFamily,
            fontSize = (baseSize - 1f).sp,
            lineHeight = lineHeight,
            fontStyle = FontStyle.Italic,
          ),
          color = theme.mutedText,
          modifier = Modifier.weight(1f),
        )
      }
    }

    ParagraphKind.POEM -> {
      Text(
        text = paragraph.text,
        style = MaterialTheme.typography.bodyLarge.copy(
          fontFamily = preferences.fontOption.fontFamily,
          fontSize = baseSize.sp,
          lineHeight = lineHeight,
          fontStyle = FontStyle.Italic,
        ),
        color = theme.text,
        modifier = modifier.padding(start = 20.dp),
      )
    }

    ParagraphKind.CODE -> {
      Surface(
        color = theme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
      ) {
        Text(
          text = paragraph.text,
          style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = (baseSize - 2f).coerceAtLeast(12f).sp,
            lineHeight = (baseSize * 1.35f).sp,
          ),
          color = theme.text,
          modifier = Modifier.padding(10.dp),
        )
      }
    }

    ParagraphKind.BODY -> {
      Text(
        text = paragraph.text,
        style = MaterialTheme.typography.bodyLarge.copy(
          fontFamily = preferences.fontOption.fontFamily,
          fontSize = baseSize.sp,
          lineHeight = lineHeight,
        ),
        textAlign = if (preferences.justifyText) TextAlign.Justify else TextAlign.Start,
        color = theme.text,
        modifier = modifier,
      )
    }
  }
}

@Composable
private fun ChapterFooterControls(
  hasPrevious: Boolean,
  hasNext: Boolean,
  textColor: Color,
  accentColor: Color,
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
      ) {
        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = null, tint = textColor)
        Text("Предыдущая", color = textColor)
      }
    }
    if (hasNext) {
      Button(
        onClick = onNext,
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
      ) {
        Text("Следующая глава")
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
    val pageBitmap by produceState<Bitmap?>(initialValue = null, filePath, pageIndex, targetWidthPx) {
      value = withContext(Dispatchers.IO) {
        PdfParser.renderPageBitmap(filePath, pageIndex, targetWidthPx)
      }
    }

    val bmp = pageBitmap
    if (bmp == null) {
      CircularProgressIndicator()
    } else {
      Image(
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
  speed: Float,
  surfaceColor: Color,
  textColor: Color,
  accentColor: Color,
  onSpeedStep: () -> Unit,
  onStop: () -> Unit,
) {
  Surface(
    color = surfaceColor,
    shape = RoundedCornerShape(24.dp),
    shadowElevation = 6.dp,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(Icons.Default.PlayArrow, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
      Text(
        text = "Озвучка ${String.format("%.1fx", speed)}",
        style = MaterialTheme.typography.labelMedium,
        color = textColor,
        modifier = Modifier.clickable(onClick = onSpeedStep),
      )
      IconButton(onClick = onStop, modifier = Modifier.size(32.dp)) {
        Icon(Icons.Default.Stop, contentDescription = "Остановить", tint = textColor, modifier = Modifier.size(18.dp))
      }
    }
  }
}
