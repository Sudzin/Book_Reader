package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.data.local.ReaderThemeMode

val AppShapes = Shapes(
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(24.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

private fun ReaderThemeMode.toColorScheme(): ColorScheme {
  return if (isDark) {
    darkColorScheme(
      primary = accent,
      onPrimary = onAccent,
      primaryContainer = accentContainer,
      onPrimaryContainer = onAccentContainer,
      secondary = accent,
      onSecondary = onAccent,
      secondaryContainer = surfaceContainerHigh,
      onSecondaryContainer = text,
      tertiary = accent,
      onTertiary = onAccent,
      tertiaryContainer = surfaceContainerHigh,
      onTertiaryContainer = text,
      background = background,
      onBackground = text,
      surface = surface,
      onSurface = text,
      surfaceVariant = surfaceContainerHigh,
      onSurfaceVariant = mutedText,
      surfaceContainerLowest = background,
      surfaceContainerLow = surfaceContainerLow,
      surfaceContainer = surface,
      surfaceContainerHigh = surfaceContainerHigh,
      surfaceContainerHighest = surfaceContainerHigh,
      outline = outline,
      outlineVariant = outline,
      error = Color(0xFFD47B6A),
      onError = Color(0xFF2E100B),
    )
  } else {
    lightColorScheme(
      primary = accent,
      onPrimary = onAccent,
      primaryContainer = accentContainer,
      onPrimaryContainer = onAccentContainer,
      secondary = accent,
      onSecondary = onAccent,
      secondaryContainer = surfaceContainerHigh,
      onSecondaryContainer = text,
      tertiary = accent,
      onTertiary = onAccent,
      tertiaryContainer = surfaceContainerHigh,
      onTertiaryContainer = text,
      background = background,
      onBackground = text,
      surface = surface,
      onSurface = text,
      surfaceVariant = surfaceContainerHigh,
      onSurfaceVariant = mutedText,
      surfaceContainerLowest = background,
      surfaceContainerLow = surfaceContainerLow,
      surfaceContainer = surface,
      surfaceContainerHigh = surfaceContainerHigh,
      surfaceContainerHighest = surfaceContainerHigh,
      outline = outline,
      outlineVariant = outline,
      error = Color(0xFF9F3A2B),
      onError = Color(0xFFFDF8F2),
    )
  }
}

@Composable
fun ListayTheme(
  readerTheme: ReaderThemeMode = ReaderThemeMode.SEPIA,
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  val colorScheme = if (useDynamic) {
    val context = LocalContext.current
    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
  } else {
    readerTheme.toColorScheme()
  }

  val isDarkStatus = if (useDynamic) darkTheme else readerTheme.isDark
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window
      if (window != null) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !isDarkStatus
        controller.isAppearanceLightNavigationBars = !isDarkStatus
      }
    }
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = AppTypography,
    shapes = AppShapes,
    content = content,
  )
}
