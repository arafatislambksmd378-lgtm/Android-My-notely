package com.notely.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemeMode { LIGHT, DARK, SYSTEM }
enum class Accent { PURPLE, GREEN, BLUE, YELLOW }

data class NotelyColors(
    val bg: Color, val surface: Color, val surface2: Color,
    val text: Color, val text2: Color, val border: Color,
    val primary: Color, val onPrimary: Color, val primaryContainer: Color,
    val isDark: Boolean = false
)

val LocalNotelyColors = staticCompositionLocalOf {
    NotelyColors(
        bg = LightBg, surface = LightSurface, surface2 = LightPurple, text = LightText,
        text2 = LightText2, border = LightBorder, primary = PrimaryPurple,
        onPrimary = Color.White, primaryContainer = LightPurple, isDark = false
    )
}

private fun accentOf(accent: Accent): Color = when (accent) {
    Accent.PURPLE -> PrimaryPurple
    Accent.GREEN -> AccentGreen
    Accent.BLUE -> AccentBlue
    Accent.YELLOW -> AccentYellow
}

@Composable
fun NotelyTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    accent: Accent = Accent.PURPLE,
    content: @Composable () -> Unit
) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val primary = accentOf(accent)
    val nc = if (dark) {
        NotelyColors(
            bg = DarkBg, surface = DarkSurface, surface2 = DarkSurface2, text = DarkText,
            text2 = DarkText2, border = DarkBorder, primary = primary, onPrimary = Color.White,
            primaryContainer = Color(0xFF3D3558), isDark = true
        )
    } else {
        NotelyColors(
            bg = LightBg, surface = LightSurface, surface2 = LightPurple, text = LightText,
            text2 = LightText2, border = LightBorder, primary = primary, onPrimary = Color.White,
            primaryContainer = LightPurple, isDark = false
        )
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = primary, onPrimary = Color.White, primaryContainer = nc.primaryContainer,
            background = nc.bg, onBackground = nc.text, surface = nc.surface, onSurface = nc.text,
            surfaceVariant = nc.surface2, onSurfaceVariant = nc.text2, outline = nc.border
        )
    } else {
        lightColorScheme(
            primary = primary, onPrimary = Color.White, primaryContainer = nc.primaryContainer,
            background = nc.bg, onBackground = nc.text, surface = nc.surface, onSurface = nc.text,
            surfaceVariant = nc.surface2, onSurfaceVariant = nc.text2, outline = nc.border
        )
    }
    CompositionLocalProvider(LocalNotelyColors provides nc) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
