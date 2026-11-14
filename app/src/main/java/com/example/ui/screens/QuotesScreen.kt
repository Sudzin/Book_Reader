package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.QuoteEntity

@Composable
fun QuotesScreen(
  quotes: List<QuoteEntity>,
  onOpenQuoteInBook: (bookId: Long, unitIndex: Int, paragraphIndex: Int) -> Unit,
  onDeleteQuote: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 760.dp),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 14.dp),
      ) {
        Text(
          text = "Цитаты",
          style = MaterialTheme.typography.displaySmall,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
          text = if (quotes.isEmpty()) "Избранные мысли из книг" else "Сохранено цитат: ${quotes.size}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (quotes.isEmpty()) {
        Column(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.size(72.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Default.FormatQuote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
              )
            }
          }
          Spacer(modifier = Modifier.height(18.dp))
          Text(
            text = stringResource(R.string.empty_quotes_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = stringResource(R.string.empty_quotes_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(quotes, key = { it.id }) { quote ->
            val tagColor = when (quote.colorTag) {
              "SAGE" -> Color(0xFF4F6B52)
              "AMBER" -> Color(0xFFB37A1E)
              else -> Color(0xFF9C422E)
            }
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .testTag("quote_card_${quote.id}")
                .clickable {
                  onOpenQuoteInBook(quote.bookId, quote.unitIndex, quote.paragraphIndex)
                },
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
              ),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                  Box(
                    modifier = Modifier
                      .width(4.dp)
                      .height(48.dp)
                      .clip(RoundedCornerShape(2.dp))
                      .background(tagColor),
                  )
                  Spacer(modifier = Modifier.width(12.dp))
                  Text(
                    text = "«${quote.text}»",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                  )
                }

                if (quote.note.isNotBlank()) {
                  Spacer(modifier = Modifier.height(10.dp))
                  Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Text(
                      text = quote.note,
                      style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.padding(10.dp),
                    )
                  }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = quote.bookTitle,
                      style = MaterialTheme.typography.labelLarge,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = "${quote.bookAuthor} • ${quote.unitTitle}",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                  Row {
                    IconButton(
                      onClick = {
                        val shareText = buildString {
                          append("«${quote.text}»\n\n— ${quote.bookAuthor}, «${quote.bookTitle}»")
                          if (quote.note.isNotBlank()) {
                            append("\n\nЗаметка: ${quote.note}")
                          }
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                          type = "text/plain"
                          putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "Поделиться цитатой"))
                      },
                    ) {
                      Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringResource(R.string.cd_share_quote),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    IconButton(onClick = { onDeleteQuote(quote.id) }) {
                      Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Удалить цитату",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
