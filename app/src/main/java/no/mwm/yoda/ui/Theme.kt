package no.mwm.yoda.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Dagobah swamp, Yoda's skin, and his burlap robe.
private val Swamp = Color(0xFF111912)
private val SwampSurface = Color(0xFF18231A)
private val SwampCard = Color(0xFF213022)
private val Skin = Color(0xFFA3B85E)
private val SkinDeep = Color(0xFF5E7A26)
private val Robe = Color(0xFFB9A57C)
private val RobeDeep = Color(0xFF6E5D3E)
private val Parchment = Color(0xFFF4EEDE)

/** Colours the Material scheme has no slot for: the stage and the speech bubble. */
data class YodaStage(val top: Color, val bottom: Color, val glow: Color)

val DarkStage = YodaStage(top = Color(0xFF0F1710), bottom = Color(0xFF27361F), glow = Color(0xFF5E8A3A))
val LightStage = YodaStage(top = Color(0xFFE9EFDC), bottom = Color(0xFFC7D5AA), glow = Color(0xFFF7FBEA))
val BubbleFill = Parchment
val BubbleInk = Color(0xFF22261C)
val BubbleHint = Color(0xFF7A6A4A)
val BubbleButton = Color(0xFF5E7A26)

private val DarkColors = darkColorScheme(
    primary = Skin,
    onPrimary = Swamp,
    primaryContainer = SwampCard,
    onPrimaryContainer = Skin,
    secondary = Robe,
    onSecondary = Swamp,
    secondaryContainer = Color(0xFF3A3526),
    onSecondaryContainer = Color(0xFFEADFC4),
    background = Swamp,
    onBackground = Parchment,
    surface = SwampSurface,
    onSurface = Parchment,
    surfaceVariant = SwampCard,
    onSurfaceVariant = Color(0xFFC6CFB8),
    outline = Color(0xFF465A45)
)

private val LightColors = lightColorScheme(
    primary = SkinDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE8C4),
    onPrimaryContainer = Color(0xFF1E2F0C),
    secondary = RobeDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6DCC3),
    onSecondaryContainer = Color(0xFF2E2615),
    background = Color(0xFFF7F5EC),
    onBackground = Color(0xFF1B1E16),
    surface = Color(0xFFEFEDE0),
    onSurface = Color(0xFF1B1E16),
    surfaceVariant = Color(0xFFE3E4D2),
    onSurfaceVariant = Color(0xFF4A4C3E),
    outline = Color(0xFF7D7F6C)
)

private val YodaTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp
    ),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp)
)

@Composable
fun YodaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    val view = LocalContext.current
    SideEffect {
        (view as? Activity)?.window?.let { w ->
            WindowCompat.getInsetsController(w, w.decorView)
                .isAppearanceLightStatusBars = !dark
        }
    }
    MaterialTheme(colorScheme = colors, typography = YodaTypography, content = content)
}
