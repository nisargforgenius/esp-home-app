package com.example.iotcontroller.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Every selectable app theme. `swatch` is the color shown for this theme's
 * entry in the picker UI (roughly its primary/accent), so the list itself
 * doubles as a visual preview without needing to render a full screen.
 */
enum class AppTheme(
    val label: String,
    val swatch: Color,
    val colorScheme: ColorScheme
) {
    FOREST_GREEN(
        label = "Forest Green",
        swatch = Color(0xFF2E5B3E),
        colorScheme = darkColorScheme(
            primary = Color(0xFF6FCF97),
            onPrimary = Color(0xFF0B2B14),
            primaryContainer = Color(0xFF2E5B3E),
            onPrimaryContainer = Color(0xFFB8E8C6),
            secondary = Color(0xFF9AD1AE),
            background = Color(0xFF122318),
            onBackground = Color(0xFFE3F0E6),
            surface = Color(0xFF16301F),
            onSurface = Color(0xFFE3F0E6),
            surfaceVariant = Color(0xFF204630),
            onSurfaceVariant = Color(0xFFBFD9C7),
            error = Color(0xFFFFB4AB)
        )
    ),
    SLATE_BLUE(
        label = "Slate Blue",
        swatch = Color(0xFF3B4A63),
        colorScheme = darkColorScheme(
            primary = Color(0xFF8FB8E8),
            onPrimary = Color(0xFF0B1E33),
            primaryContainer = Color(0xFF32476B),
            onPrimaryContainer = Color(0xFFCBDFF7),
            secondary = Color(0xFFA9C2DD),
            background = Color(0xFF10161F),
            onBackground = Color(0xFFE2E8F0),
            surface = Color(0xFF17202D),
            onSurface = Color(0xFFE2E8F0),
            surfaceVariant = Color(0xFF263449),
            onSurfaceVariant = Color(0xFFC0CCDD),
            error = Color(0xFFFFB4AB)
        )
    ),
    DEEP_PURPLE(
        label = "Deep Purple",
        swatch = Color(0xFF4A2E63),
        colorScheme = darkColorScheme(
            primary = Color(0xFFCBA6E8),
            onPrimary = Color(0xFF2A1338),
            primaryContainer = Color(0xFF4A2E63),
            onPrimaryContainer = Color(0xFFE7D3F5),
            secondary = Color(0xFFB79CC7),
            background = Color(0xFF1B1023),
            onBackground = Color(0xFFEAE0EF),
            surface = Color(0xFF24152F),
            onSurface = Color(0xFFEAE0EF),
            surfaceVariant = Color(0xFF3B2749),
            onSurfaceVariant = Color(0xFFD1BEDD),
            error = Color(0xFFFFB4AB)
        )
    ),
    NEUTRAL_GREY(
        label = "Neutral Grey",
        swatch = Color(0xFF5A5A5E),
        colorScheme = darkColorScheme(
            primary = Color(0xFFC7C6CA),
            onPrimary = Color(0xFF2B2B2E),
            primaryContainer = Color(0xFF48484C),
            onPrimaryContainer = Color(0xFFE2E1E5),
            secondary = Color(0xFFA9A9AD),
            background = Color(0xFF19191B),
            onBackground = Color(0xFFE4E3E6),
            surface = Color(0xFF212123),
            onSurface = Color(0xFFE4E3E6),
            surfaceVariant = Color(0xFF37373A),
            onSurfaceVariant = Color(0xFFC7C6CA),
            error = Color(0xFFFFB4AB)
        )
    ),
    ROSY_PINK(
        label = "Rosy Pink",
        swatch = Color(0xFF9C4A5C),
        colorScheme = darkColorScheme(
            primary = Color(0xFFF2A6B8),
            onPrimary = Color(0xFF3A0F1A),
            primaryContainer = Color(0xFF7A3646),
            onPrimaryContainer = Color(0xFFFFD9E1),
            secondary = Color(0xFFE0A3AF),
            background = Color(0xFF201115),
            onBackground = Color(0xFFF2E0E3),
            surface = Color(0xFF2B171C),
            onSurface = Color(0xFFF2E0E3),
            surfaceVariant = Color(0xFF4A2830),
            onSurfaceVariant = Color(0xFFDBBAC1),
            error = Color(0xFFFFB4AB)
        )
    ),
    RICH_ROSY_PINK(
        label = "Rich Rosy Pink",
        swatch = Color(0xFF7A1F32),
        colorScheme = darkColorScheme(
            primary = Color(0xFFFF8FA3),
            onPrimary = Color(0xFF3A0512),
            primaryContainer = Color(0xFF7A1F32),
            onPrimaryContainer = Color(0xFFFFD6DE),
            secondary = Color(0xFFE87E93),
            background = Color(0xFF230910),
            onBackground = Color(0xFFF5DEE2),
            surface = Color(0xFF33101A),
            onSurface = Color(0xFFF5DEE2),
            surfaceVariant = Color(0xFF57202D),
            onSurfaceVariant = Color(0xFFE3B4BE),
            error = Color(0xFFFFB4AB)
        )
    ),
    VIBRANT_ORANGE(
        label = "Vibrant Orange",
        swatch = Color(0xFFD9711F),
        colorScheme = darkColorScheme(
            primary = Color(0xFFFFB37A),
            onPrimary = Color(0xFF3D1C00),
            primaryContainer = Color(0xFFA3540F),
            onPrimaryContainer = Color(0xFFFFDCC0),
            secondary = Color(0xFFF0A365),
            background = Color(0xFF241207),
            onBackground = Color(0xFFF7E2D2),
            surface = Color(0xFF321A0B),
            onSurface = Color(0xFFF7E2D2),
            surfaceVariant = Color(0xFF5C331A),
            onSurfaceVariant = Color(0xFFE7C2A8),
            error = Color(0xFFFFB4AB)
        )
    ),
    SKY_LIGHT(
        label = "Sky Light",
        swatch = Color(0xFF7FB8E0),
        colorScheme = lightColorScheme(
            primary = Color(0xFF2E6FA3),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFCFE6F7),
            onPrimaryContainer = Color(0xFF102F45),
            secondary = Color(0xFF4A7FA8),
            background = Color(0xFFF3F8FC),
            onBackground = Color(0xFF16232C),
            surface = Color(0xFFE6F1FA),
            onSurface = Color(0xFF16232C),
            surfaceVariant = Color(0xFFD3E5F0),
            onSurfaceVariant = Color(0xFF3E5567),
            error = Color(0xFFBA1A1A)
        )
    ),
    RICH_VIOLET(
        label = "Rich Violet",
        swatch = Color(0xFF5B2E8C),
        colorScheme = darkColorScheme(
            primary = Color(0xFFC9A6F0),
            onPrimary = Color(0xFF2E1149),
            primaryContainer = Color(0xFF4C2A73),
            onPrimaryContainer = Color(0xFFE7D6FA),
            secondary = Color(0xFFAE93CC),
            background = Color(0xFF190F26),
            onBackground = Color(0xFFE7DFF0),
            surface = Color(0xFF221733),
            onSurface = Color(0xFFE7DFF0),
            surfaceVariant = Color(0xFF3B2A52),
            onSurfaceVariant = Color(0xFFCBBADE),
            error = Color(0xFFFFB4AB)
        )
    );
}
