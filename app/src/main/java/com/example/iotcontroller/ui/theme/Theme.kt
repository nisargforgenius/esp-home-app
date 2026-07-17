package com.example.iotcontroller.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun IoTControllerTheme(
    appTheme: AppTheme = AppTheme.SLATE_BLUE,
    content: @Composable () -> Unit
) {
    // Deliberately no dynamic-color / wallpaper-derived path here: the whole
    // point of the theme picker is that the user's chosen palette always
    // wins, on every Android version, rather than being silently overridden
    // by system dynamic color on Android 12+.
    val colorScheme = appTheme.colorScheme
    val isLight = colorScheme.background.luminance() > 0.5f

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLight
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
