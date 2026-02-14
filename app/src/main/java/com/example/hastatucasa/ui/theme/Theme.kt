package com.example.hastatucasa.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme

private val GreenPrimary       = Color(0xFF2E7D32)
private val GreenOnPrimary     = Color(0xFFFFFFFF)
private val GreenContainer     = Color(0xFFC8E6C9)
private val GreenOnContainer   = Color(0xFF003909)
private val OrangeSecondary    = Color(0xFFE65100)
private val OrangeContainer    = Color(0xFFFFE0B2)

private val LightColorScheme = lightColorScheme(
    primary            = GreenPrimary,
    onPrimary          = GreenOnPrimary,
    primaryContainer   = GreenContainer,
    onPrimaryContainer = GreenOnContainer,
    secondary          = OrangeSecondary,
    secondaryContainer = OrangeContainer,
    background         = Color(0xFFF9FBF9),
    surface            = Color(0xFFFFFFFF),
    surfaceVariant     = Color(0xFFF1F4F1),
    outline            = Color(0xFF9E9E9E),
)

private val DarkColorScheme = darkColorScheme(
    primary            = Color(0xFF81C784),   // lighter green for dark backgrounds
    onPrimary          = Color(0xFF003909),
    primaryContainer   = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary          = Color(0xFFFFB74D),   // lighter orange
    secondaryContainer = Color(0xFF7B3200),
    background         = Color(0xFF121612),
    surface            = Color(0xFF1A1F1A),
    surfaceVariant     = Color(0xFF252B25),
    outline            = Color(0xFF6B6B6B),
)

@Composable
fun HastaTuCasaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),   // ← respects system setting
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}