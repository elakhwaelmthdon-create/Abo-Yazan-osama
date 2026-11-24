package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val BonYazanLightColorScheme = lightColorScheme(
    primary = EspressoPrimary,
    onPrimary = OnEspressoPrimary,
    primaryContainer = EspressoContainer,
    onPrimaryContainer = OnEspressoContainer,
    secondary = MochaSecondary,
    onSecondary = OnMochaSecondary,
    secondaryContainer = MochaContainer,
    onSecondaryContainer = OnMochaContainer,
    tertiary = CremaGoldTertiary,
    onTertiary = OnCremaGoldTertiary,
    tertiaryContainer = CremaGoldContainer,
    onTertiaryContainer = OnCremaGoldContainer,
    background = OatMilkBackground,
    onBackground = OnWarmSurface,
    surface = CreamSurface,
    onSurface = OnWarmSurface,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = OnWarmSurfaceVariant,
    outline = WarmOutline,
    outlineVariant = WarmOutline,
)

private val BonYazanDarkColorScheme = darkColorScheme(
    primary = CremaPrimaryDark,
    onPrimary = OnCremaPrimaryDark,
    primaryContainer = EspressoContainerDark,
    onPrimaryContainer = OnEspressoContainerDark,
    secondary = MochaSecondaryDark,
    onSecondary = OnCremaPrimaryDark,
    secondaryContainer = DarkRoastSurfaceVariant,
    onSecondaryContainer = OnDarkSurface,
    tertiary = CremaGoldDark,
    onTertiary = OnCremaPrimaryDark,
    tertiaryContainer = EspressoContainerDark,
    onTertiaryContainer = CremaGoldContainer,
    background = DarkRoastBackground,
    onBackground = OnDarkSurface,
    surface = DarkRoastSurface,
    onSurface = OnDarkSurface,
    surfaceVariant = DarkRoastSurfaceVariant,
    onSurfaceVariant = OnDarkSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
)

val BonYazanShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun BonYazanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) BonYazanDarkColorScheme else BonYazanLightColorScheme

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BonYazanTypography,
            shapes = BonYazanShapes,
            content = content,
        )
    }
}
