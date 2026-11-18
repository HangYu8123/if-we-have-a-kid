package app.childrensimulator.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Day = lightColorScheme(
    primary = Color(0xFFB0503A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBD1),
    onPrimaryContainer = Color(0xFF3B0A00),
    secondary = Color(0xFF3F6A8F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4E5F7),
    onSecondaryContainer = Color(0xFF0D2236),
    tertiary = Color(0xFF6A6015),
    tertiaryContainer = Color(0xFFF3E7A6),
    onTertiaryContainer = Color(0xFF211C00),
    background = Color(0xFFFBF6F1),
    onBackground = Color(0xFF231A16),
    surface = Color(0xFFFFFBF8),
    onSurface = Color(0xFF231A16),
    surfaceVariant = Color(0xFFF3E6DE),
    onSurfaceVariant = Color(0xFF53433D),
    surfaceContainer = Color(0xFFF7EEE8),
    surfaceContainerHigh = Color(0xFFF1E6DF),
    outline = Color(0xFF85736C),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
)

/** Dim, low-stimulation palette for night care (docs/05 §7, docs/10 §5). */
private val Night = darkColorScheme(
    primary = Color(0xFFE2B48C),
    onPrimary = Color(0xFF3A2410),
    primaryContainer = Color(0xFF4A3424),
    onPrimaryContainer = Color(0xFFF4DCC6),
    secondary = Color(0xFFA9C2DB),
    onSecondary = Color(0xFF13293D),
    secondaryContainer = Color(0xFF2A3A4C),
    onSecondaryContainer = Color(0xFFD3E2F2),
    tertiary = Color(0xFFD4C98A),
    tertiaryContainer = Color(0xFF3B3714),
    onTertiaryContainer = Color(0xFFEFE6B2),
    background = Color(0xFF12141C),
    onBackground = Color(0xFFD8D2C8),
    surface = Color(0xFF181B25),
    onSurface = Color(0xFFD8D2C8),
    surfaceVariant = Color(0xFF262A36),
    onSurfaceVariant = Color(0xFFB9B2A8),
    surfaceContainer = Color(0xFF1D2030),
    surfaceContainerHigh = Color(0xFF232737),
    outline = Color(0xFF7D776F),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF5C1F1B),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    )
}

@Composable
fun ChildSimTheme(night: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (night) Night else Day, typography = AppTypography, content = content)
}

val SkinTones = listOf(Color(0xFFF6D5BD), Color(0xFFE5B48F), Color(0xFFC68A62), Color(0xFF8E5B3D))
