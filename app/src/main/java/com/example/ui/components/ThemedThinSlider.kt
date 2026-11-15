package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.local.ReaderThemeMode
import com.example.ui.theme.AnimatedReaderColors
import com.example.ui.theme.LocalAnimationsEnabled
import kotlin.math.roundToInt

fun ReaderThemeMode.inactiveTrackColor(containerBackground: Color = surface): Color {
  return if (isDark) {
    if (containerBackground == Color.Black) {
      outline.copy(alpha = 0.5f)
    } else {
      lerp(containerBackground, Color.Black, 0.50f)
    }
  } else {
    lerp(containerBackground, Color.Black, 0.14f)
  }
}

fun AnimatedReaderColors.inactiveTrackColor(containerBackground: Color = surface): Color {
  return if (isDark) {
    if (containerBackground == Color.Black) {
      outline.copy(alpha = 0.5f)
    } else {
      lerp(containerBackground, Color.Black, 0.50f)
    }
  } else {
    lerp(containerBackground, Color.Black, 0.14f)
  }
}

@Composable
fun ThemedThinSlider(
  value: Float,
  onValueChange: (Float) -> Unit,
  valueRange: ClosedFloatingPointRange<Float>,
  activeColor: Color,
  inactiveColor: Color,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  steps: Int = 0,
  trackHeight: Dp = 2.5.dp,
  thumbDiameter: Dp = 13.dp,
  onValueChangeFinished: (() -> Unit)? = null,
) {
  val minVal = valueRange.start
  val maxVal = valueRange.endInclusive
  val span = (maxVal - minVal)
  val clampedValue = if (span > 0f) value.coerceIn(minVal, maxVal) else minVal
  val fraction = if (span > 0f) ((clampedValue - minVal) / span).coerceIn(0f, 1f) else 1f

  var isDragging by remember { mutableStateOf(false) }
  val animationsEnabled = LocalAnimationsEnabled.current
  val animatedThumbDiameter by animateDpAsState(
    targetValue = if (isDragging) thumbDiameter + 2.dp else thumbDiameter,
    animationSpec = if (animationsEnabled) spring() else snap(),
    label = "slider_thumb_size",
  )

  val latestOnValueChange by rememberUpdatedState(onValueChange)
  val latestOnFinished by rememberUpdatedState(onValueChangeFinished)

  fun snapAndEmit(rawFraction: Float) {
    val bounded = rawFraction.coerceIn(0f, 1f)
    val snappedFraction = if (steps > 0) {
      val segments = steps + 1
      (bounded * segments).roundToInt().toFloat() / segments.toFloat()
    } else {
      bounded
    }
    val mapped = minVal + snappedFraction * span
    latestOnValueChange(mapped)
  }

  val interactionModifier = if (enabled && span > 0f) {
    Modifier
      .pointerInput(minVal, maxVal, steps) {
        detectTapGestures { offset ->
          val thumbRadiusPx = thumbDiameter.toPx() / 2f
          val usableWidth = (size.width - thumbRadiusPx * 2f).coerceAtLeast(1f)
          val raw = (offset.x - thumbRadiusPx) / usableWidth
          snapAndEmit(raw)
          latestOnFinished?.invoke()
        }
      }
      .pointerInput(minVal, maxVal, steps) {
        detectHorizontalDragGestures(
          onDragStart = { offset ->
            isDragging = true
            val thumbRadiusPx = thumbDiameter.toPx() / 2f
            val usableWidth = (size.width - thumbRadiusPx * 2f).coerceAtLeast(1f)
            snapAndEmit((offset.x - thumbRadiusPx) / usableWidth)
          },
          onHorizontalDrag = { change, _ ->
            change.consume()
            val thumbRadiusPx = thumbDiameter.toPx() / 2f
            val usableWidth = (size.width - thumbRadiusPx * 2f).coerceAtLeast(1f)
            snapAndEmit((change.position.x - thumbRadiusPx) / usableWidth)
          },
          onDragEnd = {
            isDragging = false
            latestOnFinished?.invoke()
          },
          onDragCancel = {
            isDragging = false
            latestOnFinished?.invoke()
          },
        )
      }
  } else {
    Modifier
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = 48.dp)
      .height(48.dp)
      .semantics {
        if (!enabled) {
          disabled()
        } else {
          setProgress { target ->
            if (span <= 0f) return@setProgress false
            latestOnValueChange(target.coerceIn(minVal, maxVal))
            latestOnFinished?.invoke()
            true
          }
        }
      }
      .then(interactionModifier),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.fillMaxWidth().height(thumbDiameter + 6.dp)) {
      val strokePx = trackHeight.toPx()
      val baseThumbRadiusPx = thumbDiameter.toPx() / 2f
      val currentThumbRadiusPx = animatedThumbDiameter.toPx() / 2f
      val centerY = size.height / 2f
      val startX = baseThumbRadiusPx
      val endX = (size.width - baseThumbRadiusPx).coerceAtLeast(startX + 1f)
      val thumbX = startX + (endX - startX) * fraction

      // Continuous inactive track (no gaps or cuts)
      drawLine(
        color = inactiveColor,
        start = Offset(startX, centerY),
        end = Offset(endX, centerY),
        strokeWidth = strokePx,
        cap = StrokeCap.Round,
      )

      // Active track overlapping cleanly up to thumb center (no gap/cut)
      val effectiveActiveColor = if (enabled) activeColor else activeColor.copy(alpha = 0.55f)
      if (thumbX > startX) {
        drawLine(
          color = effectiveActiveColor,
          start = Offset(startX, centerY),
          end = Offset(thumbX, centerY),
          strokeWidth = strokePx,
          cap = StrokeCap.Round,
        )
      }

      // Solid circular thumb without gaps/cuts
      if (enabled) {
        drawCircle(
          color = activeColor,
          radius = currentThumbRadiusPx,
          center = Offset(thumbX, centerY),
        )
      }
    }
  }
}
