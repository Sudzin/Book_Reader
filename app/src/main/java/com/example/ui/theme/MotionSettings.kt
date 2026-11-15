package com.example.ui.theme

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

val LocalAnimationsEnabled = staticCompositionLocalOf { true }

fun Context.areSystemAnimationsEnabled(): Boolean {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    val animatorsEnabled = runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true)
    if (!animatorsEnabled) return false
  }
  val animatorScale = runCatching {
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
  }.getOrDefault(1f)
  return animatorScale > 0f
}

@Composable
fun rememberSystemAnimationsEnabled(): Boolean {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var enabled by remember(context) { mutableStateOf(context.areSystemAnimationsEnabled()) }

  DisposableEffect(lifecycleOwner, context) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        enabled = context.areSystemAnimationsEnabled()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  return enabled
}
