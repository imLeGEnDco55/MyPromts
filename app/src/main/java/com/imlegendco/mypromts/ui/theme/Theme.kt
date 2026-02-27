package com.imlegendco.mypromts.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Esquema de colores oscuro personalizado
private val MyPromtsDarkColorScheme = darkColorScheme(
    primary = MagentaPrimary,
    onPrimary = TextPrimary,
    primaryContainer = MagentaDark,
    onPrimaryContainer = TextPrimary,
    
    secondary = AccentCyan,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = TextPrimary,
    
    tertiary = AccentGold,
    onTertiary = DarkBackground,
    tertiaryContainer = DarkSurfaceVariant,
    onTertiaryContainer = TextPrimary,
    
    background = DarkBackground,
    onBackground = TextPrimary,
    
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    
    error = ErrorRed,
    onError = TextPrimary,
    
    outline = MagentaPrimary.copy(alpha = 0.5f)
)

@Composable
fun MyPromtsTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MyPromtsDarkColorScheme,
        typography = Typography,
        content = content
    )
}