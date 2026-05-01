package com.linktolinux.wifidirect.presentation.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

@Composable
fun LinkToLinuxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    customColor: Color? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        customColor != null -> {
            if (darkTheme) {
                darkColorScheme(
                    primary = customColor,
                    surfaceVariant = customColor.copy(alpha = 0.2f),
                    onSurfaceVariant = Color(0xFFCAC4D0),
                    onPrimary = Color.White
                )
            } else {
                lightColorScheme(
                    primary = customColor,
                    surfaceVariant = customColor.copy(alpha = 0.1f),
                    onSurfaceVariant = Color(0xFF49454F),
                    onPrimary = Color.White
                )
            }
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
