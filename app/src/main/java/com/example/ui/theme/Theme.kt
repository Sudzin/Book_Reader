package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val EditorialDarkColorScheme = darkColorScheme(
  primary = CoralPrimaryDark,
  onPrimary = Color(0xFF4A150A),
  primaryContainer = TerracottaContainerDark,
  onPrimaryContainer = OnTerracottaContainerDark,
  secondary = SageSecondaryDark,
  onSecondary = Color(0xFF1A331D),
  secondaryContainer = SageContainerDark,
  onSecondaryContainer = OnSageContainerDark,
  tertiary = AmberTertiaryDark,
  background = GraphiteDark,
  onBackground = ParchmentTextDark,
  surface = GraphiteDark,
  onSurface = ParchmentTextDark,
  surfaceVariant = CharcoalSurfaceDark,
  onSurfaceVariant = ParchmentMutedDark,
  surfaceContainerLow = CharcoalSurfaceDark,
  surfaceContainer = CharcoalSurfaceDark,
  surfaceContainerHigh = CharcoalElevatedDark,
  outline = OutlineWarmDark,
  outlineVariant = OutlineWarmDark,
)

private val EditorialLightColorScheme = lightColorScheme(
  primary = TerracottaPrimary,
  onPrimary = Color.White,
  primaryContainer = TerracottaContainerLight,
  onPrimaryContainer = OnTerracottaContainerLight,
  secondary = SageSecondary,
  onSecondary = Color.White,
  secondaryContainer = SageContainerLight,
  onSecondaryContainer = OnSageContainerLight,
  tertiary = AmberTertiary,
  tertiaryContainer = AmberContainerLight,
  background = AlabasterLight,
  onBackground = InkDark,
  surface = AlabasterLight,
  onSurface = InkDark,
  surfaceVariant = CreamSurfaceLight,
  onSurfaceVariant = InkMutedLight,
  surfaceContainerLow = CreamSurfaceLight,
  surfaceContainer = CreamSurfaceLight,
  surfaceContainerHigh = WarmSandLight,
  outline = OutlineWarmLight,
  outlineVariant = OutlineWarmLight,
)

val AppShapes = Shapes(
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(24.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ListayTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> EditorialDarkColorScheme
    else -> EditorialLightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = AppTypography,
    shapes = AppShapes,
    content = content,
  )
}
