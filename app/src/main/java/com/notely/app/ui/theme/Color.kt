package com.notely.app.ui.theme

import androidx.compose.ui.graphics.Color

// Exact palette from the design brief.
val PrimaryPurple = Color(0xFF8950F5)
val DeepPurple = Color(0xFF7040E8)
val LightPurple = Color(0xFFF0E9FF)
val AccentGreen = Color(0xFF39B980)
val AccentBlue = Color(0xFF438CEB)
val AccentYellow = Color(0xFFF2C94C)
val AccentPink = Color(0xFFEC5A9E)
val AccentRed = Color(0xFFEF5350)
val AccentTeal = Color(0xFF26A69A)
val AccentOrange = Color(0xFFFB8C00)

val LightBg = Color(0xFFF8F7FB)
val LightSurface = Color(0xFFFFFFFF)
val LightText = Color(0xFF24232B)
val LightText2 = Color(0xFF92909D)
val LightBorder = Color(0xFFECEAF1)

val DarkBg = Color(0xFF1B1A24)
val DarkSurface = Color(0xFF2A2938)
val DarkSurface2 = Color(0xFF35344A)
val DarkText = Color(0xFFF4F3FA)
val DarkText2 = Color(0xFFA7A5B8)
val DarkBorder = Color(0xFF3A394D)

val CardColors = listOf(
    PrimaryPurple, AccentBlue, AccentGreen, AccentYellow,
    AccentPink, AccentRed, AccentTeal, AccentOrange
)

val PageColors = listOf(
    Color(0xFFFFFFFF), Color(0xFFFFF8E7), Color(0xFFF5F0FF),
    Color(0xFFE8F5EE), Color(0xFFEEF3FB), Color(0xFFFFF1E6)
)

fun accentColor(index: Int): Color = CardColors[index % CardColors.size]
