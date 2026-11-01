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

        val columnRed = IntArray(right - left)
        val columnGreen = IntArray(right - left)
        val hsv = FloatArray(3)
        var redPixels = 0
        var greenPixels = 0
        var sampled = 0

        var y = top
        while (y < bottom) {
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
                            columnRed[x - left]++
                        }
                        hue in 75f..155f -> {
                            greenPixels++
                            columnGreen[x - left]++
                        }
                    }
                }
                x += STEP
            }
            y += STEP
        }

        val redColumns = columnRed.count { it >= MIN_PIXELS_PER_COLUMN }
        val greenColumns = columnGreen.count { it >= MIN_PIXELS_PER_COLUMN }
        return Observation(redColumns, greenColumns, redPixels, greenPixels, sampled)
    }
}
