package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.data.local.ReaderPreferences
import com.example.reader.engine.BookChapter
import com.example.reader.engine.BookPage
import com.example.reader.engine.ChapterPaginator
import com.example.reader.engine.ComposeTextLineMeasurer
import com.example.reader.engine.LoadedBookContent
import com.example.reader.engine.Locator
import com.example.reader.engine.PageSlice
import com.example.reader.engine.PaginationCacheKey
import com.example.reader.engine.ParagraphKind
import com.example.reader.engine.ReaderParagraphStyleResolver
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Stable
class PageNavigationHandle {
  var totalPagesInChapter by mutableIntStateOf(0)
  var currentPageInChapter by mutableIntStateOf(0)
  internal var onGoToPage: ((Int) -> Unit)? = null
  internal var onStepNext: (() -> Unit)? = null
  internal var onStepPrev: (() -> Unit)? = null

  fun goToPage(page: Int) {
    onGoToPage?.invoke(page)
  }

  fun nextPage() {
    onStepNext?.invoke()
  }

  fun previousPage() {
    onStepPrev?.invoke()
  }
}

private data class PaginatedChapterSnapshot(
  val key: PaginationCacheKey,
  val chapter: BookChapter,
  val pages: List<BookPage>,
  val initialPage: Int,
)

@Composable
fun ReflowablePagesReader(
  bookId: Long,
  content: LoadedBookContent.Reflowable,
  unitIdx: Int,
  initialLocator: Locator,
  currentLocatorProvider: () -> Locator,
  preferences: ReaderPreferences,
  quotedParagraphIndices: Set<Int>,
  ttsActiveParagraphIndex: Int?,
  paginator: ChapterPaginator,
  pageNavHandle: PageNavigationHandle,
  onPageChanged: (BookPage, isLastPageOfBook: Boolean) -> Unit,
  onPrevChapterToEnd: () -> Unit,
  onNextChapter: () -> Unit,
  onToggleBars: () -> Unit,
  onLongPressParagraph: (chapterTitle: String, paragraphIndex: Int) -> Unit,
) {
  val density = LocalDensity.current
  val fontFamilyResolver = LocalFontFamilyResolver.current
  val layoutDirection = LocalLayoutDirection.current
  val haptics = LocalHapticFeedback.current
  val coroutineScope = rememberCoroutineScope()
  val theme = preferences.readerTheme

  val measurer = remember(fontFamilyResolver, density, layoutDirection) {
    ComposeTextLineMeasurer(
      fontFamilyResolver = fontFamilyResolver,
      density = density,
      layoutDirection = layoutDirection,
    )
  }

  val statusTopDp = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
  val navBottomDp = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .background(theme.background)
      .testTag("reader_pages_container"),
  ) {
    val topPaddingDp = statusTopDp + 24.dp
    val bottomPaddingDp = navBottomDp + 38.dp
    val maxContentWidthDp = maxWidth.coerceAtMost(680.dp)
    val horizMarginDp = preferences.horizontalMarginDp.dp

    val viewportWidthPx = with(density) {
      (maxContentWidthDp - horizMarginDp * 2).toPx().roundToInt().coerceAtLeast(100)
    }
    val viewportHeightPx = with(density) {
      (maxHeight - topPaddingDp - bottomPaddingDp).toPx().roundToInt().coerceAtLeast(160)
    }

    val chapter = content.chapters.getOrNull(unitIdx) ?: content.chapters.first()
    val cacheKey = PaginationCacheKey(
      bookId = bookId,
      chapterIndex = unitIdx,
      fontOption = preferences.fontOption,
      fontSizeSp = preferences.fontSizeSp,
      lineHeightMultiplier = preferences.lineHeightMultiplier,
      horizontalMarginDp = preferences.horizontalMarginDp,
      justifyText = preferences.justifyText,
      hyphenationEnabled = preferences.hyphenationEnabled,
      viewportWidthPx = viewportWidthPx,
      viewportHeightPx = viewportHeightPx,
      density = density.density,
      fontScale = density.fontScale,
    )

    val cachedNow = remember(cacheKey) { paginator.getCached(cacheKey) }
    var asyncSnapshot by remember { mutableStateOf<PaginatedChapterSnapshot?>(null) }

    LaunchedEffect(cacheKey) {
      if (cachedNow == null) {
        val computed = paginator.getOrPaginate(
          key = cacheKey,
          chapter = chapter,
          preferences = preferences,
          density = density,
          measurer = measurer,
        )
        val anchor = currentLocatorProvider().takeIf { it.chapterIndex == unitIdx } ?: initialLocator
        asyncSnapshot = PaginatedChapterSnapshot(
          key = cacheKey,
          chapter = chapter,
          pages = computed,
          initialPage = ChapterPaginator.findPageForLocator(computed, anchor),
        )
      }

      launch {
        if (unitIdx + 1 < content.chapters.size) {
          val nextChapter = content.chapters[unitIdx + 1]
          paginator.getOrPaginate(
            key = cacheKey.copy(chapterIndex = unitIdx + 1),
            chapter = nextChapter,
            preferences = preferences,
            density = density,
            measurer = measurer,
          )
        }
        if (unitIdx - 1 >= 0) {
          val prevChapter = content.chapters[unitIdx - 1]
          paginator.getOrPaginate(
            key = cacheKey.copy(chapterIndex = unitIdx - 1),
            chapter = prevChapter,
            preferences = preferences,
            density = density,
            measurer = measurer,
          )
        }
      }
    }

    val activeSnapshot = if (cachedNow != null) {
      remember(cacheKey, cachedNow) {
        val anchor = currentLocatorProvider().takeIf { it.chapterIndex == unitIdx } ?: initialLocator
        PaginatedChapterSnapshot(
          key = cacheKey,
          chapter = chapter,
          pages = cachedNow,
          initialPage = ChapterPaginator.findPageForLocator(cachedNow, anchor),
        )
      }
    } else {
      asyncSnapshot?.takeIf { it.key.chapterIndex == unitIdx }
    }

    if (activeSnapshot == null || activeSnapshot.pages.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = theme.accent)
      }
      return@BoxWithConstraints
    }

    val chapterSlideSpec = remember {
      tween<IntOffset>(
        durationMillis = 240,
        easing = FastOutSlowInEasing,
      )
    }
    val chapterFadeSpec = remember { tween<Float>(durationMillis = 180) }

    AnimatedContent(
      targetState = activeSnapshot.key.chapterIndex,
      transitionSpec = {
        val forward = targetState >= initialState
        val enter = slideInHorizontally(chapterSlideSpec) { w -> if (forward) w else -w } +
          fadeIn(chapterFadeSpec)
        val exit = slideOutHorizontally(chapterSlideSpec) { w -> if (forward) -w else w } +
          fadeOut(chapterFadeSpec)
        enter togetherWith exit
      },
      label = "chapter_page_transition",
      modifier = Modifier.fillMaxSize(),
    ) { renderedChapterIdx ->
      if (renderedChapterIdx != activeSnapshot.key.chapterIndex) {
        Box(modifier = Modifier.fillMaxSize().background(theme.background))
        return@AnimatedContent
      }

      val snapKey = activeSnapshot.key
      val pages = activeSnapshot.pages
      val snapChapter = activeSnapshot.chapter
      val snapChapterIdx = snapKey.chapterIndex

      key(snapKey) {
        val pagerState = rememberPagerState(
          initialPage = activeSnapshot.initialPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
          pageCount = { pages.size.coerceAtLeast(1) },
        )

        val snapSpring = remember {
          spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
          )
        }
        val tapTurnTween = remember {
          tween<Float>(
            durationMillis = 220,
            easing = FastOutSlowInEasing,
          )
        }

        LaunchedEffect(pagerState, pages, snapChapterIdx) {
          pageNavHandle.totalPagesInChapter = pages.size
          snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { pageIdx ->
              val safeIdx = pageIdx.coerceIn(0, pages.lastIndex)
              pageNavHandle.currentPageInChapter = safeIdx
              val page = pages[safeIdx]
              val isLastOfBook = snapChapterIdx == content.chapters.lastIndex && safeIdx == pages.lastIndex
              onPageChanged(page, isLastOfBook)
            }
        }

        LaunchedEffect(initialLocator, pages, snapChapterIdx) {
          if (initialLocator.chapterIndex == snapChapterIdx) {
            val desiredPage = ChapterPaginator.findPageForLocator(pages, initialLocator)
            if (desiredPage != pagerState.currentPage) {
              pagerState.scrollToPage(desiredPage)
            }
          }
        }

        pageNavHandle.onGoToPage = { page ->
          val target = page.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
          coroutineScope.launch {
            pagerState.scrollToPage(target)
          }
        }
        pageNavHandle.onStepNext = {
          val next = pagerState.currentPage + 1
          if (next < pages.size) {
            coroutineScope.launch {
              pagerState.animateScrollToPage(next, animationSpec = tapTurnTween)
            }
          } else if (snapChapterIdx + 1 < content.chapters.size) {
            onNextChapter()
          }
        }
        pageNavHandle.onStepPrev = {
          val prev = pagerState.currentPage - 1
          if (prev >= 0) {
            coroutineScope.launch {
              pagerState.animateScrollToPage(prev, animationSpec = tapTurnTween)
            }
          } else if (snapChapterIdx > 0) {
            onPrevChapterToEnd()
          }
        }

        val boundaryOverscroll = remember(pagerState, snapChapterIdx, pages.size, content.chapters.size) {
          var accumulatedX = 0f
          val thresholdPx = with(density) { 48.dp.toPx() }
          object : NestedScrollConnection {
            override fun onPostScroll(
              consumed: Offset,
              available: Offset,
              source: NestedScrollSource,
            ): Offset {
              if (source == NestedScrollSource.UserInput) {
                val atStart = pagerState.currentPage == 0 && !pagerState.canScrollBackward
                val atEnd = pagerState.currentPage >= pages.lastIndex && !pagerState.canScrollForward
                if (atStart && available.x > 0f && snapChapterIdx > 0) {
                  accumulatedX += available.x
                } else if (atEnd && available.x < 0f && snapChapterIdx + 1 < content.chapters.size) {
                  accumulatedX += available.x
                } else {
                  accumulatedX = 0f
                }
              }
              return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
              if (accumulatedX > thresholdPx && snapChapterIdx > 0) {
                accumulatedX = 0f
                onPrevChapterToEnd()
              } else if (accumulatedX < -thresholdPx && snapChapterIdx + 1 < content.chapters.size) {
                accumulatedX = 0f
                onNextChapter()
              } else {
                accumulatedX = 0f
              }
              return Velocity.Zero
            }
          }
        }

        val edgeGuardPx = with(density) { 28.dp.toPx() }

        HorizontalPager(
          state = pagerState,
          beyondViewportPageCount = 1,
          flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            snapAnimationSpec = snapSpring,
          ),
          modifier = Modifier
            .fillMaxSize()
            .nestedScroll(boundaryOverscroll)
            .testTag("reader_horizontal_pager"),
        ) { pageIdx ->
          val page = pages.getOrNull(pageIdx) ?: pages.first()
          val sliceRects = remember(snapKey, pageIdx) { mutableStateMapOf<Int, Rect>() }
          var containerRootOffset by remember { mutableStateOf(Offset.Zero) }

          Box(
            modifier = Modifier
              .fillMaxSize()
              .graphicsLayer {
                val pageOffset = (pagerState.currentPage - pageIdx) + pagerState.currentPageOffsetFraction
                val absOffset = abs(pageOffset).coerceIn(0f, 1f)
                val scale = 1f - absOffset * 0.018f
                scaleX = scale
                scaleY = scale
                alpha = 1f - absOffset * 0.14f
              }
              .onGloballyPositioned { coords ->
                containerRootOffset = coords.positionInRoot()
              }
              .pointerInput(pageIdx, pages.size, snapChapterIdx, edgeGuardPx) {
                detectTapGestures(
                  onTap = { tapOffset ->
                    val widthPx = size.width.toFloat()
                    val safeEdge = edgeGuardPx.coerceAtMost(widthPx / 4f)
                    val x = tapOffset.x
                    if (x < safeEdge || x > widthPx - safeEdge) {
                      return@detectTapGestures
                    }
                    when {
                      x < widthPx / 3f -> pageNavHandle.previousPage()
                      x > (widthPx * 2f) / 3f -> pageNavHandle.nextPage()
                      else -> onToggleBars()
                    }
                  },
                  onLongPress = { pressOffset ->
                    val rootPoint = containerRootOffset + pressOffset
                    val hitEntry = sliceRects.entries.firstOrNull { (_, rect) ->
                      rootPoint.y in (rect.top - 12f)..(rect.bottom + 12f)
                    } ?: sliceRects.entries.minByOrNull { (_, rect) ->
                      when {
                        rootPoint.y < rect.top -> rect.top - rootPoint.y
                        rootPoint.y > rect.bottom -> rootPoint.y - rect.bottom
                        else -> 0f
                      }
                    }
                    val slice = hitEntry?.let { page.slices.getOrNull(it.key) }
                    if (slice != null) {
                      val originalPara = snapChapter.paragraphs.getOrNull(slice.paragraphIndex)
                      if (originalPara != null && originalPara.text.length > 8) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPressParagraph(snapChapter.title, originalPara.index)
                      }
                    }
                  },
                )
              },
            contentAlignment = Alignment.TopCenter,
          ) {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .padding(
                  start = horizMarginDp,
                  end = horizMarginDp,
                  top = topPaddingDp,
                  bottom = bottomPaddingDp,
                ),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              page.slices.forEachIndexed { idx, slice ->
                val isQuoted = quotedParagraphIndices.contains(slice.paragraphIndex)
                val isTtsActive = ttsActiveParagraphIndex == slice.paragraphIndex
                PageSliceView(
                  slice = slice,
                  preferences = preferences,
                  isQuoted = isQuoted,
                  isTtsActive = isTtsActive,
                  modifier = Modifier.onGloballyPositioned { coords ->
                    sliceRects[idx] = coords.boundsInRoot()
                  },
                )
              }
            }

            Text(
              text = "${pageIdx + 1} / ${pages.size}",
              style = MaterialTheme.typography.labelSmall,
              color = theme.mutedText.copy(alpha = 0.7f),
              modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navBottomDp + 12.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PageSliceView(
  slice: PageSlice,
  preferences: ReaderPreferences,
  isQuoted: Boolean,
  isTtsActive: Boolean,
  modifier: Modifier = Modifier,
) {
  val theme = preferences.readerTheme
  val spec = remember(slice.kind, slice.text, preferences) {
    ReaderParagraphStyleResolver.resolve(slice.kind, slice.text, preferences)
  }

  val topPad = if (slice.isFirstSliceOfParagraph) spec.extraTopPaddingDp else 0.dp
  val bottomPad = if (slice.isLastSliceOfParagraph) spec.extraBottomPaddingDp else 0.dp
  val highlightBg = when {
    isTtsActive -> theme.accent.copy(alpha = 0.18f)
    isQuoted -> theme.accent.copy(alpha = 0.11f)
    else -> Color.Transparent
  }

  val baseModifier = modifier
    .fillMaxWidth()
    .clip(RoundedCornerShape(8.dp))
    .background(highlightBg)

  when (slice.kind) {
    ParagraphKind.HEADING -> {
      Text(
        text = slice.text,
        style = spec.textStyle,
        color = theme.text,
        modifier = baseModifier.padding(top = topPad, bottom = bottomPad),
      )
    }

    ParagraphKind.SUBHEADING -> {
      Text(
        text = slice.text,
        style = spec.textStyle,
        color = theme.mutedText,
        modifier = baseModifier.padding(top = topPad, bottom = bottomPad),
      )
    }

    ParagraphKind.QUOTE -> {
      Row(
        modifier = baseModifier
          .height(IntrinsicSize.Min)
          .padding(top = topPad, bottom = bottomPad),
      ) {
        Box(
          modifier = Modifier
            .width(3.dp)
            .fillMaxHeight()
            .background(theme.accent.copy(alpha = 0.7f)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
          text = slice.text,
          style = spec.textStyle,
          color = theme.mutedText,
          modifier = Modifier.weight(1f),
        )
      }
    }

    ParagraphKind.POEM -> {
      Text(
        text = slice.text,
        style = spec.textStyle,
        color = theme.text,
        modifier = baseModifier.padding(start = spec.extraStartPaddingDp, top = topPad, bottom = bottomPad),
      )
    }

    ParagraphKind.CODE -> {
      val topInner = if (slice.isFirstSliceOfParagraph) 10.dp else 0.dp
      val bottomInner = if (slice.isLastSliceOfParagraph) 10.dp else 0.dp
      val outerTop = if (slice.isFirstSliceOfParagraph) 4.dp else 0.dp
      val outerBottom = if (slice.isLastSliceOfParagraph) 4.dp else 0.dp
      Surface(
        color = theme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = baseModifier.padding(top = outerTop, bottom = outerBottom),
      ) {
        Text(
          text = slice.text,
          style = spec.textStyle,
          color = theme.text,
          modifier = Modifier.padding(
            start = 10.dp,
            end = 10.dp,
            top = topInner,
            bottom = bottomInner,
          ),
        )
      }
    }

    ParagraphKind.BODY -> {
      Text(
        text = slice.text,
        style = spec.textStyle,
        textAlign = spec.textAlign,
        color = theme.text,
        modifier = baseModifier.padding(top = topPad, bottom = bottomPad),
      )
    }
  }
}
