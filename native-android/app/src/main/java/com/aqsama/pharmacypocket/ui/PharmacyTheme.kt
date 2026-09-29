package com.aqsama.pharmacypocket.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.aqsama.pharmacypocket.data.ThemePreference

private val LightPharmacyColors = lightColorScheme(
    primary = Color(0xFF103E3B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEFE1),
    onPrimaryContainer = Color(0xFF175D3F),
    secondary = Color(0xFF315B49),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5F0EA),
    onSecondaryContainer = Color(0xFF244A3C),
    tertiary = Color(0xFF103E3B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCEFE1),
    onTertiaryContainer = Color(0xFF175D3F),
    background = Color(0xFFF2F5F1),
    onBackground = Color(0xFF173C30),
    surface = Color.White,
    onSurface = Color(0xFF173C30),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEBF0E9),
    surfaceContainer = Color(0xFFE4EBE2),
    surfaceContainerHigh = Color(0xFFDDE5DA),
    surfaceContainerHighest = Color(0xFFD6DFD3),
    surfaceVariant = Color(0xFFE7EFEA),
    onSurfaceVariant = Color(0xFF60766D),
    outline = Color(0xFFCBD9D2),
    outlineVariant = Color(0xFFE0E8E4),
    error = Color(0xFF9B3F2C),
    onError = Color.White,
)

private val DarkPharmacyColors = darkColorScheme(
    primary = Color(0xFF8ED7B5),
    onPrimary = Color(0xFF003829),
    primaryContainer = Color(0xFF1D4C3A),
    onPrimaryContainer = Color(0xFFC8F2DD),
    secondary = Color(0xFFB5CCBF),
    onSecondary = Color(0xFF20352C),
    secondaryContainer = Color(0xFF2A4036),
    onSecondaryContainer = Color(0xFFD7E9DF),
    tertiary = Color(0xFF244B3B),
    onTertiary = Color(0xFFE6F4EC),
    tertiaryContainer = Color(0xFF213B31),
    onTertiaryContainer = Color(0xFFC9EAD9),
    background = Color(0xFF0E1512),
    onBackground = Color(0xFFE1EAE5),
    surface = Color(0xFF151D1A),
    onSurface = Color(0xFFE1EAE5),
    surfaceContainerLowest = Color(0xFF0B120F),
    surfaceContainerLow = Color(0xFF19221D),
    surfaceContainer = Color(0xFF202A23),
    surfaceContainerHigh = Color(0xFF27322B),
    surfaceContainerHighest = Color(0xFF303B33),
    surfaceVariant = Color(0xFF22302A),
    onSurfaceVariant = Color(0xFFB7C8BF),
    outline = Color(0xFF677A71),
    outlineVariant = Color(0xFF33443D),
    error = Color(0xFFFFB4A5),
    onError = Color(0xFF690005),
)

internal val LocalPharmacyDarkTheme = staticCompositionLocalOf { false }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun PharmacyPocketTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themePreference) {
        ThemePreference.SYSTEM -> systemDark
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()

    if (!view.isInEditMode) {
        SideEffect {
            val window = activity?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalPharmacyDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkPharmacyColors else LightPharmacyColors,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp),
                extraLarge = RoundedCornerShape(36.dp),
            ),
            typography = Typography(
                headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
                headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
                titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
                titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            ),
            content = content,
        )
    }
}
