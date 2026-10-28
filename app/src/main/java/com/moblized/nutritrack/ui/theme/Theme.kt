package com.moblized.nutritrack.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moblized.nutritrack.R
import com.moblized.nutritrack.data.NutrientGroup

private val Light = lightColorScheme(
    primary = Color(0xFF1E7A52),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F1CF),
    onPrimaryContainer = Color(0xFF002113),
    secondary = Color(0xFF4C6357),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCEE9D8),
    onSecondaryContainer = Color(0xFF092016),
    tertiary = Color(0xFF3C6472),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC0E9FA),
    onTertiaryContainer = Color(0xFF001F28),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    background = Color(0xFFF6FAF6),
    onBackground = Color(0xFF171D19),
    surface = Color(0xFFF6FAF6),
    onSurface = Color(0xFF171D19),
    surfaceVariant = Color(0xFFDBE5DD),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFBFC9C1),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5F0),
    surfaceContainer = Color(0xFFEAEFEA),
    surfaceContainerHigh = Color(0xFFE4EAE4),
    surfaceContainerHighest = Color(0xFFDFE4DF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF6EE0A8),
    onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF005234),
    onPrimaryContainer = Color(0xFFB4F1CF),
    secondary = Color(0xFFB3CCBD),
    onSecondary = Color(0xFF1F352A),
    secondaryContainer = Color(0xFF354B3F),
    onSecondaryContainer = Color(0xFFCEE9D8),
    tertiary = Color(0xFFA4CDDD),
    onTertiary = Color(0xFF053542),
    tertiaryContainer = Color(0xFF234C5A),
    onTertiaryContainer = Color(0xFFC0E9FA),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDEE4DE),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFDEE4DE),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFBFC9C1),
    outline = Color(0xFF8A938C),
    outlineVariant = Color(0xFF404943),
    surfaceContainerLowest = Color(0xFF0A0F0C),
    surfaceContainerLow = Color(0xFF171D19),
    surfaceContainer = Color(0xFF1B211D),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303632),
)

private fun ColorScheme.pureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0D0C),
    surfaceContainer = Color(0xFF101211),
    surfaceContainerHigh = Color(0xFF171A18),
    surfaceContainerHighest = Color(0xFF1E2220),
)

/** Nunito: a rounded, friendly typeface (SIL Open Font License, see third_party/nunito). */
val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_medium, FontWeight.Medium),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
)

private fun TextStyle.nunito(weight: FontWeight? = null) =
    copy(fontFamily = Nunito, fontWeight = weight ?: fontWeight)

private val AppTypography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.nunito(FontWeight.ExtraBold),
        displayMedium = t.displayMedium.nunito(FontWeight.ExtraBold),
        displaySmall = t.displaySmall.nunito(FontWeight.ExtraBold),
        headlineLarge = t.headlineLarge.nunito(FontWeight.ExtraBold),
        headlineMedium = t.headlineMedium.nunito(FontWeight.ExtraBold),
        headlineSmall = t.headlineSmall.nunito(FontWeight.Bold),
        titleLarge = t.titleLarge.nunito(FontWeight.ExtraBold),
        titleMedium = t.titleMedium.nunito(FontWeight.Bold),
        titleSmall = t.titleSmall.nunito(FontWeight.Bold),
        bodyLarge = t.bodyLarge.nunito(),
        bodyMedium = t.bodyMedium.nunito(FontWeight.Medium),
        bodySmall = t.bodySmall.nunito(FontWeight.Medium),
        labelLarge = t.labelLarge.nunito(FontWeight.Bold),
        labelMedium = t.labelMedium.nunito(FontWeight.SemiBold),
        labelSmall = t.labelSmall.nunito(FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun NutriTheme(dark: Boolean, pureBlack: Boolean, content: @Composable () -> Unit) {
    val scheme = when {
        dark && pureBlack -> Dark.pureBlack()
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
}

/** Accent color per nutrient category, readable on light and dark backgrounds. */
fun NutrientGroup.color(): Color = when (this) {
    NutrientGroup.GENERAL -> Color(0xFF7C8C84)
    NutrientGroup.CARBS -> Color(0xFF3F8EDB)
    NutrientGroup.LIPIDS -> Color(0xFFE09A2D)
    NutrientGroup.PROTEIN -> Color(0xFF2FAE6E)
    NutrientGroup.VITAMINS -> Color(0xFF9070DB)
    NutrientGroup.MINERALS -> Color(0xFF21A3A8)
}

val BigNumber = TextStyle(fontFamily = Nunito, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)

/** True when the app is showing its dark theme (which can differ from the system setting). */
@Composable
@ReadOnlyComposable
fun isAppDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** A cheerful container color per meal: sunny breakfast, fresh lunch, cozy dinner, sweet snacks. */
@Composable
@ReadOnlyComposable
fun mealColor(meal: Int): Color {
    val dark = isAppDark()
    return when (meal) {
        0 -> if (dark) Color(0xFF4A3B12) else Color(0xFFFFE9B0)
        1 -> if (dark) Color(0xFF1C3D29) else Color(0xFFCDF3D9)
        2 -> if (dark) Color(0xFF30285A) else Color(0xFFE3DBFF)
        else -> if (dark) Color(0xFF4D2234) else Color(0xFFFFD8E4)
    }
}

/** Soft tile behind a food emoji, picked from the food's name so each food keeps its color. */
@Composable
@ReadOnlyComposable
fun foodTileColor(key: String): Color {
    val light = listOf(0xFFFFE9B0, 0xFFCDF3D9, 0xFFE3DBFF, 0xFFFFD8E4, 0xFFCFE8FF, 0xFFFFDCC7)
    val dark = listOf(0xFF4A3B12, 0xFF1C3D29, 0xFF30285A, 0xFF4D2234, 0xFF173650, 0xFF4F2E1A)
    val palette = if (isAppDark()) dark else light
    return Color(palette[(key.hashCode() and 0x7fffffff) % palette.size])
}
