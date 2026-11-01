package com.euro.otcai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale

class CaptureService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "result_data"
        const val EXTRA_ASSET = "selected_asset"
        const val EXTRA_TIMEFRAME = "selected_timeframe"
        private const val CHANNEL = "euro_otc_capture"
    }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var bubble: TextView? = null
    private var windowManager: WindowManager? = null
    private var frameThread: HandlerThread? = null
    private var frameCount = 0L
    @Volatile private var ocrBusy = false
    private var lastOcrText = "En attente du premier texte"
    private var selectedAsset = "Actif non sélectionné"
    private var selectedTimeframe = "M1"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(7, notification())

        selectedAsset = intent?.getStringExtra(EXTRA_ASSET) ?: "Actif non sélectionné"
        selectedTimeframe = intent?.getStringExtra(EXTRA_TIMEFRAME) ?: "M1"
        val code = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        @Suppress("DEPRECATION")
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            intent?.getParcelableExtra(EXTRA_DATA)
        }
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(code, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, mainHandler)

        val metrics = resources.displayMetrics
        frameThread = HandlerThread("EuroOtcFrameReader").apply { start() }
        reader = ImageReader.newInstance(
            metrics.widthPixels,
            metrics.heightPixels,
            PixelFormat.RGBA_8888,
            3
        )
        reader?.setOnImageAvailableListener({ source ->
            val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
            frameCount += 1
            if (frameCount % 45L == 0L && !ocrBusy) {
                val bitmap = imageToBitmap(image)
                if (bitmap != null) runOcr(bitmap)
            }
            image.close()
            if (frameCount == 1L || frameCount % 15L == 0L) {
                updateBubble()
            }
        }, Handler(frameThread!!.looper))

        display = projection?.createVirtualDisplay(
            "EURO-OTC-AI",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader?.surface,
            null,
            null
        )
        showBubble()
        return START_NOT_STICKY
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        return try {
            val plane = image.planes.firstOrNull() ?: return null
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            if (pixelStride <= 0) return null
            val paddedWidth = rowStride / pixelStride
            val full = Bitmap.createBitmap(
                paddedWidth,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            buffer.rewind()
            full.copyPixelsFromBuffer(buffer)
            val cropped = if (paddedWidth != image.width) {
                Bitmap.createBitmap(full, 0, 0, image.width, image.height).also {
                    full.recycle()
                }
            } else full
            cropped
        } catch (_: Exception) {
            null
        }
    }

    private fun runOcr(bitmap: Bitmap) {
        ocrBusy = true
        try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { result ->
                    val candidates = result.textBlocks
                        .flatMap { it.lines }
                        .map { it.text.trim() }
                        .filter { line ->
                            val upper = line.uppercase(Locale.ROOT)
                            upper.contains("OTC") ||
                                upper.contains("EUR") ||
                                upper.contains("USD") ||
                                upper.contains("M1") ||
                                upper.contains("M5") ||
                                upper.contains("M15") ||
                                upper.contains("BUY") ||
                                upper.contains("SELL") ||
                                line.any { it.isDigit() }
                        }
                        .distinct()
                        .take(2)
                    lastOcrText = if (candidates.isEmpty()) {
                        "Texte du graphique non identifié"
                    } else {
                        candidates.joinToString(" | ").take(90)
                    }
                    updateBubble()
                }
                .addOnFailureListener {
                    lastOcrText = "Lecture OCR indisponible"
                    updateBubble()
                }
                .addOnCompleteListener {
                    bitmap.recycle()
                    ocrBusy = false
                }
        } catch (_: Exception) {
            bitmap.recycle()
            ocrBusy = false
            lastOcrText = "Lecture OCR indisponible"
        }
    }

    private fun updateBubble() {
        val count = frameCount
        val text = "EURO AI\n$selectedAsset • $selectedTimeframe\nCAPTURE ACTIVE\nImages reçues : $count\nOCR : $lastOcrText\nNO SIGNAL"
        mainHandler.post { bubble?.text = text }
    }

    private fun showBubble() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = TextView(this).apply {
            text = "EURO AI\n$selectedAsset • $selectedTimeframe\nCAPTURE ACTIVE\nImages reçues : $frameCount\nOCR : $lastOcrText\nNO SIGNAL"
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xEE17212B.toInt())
            setPadding(22, 16, 22, 16)
        }
        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 20
            y = 180
        }
        try {
            windowManager?.addView(view, params)
            bubble = view
        } catch (_: Exception) {
            // Overlay permission may have been revoked while the service was active.
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL,
                "EURO OTC AI capture",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("EURO OTC AI actif")
            .setContentText("Capture et lecture OCR locales • Aucun signal validé")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        reader?.setOnImageAvailableListener(null, null)
        try {
            bubble?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {
        }
        display?.release()
        reader?.close()
        frameThread?.quitSafely()
        projection?.stop()
        try {
            recognizer.close()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
