package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.BookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun BookCoverArt(
  book: BookEntity,
  modifier: Modifier = Modifier,
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
  Box(
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
      val baseColor = Color(book.accentColorHex)
      val darkShade = Color(
        red = (baseColor.red * 0.72f).coerceIn(0f, 1f),
        green = (baseColor.green * 0.72f).coerceIn(0f, 1f),
        blue = (baseColor.blue * 0.72f).coerceIn(0f, 1f),
      )
      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(Brush.verticalGradient(listOf(baseColor, darkShade)))
          .padding(start = 8.dp, end = 8.dp, top = 26.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.Bottom,
      ) {
        Text(
          text = book.title,
          style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            lineHeight = 14.sp,
          ),
          color = Color(0xFFFDF8F0),
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = book.author,
          style = MaterialTheme.typography.labelSmall,
          color = Color(0xFFFDF8F0).copy(alpha = 0.8f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

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
