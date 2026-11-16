package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class SmartStoreExtendedColors(
    val violetPrimary: Color = VioletPrimary,
    val midnightSecondary: Color = MidnightBlue,
    val goldPremium: Color = GoldPremium,
    val goldSoftBg: Color = GoldSoft,
    val goldOnSoft: Color = GoldOnSoft,
    val successGreen: Color = GreenSuccess,
    val successSoftBg: Color = GreenSuccessSoft,
    val fintechAccent: Color = VioletPrimary,
    val mtnAccent: Color = GoldPremium,
    val orangeAccent: Color = Color(0xFFEA580C),
    val heroGradientStart: Color = MidnightBlue,
    val heroGradientEnd: Color = VioletPrimary,
    val cardBorder: Color = BorderLight,
    val barcodeBg: Color = CardWhite,
    val barcodeInk: Color = TextPrimaryInk
)

val LocalSmartStoreColors = staticCompositionLocalOf { SmartStoreExtendedColors() }

private val LightExtendedColors = SmartStoreExtendedColors(
    violetPrimary = VioletPrimary,
    midnightSecondary = MidnightBlue,
    goldPremium = GoldPremium,
    goldSoftBg = GoldSoft,
    goldOnSoft = GoldOnSoft,
    successGreen = GreenSuccess,
    successSoftBg = GreenSuccessSoft,
    fintechAccent = VioletPrimary,
    mtnAccent = GoldPremium,
    orangeAccent = Color(0xFFEA580C),
    heroGradientStart = MidnightBlue,
    heroGradientEnd = VioletPrimary,
    cardBorder = BorderLight,
    barcodeBg = CardWhite,
    barcodeInk = TextPrimaryInk
)

private val DarkExtendedColors = SmartStoreExtendedColors(
    violetPrimary = VioletPrimaryDarkMode,
    midnightSecondary = MidnightBlueLight,
    goldPremium = GoldPremium,
    goldSoftBg = Color(0xFF3A2E0B),
    goldOnSoft = Color(0xFFFDE68A),
    successGreen = Color(0xFF22C55E),
    successSoftBg = Color(0xFF0F3D22),
    fintechAccent = VioletPrimaryDarkMode,
    mtnAccent = GoldPremium,
    orangeAccent = Color(0xFFF97316),
    heroGradientStart = Color(0xFF0F172A),
    heroGradientEnd = Color(0xFF3E299E),
    cardBorder = DarkBorder,
    barcodeBg = CardWhite,
    barcodeInk = TextPrimaryInk
)

private val SmartStoreLightColorScheme = lightColorScheme(
    primary = VioletPrimary,
    onPrimary = Color.White,
    primaryContainer = VioletSoft,
    onPrimaryContainer = VioletOnSoft,
    secondary = MidnightBlue,
    onSecondary = Color.White,
    secondaryContainer = MidnightBlueSoft,
    onSecondaryContainer = MidnightBlue,
    tertiary = GreenSuccess,
    onTertiary = Color.White,
    tertiaryContainer = GreenSuccessSoft,
    onTertiaryContainer = GreenOnSuccessSoft,
    background = CanvasLight,
    onBackground = TextPrimaryInk,
    surface = CardWhite,
    onSurface = TextPrimaryInk,
    surfaceVariant = SurfaceMutedLight,
    onSurfaceVariant = TextSecondaryInk,
    surfaceContainerLowest = CardWhite,
    surfaceContainerLow = CanvasLight,
    surfaceContainer = CardWhite,
    surfaceContainerHigh = SurfaceMutedLight,
    outline = BorderLight,
    outlineVariant = HairlineLight,
    error = DangerRed,
    onError = Color.White,
    errorContainer = DangerRedSoft,
    onErrorContainer = DangerOnSoft
)

private val SmartStoreDarkColorScheme = darkColorScheme(
    primary = VioletPrimaryDarkMode,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF332182),
    onPrimaryContainer = Color(0xFFE9E4FF),
    secondary = Color(0xFF60A5FA),
    onSecondary = MidnightBlue,
    secondaryContainer = MidnightBlue,
    onSecondaryContainer = Color(0xFFDBEAFE),
    tertiary = Color(0xFF22C55E),
    onTertiary = Color(0xFF052E16),
    tertiaryContainer = Color(0xFF14532D),
    onTertiaryContainer = Color(0xFFDCFCE7),
    background = DarkCanvas,
    onBackground = TextPrimaryDarkMode,
    surface = DarkCard,
    onSurface = TextPrimaryDarkMode,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondaryDarkMode,
    surfaceContainerLowest = DarkCanvas,
    surfaceContainerLow = DarkCard,
    surfaceContainer = DarkCard,
    surfaceContainerHigh = DarkSurfaceElevated,
    outline = DarkBorder,
    outlineVariant = Color(0xFF1F2A40),
    error = Color(0xFFEF4444),
    onError = Color.White,
    errorContainer = Color(0xFF450A0A),
    onErrorContainer = Color(0xFFFECACA)
)

// Rounded 14dp–18dp luxury card & control geometry
val SmartStoreShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(18.dp)
)

@Composable
fun SmartStoreProTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SmartStoreDarkColorScheme else SmartStoreLightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors
    CompositionLocalProvider(LocalSmartStoreColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SmartStoreTypography,
            shapes = SmartStoreShapes,
            content = content
        )
    }
}
