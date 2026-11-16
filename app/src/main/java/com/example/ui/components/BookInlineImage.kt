package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
  exactHeightDp: Dp? = null,
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

  Box(
    modifier = modifier
      .fillMaxWidth()
      .testTag("reader_inline_image_$paragraphIndex"),
    contentAlignment = Alignment.Center,
  ) {
    if (loadedBitmap != null) {
      val needsLightBackdrop = theme.isDark && loadedBitmap.hasAlpha()
      val sizeModifier = if (exactHeightDp != null) {
        Modifier
          .fillMaxWidth()
          .height(exactHeightDp)
      } else {
        Modifier
          .fillMaxWidth()
          .heightIn(max = maxHeightDp)
      }
      Box(
        modifier = sizeModifier
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
          modifier = Modifier
            .then(
              if (exactHeightDp != null) {
                Modifier.height(if (needsLightBackdrop) (exactHeightDp - 12.dp).coerceAtLeast(16.dp) else exactHeightDp)
              } else {
                Modifier.heightIn(max = maxHeightDp)
              }
            )
            .fillMaxWidth()
            .clip(shape),
        )
      }
    } else if (!state.done) {
      val aspect = if (imageWidth > 0 && imageHeight > 0) {
        (imageHeight.toFloat() / imageWidth.toFloat()).coerceIn(0.4f, 1.4f)
      } else {
        0.6f
      }
      val placeholderHeight = exactHeightDp ?: (220f * aspect).dp.coerceIn(96.dp, maxHeightDp)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(placeholderHeight)
          .clip(shape)
          .background(theme.surfaceContainerLow),
      )
    } else {
      val fallbackHeight = exactHeightDp?.coerceAtMost(140.dp) ?: 96.dp
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
