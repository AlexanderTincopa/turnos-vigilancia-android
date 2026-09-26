package com.alexandertm.turnosvigilancia.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AppBlue = Color(0xFF0B5CAD)
val AppBlue2 = Color(0xFF1E73E8)
val AppBg = Color(0xFFF7F9FC)
val AppText = Color(0xFF0F172A)
val AppMuted = Color(0xFF64748B)

private val Colors = lightColorScheme(
    primary = AppBlue,
    secondary = AppBlue2,
    background = AppBg,
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = AppText,
    onSurface = AppText
)

@Composable
fun TurnosTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}

fun colorFromHex(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(Color.Gray)
