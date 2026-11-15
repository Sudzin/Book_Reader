package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
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

@Immutable
data class AnimatedReaderColors(
  val background: Color,
  val surface: Color,
  val surfaceContainerLow: Color,
  val surfaceContainerHigh: Color,
  val text: Color,
  val mutedText: Color,
  val accent: Color,
  val onAccent: Color,
  val accentContainer: Color,
  val onAccentContainer: Color,
  val outline: Color,
  val isDark: Boolean,
)

@Composable
fun rememberAnimatedReaderColors(
  theme: ReaderThemeMode,
  animationsEnabled: Boolean = LocalAnimationsEnabled.current,
): AnimatedReaderColors {
  val spec = tween<Color>(durationMillis = if (animationsEnabled) 250 else 0)
  val background by animateColorAsState(theme.background, spec, label = "reader_bg")
  val surface by animateColorAsState(theme.surface, spec, label = "reader_surface")
  val surfaceContainerLow by animateColorAsState(theme.surfaceContainerLow, spec, label = "reader_surface_low")
  val surfaceContainerHigh by animateColorAsState(theme.surfaceContainerHigh, spec, label = "reader_surface_high")
  val text by animateColorAsState(theme.text, spec, label = "reader_text")
  val mutedText by animateColorAsState(theme.mutedText, spec, label = "reader_muted")
  val accent by animateColorAsState(theme.accent, spec, label = "reader_accent")
  val onAccent by animateColorAsState(theme.onAccent, spec, label = "reader_on_accent")
  val accentContainer by animateColorAsState(theme.accentContainer, spec, label = "reader_accent_container")
  val onAccentContainer by animateColorAsState(theme.onAccentContainer, spec, label = "reader_on_accent_container")
  val outline by animateColorAsState(theme.outline, spec, label = "reader_outline")
  return AnimatedReaderColors(
    background = background,
    surface = surface,
    surfaceContainerLow = surfaceContainerLow,
    surfaceContainerHigh = surfaceContainerHigh,
    text = text,
    mutedText = mutedText,
    accent = accent,
    onAccent = onAccent,
    accentContainer = accentContainer,
    onAccentContainer = onAccentContainer,
    outline = outline,
    isDark = theme.isDark,
  )
}

