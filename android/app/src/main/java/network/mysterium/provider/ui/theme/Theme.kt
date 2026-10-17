package network.mysterium.provider.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import network.mysterium.provider.R

private val colorScheme = lightColorScheme(
    primary = Colors.primary,
    onPrimary = Colors.primaryBg,
    primaryContainer = Colors.blue100,
    onPrimaryContainer = Colors.blue700,
    secondary = Colors.blue600,
    onSecondary = Color.White,
    secondaryContainer = Colors.blue200,
    onSecondaryContainer = Colors.blue700,
    tertiary = Colors.pink600,
    onTertiary = Color.White,
    background = Colors.primaryBg,
    onBackground = Colors.grey800,
    surface = Colors.primaryBg,
    onSurface = Colors.grey800,
    surfaceVariant = Colors.cardBg,
    onSurfaceVariant = Colors.grey500,
    outline = Colors.borders,
    outlineVariant = Colors.grey200,
    error = Colors.red500,
    onError = Color.White,
    errorContainer = Colors.red50,
    onErrorContainer = Colors.red500,
)

@Composable
fun MysteriumTheme(
    context: Context = LocalContext.current,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Edge-to-edge on Android 16+: the window background keeps the brand
            // gradient visible behind system bars; status bar icons stay dark.
            val background = ContextCompat.getDrawable(context, R.drawable.gradient_theme)
            val window = (view.context as Activity).window
            window.setBackgroundDrawable(background)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
