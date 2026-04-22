package com.example.timetable.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.timetable.data.BackgroundImageCropCalculator
import com.example.timetable.data.BackgroundImageTransform
import kotlin.math.roundToInt

@Composable
fun CustomBackgroundImage(
    bitmap: ImageBitmap,
    imageTransform: BackgroundImageTransform,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.clipToBounds()) {
        val destinationWidth = size.width.roundToInt().coerceAtLeast(1)
        val destinationHeight = size.height.roundToInt().coerceAtLeast(1)
        val crop = BackgroundImageCropCalculator.calculate(
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            viewportWidth = size.width,
            viewportHeight = size.height,
            transform = imageTransform,
        )

        drawImage(
            image = bitmap,
            srcOffset = IntOffset(crop.left, crop.top),
            srcSize = IntSize(crop.width, crop.height),
            dstSize = IntSize(destinationWidth, destinationHeight),
            filterQuality = FilterQuality.Medium,
        )
    }
}