@Composable
private fun animateColorScheme(
  target: ColorScheme,
  animationsEnabled: Boolean,
): ColorScheme {
  val spec = tween<Color>(durationMillis = if (animationsEnabled) 250 else 0)
  val primary by animateColorAsState(target.primary, spec, label = "cs_primary")
  val onPrimary by animateColorAsState(target.onPrimary, spec, label = "cs_onPrimary")
  val primaryContainer by animateColorAsState(target.primaryContainer, spec, label = "cs_primaryContainer")
  val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, spec, label = "cs_onPrimaryContainer")
  val secondary by animateColorAsState(target.secondary, spec, label = "cs_secondary")
  val onSecondary by animateColorAsState(target.onSecondary, spec, label = "cs_onSecondary")
  val secondaryContainer by animateColorAsState(target.secondaryContainer, spec, label = "cs_secondaryContainer")
  val onSecondaryContainer by animateColorAsState(target.onSecondaryContainer, spec, label = "cs_onSecondaryContainer")
  val tertiary by animateColorAsState(target.tertiary, spec, label = "cs_tertiary")
  val onTertiary by animateColorAsState(target.onTertiary, spec, label = "cs_onTertiary")
  val tertiaryContainer by animateColorAsState(target.tertiaryContainer, spec, label = "cs_tertiaryContainer")
  val onTertiaryContainer by animateColorAsState(target.onTertiaryContainer, spec, label = "cs_onTertiaryContainer")
  val background by animateColorAsState(target.background, spec, label = "cs_background")
  val onBackground by animateColorAsState(target.onBackground, spec, label = "cs_onBackground")
  val surface by animateColorAsState(target.surface, spec, label = "cs_surface")
  val onSurface by animateColorAsState(target.onSurface, spec, label = "cs_onSurface")
  val surfaceVariant by animateColorAsState(target.surfaceVariant, spec, label = "cs_surfaceVariant")
  val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, spec, label = "cs_onSurfaceVariant")
  val surfaceContainerLowest by animateColorAsState(target.surfaceContainerLowest, spec, label = "cs_surfaceLowest")
  val surfaceContainerLow by animateColorAsState(target.surfaceContainerLow, spec, label = "cs_surfaceLow")
  val surfaceContainer by animateColorAsState(target.surfaceContainer, spec, label = "cs_surfaceContainer")
  val surfaceContainerHigh by animateColorAsState(target.surfaceContainerHigh, spec, label = "cs_surfaceHigh")
  val surfaceContainerHighest by animateColorAsState(target.surfaceContainerHighest, spec, label = "cs_surfaceHighest")
  val surfaceDim by animateColorAsState(target.surfaceDim, spec, label = "cs_surfaceDim")
  val surfaceBright by animateColorAsState(target.surfaceBright, spec, label = "cs_surfaceBright")
  val outline by animateColorAsState(target.outline, spec, label = "cs_outline")
  val outlineVariant by animateColorAsState(target.outlineVariant, spec, label = "cs_outlineVariant")
  val error by animateColorAsState(target.error, spec, label = "cs_error")
  val onError by animateColorAsState(target.onError, spec, label = "cs_onError")

  return target.copy(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    inversePrimary = primary,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    inverseSurface = onBackground,
    inverseOnSurface = background,
    surfaceContainerLowest = surfaceContainerLowest,
    surfaceContainerLow = surfaceContainerLow,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest,
    surfaceDim = surfaceDim,
    surfaceBright = surfaceBright,
    outline = outline,
    outlineVariant = outlineVariant,
    error = error,
    onError = onError,
  )
}

private fun ReaderThemeMode.toColorScheme(): ColorScheme {
  return if (isDark) {
    darkColorScheme(
      primary = accent,
      onPrimary = onAccent,
      primaryContainer = accentContainer,
      onPrimaryContainer = onAccentContainer,
      inversePrimary = accent,
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
      surfaceTint = Color.Transparent,
      inverseSurface = text,
      inverseOnSurface = background,
      surfaceContainerLowest = background,
      surfaceContainerLow = surfaceContainerLow,
      surfaceContainer = surface,
      surfaceContainerHigh = surfaceContainerHigh,
      surfaceContainerHighest = surfaceContainerHigh,
      surfaceDim = background,
      surfaceBright = surfaceContainerHigh,
      outline = outline,
      outlineVariant = outline,
      scrim = Color.Black,
      error = Color(0xFFD47B6A),
      onError = Color(0xFF2E100B),
    )
  } else {
    lightColorScheme(
      primary = accent,
      onPrimary = onAccent,
      primaryContainer = accentContainer,
      onPrimaryContainer = onAccentContainer,
      inversePrimary = accent,
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
      surfaceTint = Color.Transparent,
      inverseSurface = text,
      inverseOnSurface = background,
      surfaceContainerLowest = background,
      surfaceContainerLow = surfaceContainerLow,
      surfaceContainer = surface,
      surfaceContainerHigh = surfaceContainerHigh,
      surfaceContainerHighest = surfaceContainerHigh,
      surfaceDim = surfaceContainerHigh,
      surfaceBright = background,
      outline = outline,
      outlineVariant = outline,
      scrim = Color.Black,
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
  animationsEnabled: Boolean = rememberSystemAnimationsEnabled(),
  content: @Composable () -> Unit,
) {
  val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  val targetColorScheme = if (useDynamic) {
    val context = LocalContext.current
    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
  } else {
    readerTheme.toColorScheme()
  }
  val colorScheme = animateColorScheme(
    target = targetColorScheme,
    animationsEnabled = animationsEnabled,
  )

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

  CompositionLocalProvider(LocalAnimationsEnabled provides animationsEnabled) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = AppTypography,
      shapes = AppShapes,
      content = content,
    )
  }
}
