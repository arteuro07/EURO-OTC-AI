package com.euro.otcai

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DiagnosticsStore {
    const val FILE_NAME = "euro_otc_diagnostic.csv"

    @Synchronized
    fun append(
        context: Context,
        asset: String,
        timeframe: String,
        frameCount: Long,
        redColumns: Int,
        greenColumns: Int,
        redSegments: Int,
        greenSegments: Int,
        ocr: String,
        status: String
    ) {
        try {
            val file = context.getFileStreamPath(FILE_NAME)
            val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
            context.openFileOutput(FILE_NAME, Context.MODE_APPEND).bufferedWriter().use { out ->
                if (file.length() == 0L) {
                    out.append("timestamp_utc,asset,timeframe,frame_count,red_columns,green_columns,red_segments,green_segments,ocr,status\n")
                }
                val fields = listOf(
                    stamp, asset, timeframe, frameCount.toString(),
                    redColumns.toString(), greenColumns.toString(),
                    redSegments.toString(), greenSegments.toString(), ocr, status
                )
                out.append(fields.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
                out.append('\n')
            }
        } catch (_: Exception) {
            // Logging failures must never stop screen capture.
        }
    }
}
