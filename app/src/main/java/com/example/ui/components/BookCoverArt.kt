package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.BookEntity
import com.example.ui.theme.LiterataFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal fun bookCoverInitial(title: String): String {
  val ch = title.trim().firstOrNull { it.isLetterOrDigit() }
    ?: title.trim().firstOrNull()
    ?: 'К'
  return ch.uppercaseChar().toString()
}

@Composable
fun BookCoverArt(
  book: BookEntity,
  modifier: Modifier = Modifier,
  showFormatBadge: Boolean = true,
) {
  val bitmap by produceState<Bitmap?>(initialValue = null, key1 = book.coverPath) {
    value = withContext(Dispatchers.IO) {
      val path = book.coverPath ?: return@withContext null
      val file = File(path)
      if (!file.exists()) return@withContext null
      runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    }
  }

  val shape = RoundedCornerShape(12.dp)
  BoxWithConstraints(
    modifier = modifier
      .clip(shape)
      .border(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        shape = shape,
      ),
  ) {
    val loadedBitmap = bitmap
    if (loadedBitmap != null) {
      Image(
        bitmap = loadedBitmap.asImageBitmap(),
        contentDescription = book.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    } else {
      val baseThemeColor = MaterialTheme.colorScheme.primary
      val shadeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.86f)
      val letterColor = MaterialTheme.colorScheme.onPrimary
      val letterSize = (maxWidth.value * 0.45f).coerceIn(20f, 44f).sp
      val initial = bookCoverInitial(book.title)

      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Brush.verticalGradient(listOf(baseThemeColor, shadeColor)))
          .testTag("book_cover_monogram_${book.id}"),
        contentAlignment = Alignment.Center,
      ) {
        Box(
          modifier = Modifier
            .align(Alignment.CenterStart)
            .width(4.dp)
            .fillMaxHeight()
            .background(letterColor.copy(alpha = 0.16f)),
        )
        Text(
          text = initial,
          style = MaterialTheme.typography.headlineMedium.copy(
            fontFamily = LiterataFontFamily,
            fontSize = letterSize,
            lineHeight = letterSize,
            fontWeight = FontWeight.Bold,
          ),
          color = letterColor,
          maxLines = 1,
          softWrap = false,
          modifier = Modifier.testTag("book_cover_initial_${book.id}"),
        )
      }
    }

    if (showFormatBadge) {
      Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(5.dp),
      ) {
        Text(
          text = book.format,
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          softWrap = false,
          modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
        )
      }
    }
  }
}
