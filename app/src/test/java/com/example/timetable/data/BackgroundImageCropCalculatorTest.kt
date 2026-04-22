package com.example.timetable.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundImageCropCalculatorTest {
    @Test
    fun calculateCentersWideImageByDefault() {
        val crop = BackgroundImageCropCalculator.calculate(
            imageWidth = 2000,
            imageHeight = 1000,
            viewportWidth = 1000f,
            viewportHeight = 1000f,
            transform = BackgroundImageTransform(),
        )

        assertEquals(500, crop.left)
        assertEquals(0, crop.top)
        assertEquals(1000, crop.width)
        assertEquals(1000, crop.height)
    }

    @Test
    fun calculateAllowsZoomedCropToReachEdges() {
        val crop = BackgroundImageCropCalculator.calculate(
            imageWidth = 2000,
            imageHeight = 1000,
            viewportWidth = 1000f,
            viewportHeight = 1000f,
            transform = BackgroundImageTransform(
                scale = 2f,
                horizontalBias = 1f,
                verticalBias = 1f,
            ),
        )

        assertEquals(1500, crop.left)
        assertEquals(500, crop.top)
        assertEquals(500, crop.width)
        assertEquals(500, crop.height)
    }

    @Test
    fun calculateClampsOutOfRangeValues() {
        val crop = BackgroundImageCropCalculator.calculate(
            imageWidth = 1200,
            imageHeight = 1600,
            viewportWidth = 600f,
            viewportHeight = 1200f,
            transform = BackgroundImageTransform(
                scale = 99f,
                horizontalBias = -5f,
                verticalBias = 5f,
            ),
        )

        assertEquals(0, crop.left)
        assertEquals(1067, crop.top)
        assertEquals(267, crop.width)
        assertEquals(533, crop.height)
    }
}
