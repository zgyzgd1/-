package com.example.timetable.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Composable
fun LiquidGlassPane(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    tint: Color = appSurfaceColor(),
    accent: Color = MaterialTheme.colorScheme.primary,
    content: @Composable BoxScope.() -> Unit,
) {
    val wallpaperMode = LocalGlobalBackgroundEnabled.current
    val shadowColor = accent.copy(alpha = if (wallpaperMode) 0.16f else 0.10f)
    val baseBrush = Brush.linearGradient(
        colors = listOf(
            tint.copy(alpha = if (wallpaperMode) 0.34f else 0.88f),
            tint.copy(alpha = if (wallpaperMode) 0.18f else 0.72f),
        ),
        start = Offset.Zero,
        end = Offset(900f, 1400f),
    )
    val edgeBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (wallpaperMode) 0.42f else 0.24f),
            Color.White.copy(alpha = 0.16f),
            Color.White.copy(alpha = 0.04f),
        ),
        start = Offset.Zero,
        end = Offset(1200f, 1200f),
    )

    Box(
        modifier = modifier
            .shadow(
                elevation = if (wallpaperMode) 14.dp else 6.dp,
                shape = shape,
                ambientColor = shadowColor,
                spotColor = shadowColor,
            )
            .clip(shape)
            .background(baseBrush)
            .border(width = 1.dp, brush = edgeBrush, shape = shape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (wallpaperMode) 0.18f else 0.10f),
                            Color.Transparent,
                            MaterialTheme.colorScheme.scrim.copy(alpha = if (wallpaperMode) 0.08f else 0.04f),
                        )
                    )
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = if (wallpaperMode) 0.22f else 0.12f),
                            Color.Transparent,
                        ),
                        center = Offset(220f, 80f),
                        radius = 560f,
                    )
                ),
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.78f)
                .height(30.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (wallpaperMode) 0.26f else 0.12f),
                            Color.Transparent,
                        )
                    )
                ),
        )

        content()
    }
}
