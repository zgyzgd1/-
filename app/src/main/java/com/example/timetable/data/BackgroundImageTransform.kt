package com.example.timetable.data

import kotlin.math.max
import kotlin.math.roundToInt

data class BackgroundImageTransform(
    val scale: Float = 1f,
    val horizontalBias: Float = 0f,
    val verticalBias: Float = 0f,
) {
    fun normalized(): BackgroundImageTransform {
        return BackgroundImageTransform(
            scale = scale.coerceIn(MIN_SCALE, MAX_SCALE),
            horizontalBias = horizontalBias.coerceIn(-1f, 1f),
            verticalBias = verticalBias.coerceIn(-1f, 1f),
        )
    }

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 3f
    }
}

data class BackgroundImageCrop(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

object BackgroundImageCropCalculator {
    fun calculate(
        imageWidth: Int,
        imageHeight: Int,
        viewportWidth: Float,
        viewportHeight: Float,
        transform: BackgroundImageTransform,
    ): BackgroundImageCrop {
        if (imageWidth <= 0 || imageHeight <= 0 || viewportWidth <= 0f || viewportHeight <= 0f) {
            return BackgroundImageCrop(
                left = 0,
                top = 0,
                width = imageWidth.coerceAtLeast(1),
                height = imageHeight.coerceAtLeast(1),
            )
        }

        val normalized = transform.normalized()
        val baseScale = max(
            viewportWidth / imageWidth.toFloat(),
            viewportHeight / imageHeight.toFloat(),
        )
        val finalScale = baseScale * normalized.scale

        val cropWidth = (viewportWidth / finalScale).roundToInt().coerceIn(1, imageWidth)
        val cropHeight = (viewportHeight / finalScale).roundToInt().coerceIn(1, imageHeight)

        val maxLeft = (imageWidth - cropWidth).coerceAtLeast(0)
        val maxTop = (imageHeight - cropHeight).coerceAtLeast(0)
        val left = (maxLeft * biasToFraction(normalized.horizontalBias)).roundToInt().coerceIn(0, maxLeft)
        val top = (maxTop * biasToFraction(normalized.verticalBias)).roundToInt().coerceIn(0, maxTop)

        return BackgroundImageCrop(
            left = left,
            top = top,
            width = cropWidth,
            height = cropHeight,
        )
    }

    private fun biasToFraction(bias: Float): Float = (bias + 1f) / 2f
}
