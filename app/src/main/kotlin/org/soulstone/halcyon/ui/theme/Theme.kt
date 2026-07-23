package org.soulstone.halcyon.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Catppuccin Mocha — HALCYON matches VIGIL / OVERWATCH.
private val Base = Color(0xFF1E1E2E)
private val Mantle = Color(0xFF181825)
private val Crust = Color(0xFF11111B)
private val Surface0 = Color(0xFF313244)
private val Surface1 = Color(0xFF45475A)
private val Text = Color(0xFFCDD6F4)
private val Subtext = Color(0xFFA6ADC8)
private val Blue = Color(0xFF89B4FA)
private val Mauve = Color(0xFFCBA6F7)

// Surface anchors exposed for the animation.
val HalcyonBase = Base
val HalcyonCrust = Crust
val HalcyonText = Text
val HalcyonSubtext = Subtext

// The breath is green, and its vibrancy tracks the lungs: muted + dark when
// empty, vivid + luminous when full. Anchored on Catppuccin Mocha's greens.
val HalcyonGreenEmpty = Color(0xFF3E5A4A)  // breathed out — muted, low sage
val HalcyonGreenMid = Color(0xFF74C79E)    // transition green
val HalcyonGreenFull = Color(0xFFA6E3A1)   // Catppuccin Green — vibrant
val HalcyonGreenPeak = Color(0xFFCFF7C4)   // the crest of a full inhale — bright mint
val HalcyonMint = Color(0xFFB9F0C4)         // spark/highlight tint

private val MochaScheme = darkColorScheme(
    primary = Blue,
    onPrimary = Base,
    secondary = Mauve,
    background = Base,
    onBackground = Text,
    surface = Mantle,
    onSurface = Text,
    surfaceVariant = Surface0,
    onSurfaceVariant = Subtext,
    outline = Surface1
)

@Composable
fun HalcyonTheme(content: @Composable () -> Unit) {
    // HALCYON is dark/Mocha regardless of system setting, matching VIGIL.
    MaterialTheme(colorScheme = MochaScheme, content = content)
}
