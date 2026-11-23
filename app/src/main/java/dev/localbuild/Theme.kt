package dev.localbuild

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette « forge » : fond charbon, braise orange, acier. */
object Forge {
    val Bg = Color(0xFF14100D)
    val Surface = Color(0xFF1F1813)
    val Ember = Color(0xFFFF7A1A)
    val Gold = Color(0xFFFFD27A)
    val Steel = Color(0xFF5B6270)
    val SteelLight = Color(0xFF8A93A6)
    val Text = Color(0xFFF3E9DF)
    val Dim = Color(0xFF9C8F84)
}

@Composable
fun ForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Forge.Ember,
            onPrimary = Color(0xFF1A0E05),
            background = Forge.Bg,
            onBackground = Forge.Text,
            surface = Forge.Bg,
            onSurface = Forge.Text,
            surfaceVariant = Forge.Surface,
            onSurfaceVariant = Forge.Dim,
            outline = Color(0xFF4A3D33),
            error = Color(0xFFFF7B6B)
        ),
        content = content
    )
}
