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

private val Swamp = Color(0xFF0E1A12)
private val SwampSurface = Color(0xFF16241A)
private val SwampCard = Color(0xFF1D2F22)
private val Saber = Color(0xFF8CC152)
private val SaberDim = Color(0xFF6FA03F)
private val Parchment = Color(0xFFF2F5EC)
private val Robe = Color(0xFFB89B6E)

private val DarkColors = darkColorScheme(
    primary = Saber,
    onPrimary = Swamp,
    primaryContainer = SwampCard,
    onPrimaryContainer = Saber,
    secondary = Robe,
    onSecondary = Swamp,
    background = Swamp,
    onBackground = Parchment,
    surface = SwampSurface,
    onSurface = Parchment,
    surfaceVariant = SwampCard,
    onSurfaceVariant = Color(0xFFC3CFBC),
    outline = Color(0xFF445A49)
)

private val LightColors = lightColorScheme(
    primary = SaberDim,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEBC8),
    onPrimaryContainer = Color(0xFF1B3310),
    secondary = Color(0xFF7A6544),
    background = Color(0xFFFBFDF6),
    onBackground = Color(0xFF191D17),
    surface = Color(0xFFF3F6EC),
    onSurface = Color(0xFF191D17),
    surfaceVariant = Color(0xFFE1E8D8),
    onSurfaceVariant = Color(0xFF444B41),
    outline = Color(0xFF757D70)
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
