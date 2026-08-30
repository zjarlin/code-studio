package site.addzero.studio.workbench.components.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val lightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E9FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF006A6A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF9CF1F0),
    onSecondaryContainer = Color(0xFF002020),
    surface = Color(0xFFF9FAFC),
    surfaceVariant = Color(0xFFE1E6ED),
)

private val darkColors = darkColorScheme(
    primary = Color(0xFFA8C8FF),
    onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF00468A),
    onPrimaryContainer = Color(0xFFD7E9FF),
    secondary = Color(0xFF80D5D4),
    onSecondary = Color(0xFF003737),
    secondaryContainer = Color(0xFF004F4F),
    onSecondaryContainer = Color(0xFF9CF1F0),
    surface = Color(0xFF111418),
    surfaceVariant = Color(0xFF41474E),
)

@Composable
internal fun WorkbenchTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkColors else lightColors,
        content = content,
    )
}
