package com.euro.otcai

import android.graphics.Bitmap
import android.graphics.Color

/**
 * First-pass visual calibration for the Pocket Option layout seen in the
 * 2026-11-01 reference screenshot. This measures colored chart columns only;
 * it does NOT identify completed candles or generate trading signals.
 */
object ChartFrameAnalyzer {
    data class Observation(
        val redColumns: Int,
        val greenColumns: Int,
        val redPixels: Int,
        val greenPixels: Int,
        val redSegments: Int,
        val greenSegments: Int,
        val sampledPixels: Int
    )

    // Normalized plot window: exclude the asset selector, right price scale,
    // order controls and RSI panel. Recalibrate if the app layout changes.
    private const val LEFT = 0.00f
    private const val TOP = 0.24f
    private const val RIGHT = 0.80f
    private const val BOTTOM = 0.58f
    private const val STEP = 2
    private const val MIN_PIXELS_PER_COLUMN = 4

    fun analyze(bitmap: Bitmap): Observation {
        val left = (bitmap.width * LEFT).toInt().coerceIn(0, bitmap.width - 1)
        val top = (bitmap.height * TOP).toInt().coerceIn(0, bitmap.height - 1)
        val right = (bitmap.width * RIGHT).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = (bitmap.height * BOTTOM).toInt().coerceIn(top + 1, bitmap.height)

        val sampledWidth = ((right - left) + STEP - 1) / STEP
        val columnRed = IntArray(sampledWidth)
        val columnGreen = IntArray(sampledWidth)
        val hsv = FloatArray(3)
        var redPixels = 0
        var greenPixels = 0
        var sampled = 0

        var y = top
        while (y < bottom) {
            var sampleX = 0
            var x = left
            while (x < right) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                sampled++
                val hue = hsv[0]
                val saturation = hsv[1]
                val value = hsv[2]
                if (saturation >= 0.48f && value >= 0.42f) {
                    when {
                        hue <= 15f || hue >= 345f -> {
                            redPixels++
                            columnRed[sampleX]++
                        }
                        hue in 75f..155f -> {
                            greenPixels++
                            columnGreen[sampleX]++
                        }
                    }
                }
                x += STEP
                sampleX++
            }
            y += STEP
        }

        val redActive = columnRed.map { it >= MIN_PIXELS_PER_COLUMN }
        val greenActive = columnGreen.map { it >= MIN_PIXELS_PER_COLUMN }
        val redColumns = redActive.count { it }
        val greenColumns = greenActive.count { it }
        fun countSegments(active: List<Boolean>): Int {
            var segments = 0
            var inside = false
            for (isActive in active) {
                if (isActive && !inside) segments++
                inside = isActive
            }
            return segments
        }
        val redSegments = countSegments(redActive)
        val greenSegments = countSegments(greenActive)
        return Observation(redColumns, greenColumns, redPixels, greenPixels,
            redSegments, greenSegments, sampled)
    }
}
