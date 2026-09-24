package com.aqsama.pharmacypocket.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PharmacyColors = lightColorScheme(
    primary = Color(0xFF103E3B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEFE1),
    onPrimaryContainer = Color(0xFF175D3F),
    secondary = Color(0xFF315B49),
    onSecondary = Color.White,
    background = Color(0xFFF4F7F6),
    onBackground = Color(0xFF173C30),
    surface = Color.White,
    onSurface = Color(0xFF173C30),
    surfaceVariant = Color(0xFFE7EFEA),
    onSurfaceVariant = Color(0xFF60766D),
    outline = Color(0xFFCBD9D2),
    error = Color(0xFF9B3F2C),
)

@Composable
fun PharmacyPocketTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PharmacyColors,
        content = content,
    )
}
