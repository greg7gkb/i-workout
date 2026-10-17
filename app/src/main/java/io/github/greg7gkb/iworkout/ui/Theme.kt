package io.github.greg7gkb.iworkout.ui

import android.content.Context
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.edit
import io.github.greg7gkb.iworkout.R
import io.github.greg7gkb.iworkout.data.local.ExerciseType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// "Volt": charcoal + electric lime. A fixed brand palette (not wallpaper-derived dynamic color),
// so the app looks the same on every phone.
private val Lime = Color(0xFFC6F432)
private val Charcoal = Color(0xFF1C1F24)

private val VoltDark = darkColorScheme(
    primary = Lime,
    onPrimary = Charcoal,
    primaryContainer = Color(0xFF3A4A0C),
    onPrimaryContainer = Color(0xFFE2FF9A),
    secondary = Color(0xFFB9C7A0),
    onSecondary = Charcoal,
    tertiary = Color(0xFFFFB020),
    background = Color(0xFF121417),
    onBackground = Color(0xFFE6E8E3),
    surface = Color(0xFF121417),
    onSurface = Color(0xFFE6E8E3),
    surfaceVariant = Color(0xFF2A2E35),
    onSurfaceVariant = Color(0xFFB4B8B0),
    surfaceContainerLowest = Color(0xFF0D0F11),
    surfaceContainerLow = Color(0xFF181B1F),
    surfaceContainer = Color(0xFF1C1F24),
    surfaceContainerHigh = Color(0xFF23272D),
    surfaceContainerHighest = Color(0xFF2A2E35),
    outline = Color(0xFF6E7369),
    error = Color(0xFFFF6B6B),
)

// In light mode lime is too pale for text, so `primary` is a deep olive; lime stays the button fill.
private val VoltLight = lightColorScheme(
    primary = Color(0xFF4A6400),
    onPrimary = Color.White,
    primaryContainer = Lime,
    onPrimaryContainer = Charcoal,
    secondary = Color(0xFF59624A),
    onSecondary = Color.White,
    tertiary = Color(0xFFB36B00),
    background = Color(0xFFF6F7F2),
    onBackground = Charcoal,
    surface = Color(0xFFF6F7F2),
    onSurface = Charcoal,
    surfaceVariant = Color(0xFFE4E7DC),
    onSurfaceVariant = Color(0xFF4A4F45),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F2EA),
    surfaceContainer = Color(0xFFEAEDE3),
    surfaceContainerHigh = Color.White, // cards
    surfaceContainerHighest = Color(0xFFDDE1D4),
    outline = Color(0xFF777C70),
    error = Color(0xFFBA1A1A),
)

// Filled buttons are Volt lime with charcoal text in both themes (light `primary` is olive, for text).
@Composable
fun limeButtonColors(): ButtonColors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Charcoal)

// One accent per Exercise type, used for the stripe on each card.
@Composable
@ReadOnlyComposable
fun ExerciseType.accent(): Color = when (this) {
    ExerciseType.WARM_UP -> Color(0xFFFFB020)
    ExerciseType.EQUIPMENT -> MaterialTheme.colorScheme.primary
    ExerciseType.BODYWEIGHT -> Color(0xFF2EC4B6)
    ExerciseType.COOL_DOWN -> Color(0xFF5B8DEF)
}

// Oswald (SIL OFL, licenses/Oswald-OFL.txt): a condensed athletic face for headings and exercise names only;
// body text stays the system font for readability. It's a variable font, so each weight sets its axis.
private fun oswald(weight: FontWeight) =
    Font(R.font.oswald, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

private val Oswald = FontFamily(oswald(FontWeight.Medium), oswald(FontWeight.SemiBold))

// Everything is read at arm's length mid-workout, so all text is scaled up from Material defaults.
private const val TEXT_SCALE = 1.3f

private fun TextStyle.scaled() = copy(fontSize = fontSize * TEXT_SCALE, lineHeight = lineHeight * TEXT_SCALE)

private fun TextStyle.display() = copy(fontFamily = Oswald, fontWeight = FontWeight.SemiBold)

private val AppTypography = Typography().run {
    Typography(
        displayLarge = displayLarge.scaled().display(),
        displayMedium = displayMedium.scaled().display(),
        displaySmall = displaySmall.scaled().display(),
        headlineLarge = headlineLarge.scaled().display(),
        headlineMedium = headlineMedium.scaled().display(),
        headlineSmall = headlineSmall.scaled().display(),
        titleLarge = titleLarge.scaled().display(),
        titleMedium = titleMedium.scaled(),
        titleSmall = titleSmall.scaled(),
        bodyLarge = bodyLarge.scaled(),
        bodyMedium = bodyMedium.scaled(),
        bodySmall = bodySmall.scaled(),
        labelLarge = labelLarge.scaled(),
        labelMedium = labelMedium.scaled(),
        labelSmall = labelSmall.scaled(),
    )
}

@Composable
fun IWorkoutTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) VoltDark else VoltLight, typography = AppTypography, content = content)
}

// The user's light/dark choice, persisted. Null until they first toggle, meaning "follow the system".
class ThemePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("theme", Context.MODE_PRIVATE)
    private val _dark = MutableStateFlow(if (prefs.contains(KEY_DARK)) prefs.getBoolean(KEY_DARK, false) else null)
    val dark: StateFlow<Boolean?> = _dark.asStateFlow()

    fun setDark(dark: Boolean) {
        prefs.edit { putBoolean(KEY_DARK, dark) }
        _dark.value = dark
    }

    private companion object {
        const val KEY_DARK = "dark"
    }
}
