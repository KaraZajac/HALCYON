package org.soulstone.halcyon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import org.soulstone.halcyon.ui.BreathingScreen
import org.soulstone.halcyon.ui.theme.HalcyonBase
import org.soulstone.halcyon.ui.theme.HalcyonCrust
import org.soulstone.halcyon.ui.theme.HalcyonTheme

/**
 * HALCYON's single screen. No permissions, no services, no persistence — the
 * whole app is one tap-to-start / tap-to-stop box-breathing animation.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HalcyonTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(HalcyonBase, HalcyonCrust)))
                ) {
                    BreathingScreen()
                }
            }
        }
    }
}
