package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.reader.engine.BookImageExtractor
import com.example.ui.theme.AnimatedReaderColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class DecodedImageState(
  val done: Boolean,
  val bitmap: Bitmap?,
)

@Composable
fun BookInlineImage(
  imagePath: String?,
  imageWidth: Int,
  imageHeight: Int,
  imageAlt: String?,
  paragraphIndex: Int,
  theme: AnimatedReaderColors,
  modifier: Modifier = Modifier,
  maxHeightDp: Dp = 440.dp,
) {
  val state by produceState(
    initialValue = DecodedImageState(done = false, bitmap = null),
    key1 = imagePath,
  ) {
    value = DecodedImageState(done = false, bitmap = null)
    val decoded = withContext(Dispatchers.IO) {
      if (imagePath.isNullOrBlank()) null
      else BookImageExtractor.decodeSampledBitmap(imagePath, maxWidthPx = 1440, maxHeightPx = 1800)
    }
    value = DecodedImageState(done = true, bitmap = decoded)
  }

  val shape = RoundedCornerShape(10.dp)
  val loadedBitmap = state.bitmap

  BoxWithConstraints(
    modifier = modifier
      .fillMaxWidth()
      .testTag("reader_inline_image_$paragraphIndex"),
    contentAlignment = Alignment.Center,
  ) {
    val availableWidthDp = maxWidth.coerceAtLeast(64.dp)
    val effectiveMaxHeightDp = maxHeightDp.coerceAtLeast(48.dp)

    if (loadedBitmap != null) {
      val bw = loadedBitmap.width.coerceAtLeast(1)
      val bh = loadedBitmap.height.coerceAtLeast(1)
      val aspect = bw.toFloat() / bh.toFloat()

      val targetWidthDp = (bw * 2.2f).dp.coerceAtMost(availableWidthDp)
      val desiredHeightDp = targetWidthDp / aspect
      val (finalWidthDp, finalHeightDp) = if (desiredHeightDp > effectiveMaxHeightDp) {
        (effectiveMaxHeightDp * aspect).coerceAtMost(availableWidthDp) to effectiveMaxHeightDp
      } else {
        targetWidthDp to desiredHeightDp
      }

      val needsLightBackdrop = theme.isDark && loadedBitmap.hasAlpha()
      Box(
        modifier = Modifier
          .size(width = finalWidthDp.coerceAtLeast(24.dp), height = finalHeightDp.coerceAtLeast(24.dp))
          .clip(shape)
          .then(
            if (needsLightBackdrop) {
              Modifier
                .background(Color(0xFFF3EFE6))
                .padding(6.dp)
            } else {
              Modifier
            }
          ),
        contentAlignment = Alignment.Center,
      ) {
        Image(
          bitmap = loadedBitmap.asImageBitmap(),
          contentDescription = imageAlt?.takeIf { it.isNotBlank() } ?: "Иллюстрация из книги",
          contentScale = ContentScale.Fit,
          filterQuality = FilterQuality.High,
          modifier = Modifier
            .fillMaxSize()
            .clip(shape),
        )
      }
    } else if (!state.done) {
      val bw = imageWidth.coerceAtLeast(1)
      val bh = imageHeight.coerceAtLeast(1)
      val aspect = if (imageWidth > 0 && imageHeight > 0) bw.toFloat() / bh.toFloat() else 1.5f
      val targetWidthDp = if (imageWidth > 0) {
        (bw * 2.2f).dp.coerceAtMost(availableWidthDp)
      } else {
        availableWidthDp
      }
      val placeholderHeight = (targetWidthDp / aspect).coerceIn(48.dp, effectiveMaxHeightDp)
      Box(
        modifier = Modifier
          .size(width = targetWidthDp, height = placeholderHeight)
          .clip(shape)
          .background(theme.surfaceContainerLow),
      )
    } else {
      val fallbackHeight = effectiveMaxHeightDp.coerceAtMost(96.dp)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(fallbackHeight)
          .clip(shape)
          .background(theme.surfaceContainerLow)
          .border(1.dp, theme.outline.copy(alpha = 0.28f), shape),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.BrokenImage,
            contentDescription = null,
            tint = theme.mutedText,
            modifier = Modifier.size(22.dp),
          )
          Text(
            text = imageAlt?.takeIf { it.isNotBlank() } ?: "Изображение недоступно",
            style = MaterialTheme.typography.labelSmall,
            color = theme.mutedText,
          )
        }
      }
    }
  }
}
