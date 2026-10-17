package io.github.greg7gkb.iworkout.ui

import android.content.Context
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Everything is read at arm's length mid-workout, so all text is scaled up from Material defaults.
private const val TEXT_SCALE = 1.3f

private fun TextStyle.scaled() = copy(fontSize = fontSize * TEXT_SCALE, lineHeight = lineHeight * TEXT_SCALE)

private val LargeTypography = Typography().run {
    Typography(
        displayLarge = displayLarge.scaled(),
        displayMedium = displayMedium.scaled(),
        displaySmall = displaySmall.scaled(),
        headlineLarge = headlineLarge.scaled(),
        headlineMedium = headlineMedium.scaled(),
        headlineSmall = headlineSmall.scaled(),
        titleLarge = titleLarge.scaled(),
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
    val context = LocalContext.current
    // Dynamic (wallpaper) colors need Android 12+; fall back to the Material baseline below that.
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, typography = LargeTypography, content = content)
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
