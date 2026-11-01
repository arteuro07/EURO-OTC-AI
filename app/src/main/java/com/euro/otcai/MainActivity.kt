package com.euro.otcai

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val captureRequest = 4102
    private lateinit var status: TextView
    private val projectionManager by lazy { getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
            setBackgroundColor(0xFF101820.toInt())
        }
        fun label(text: String, size: Float = 16f) = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 12, 0, 12)
        }
        root.addView(label("EURO OTC AI", 28f))
        root.addView(label("Assistant d’observation du graphique Pocket Option"))
        status = label("État : prêt\nAucun signal validé.")
        root.addView(status)
        val overlay = Button(this).apply {
            text = "1. Autoriser la bulle flottante"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else status.text = "Bulle flottante autorisée."
            }
        }
        root.addView(overlay)
        root.addView(Button(this).apply {
            text = "2. Démarrer la capture d’écran"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    status.text = "Autorise d’abord la superposition."
                } else startActivityForResult(projectionManager.createScreenCaptureIntent(), captureRequest)
            }
        })
        root.addView(Button(this).apply {
            text = "Arrêter l’assistant"
            setOnClickListener { stopService(Intent(this@MainActivity, CaptureService::class.java)); status.text = "Assistant arrêté." }
        })
        root.addView(label("Important : la capture observe uniquement les pixels visibles. Elle ne donne pas accès au flux OTC interne ni aux cotations serveur. Tant que la lecture du graphique n’est pas validée, le résultat reste NO SIGNAL."))
        setContentView(root)
    }

    @Deprecated("Deprecated by Android but retained for compatibility with the screen-capture consent flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest && resultCode == RESULT_OK && data != null) {
            val service = Intent(this, CaptureService::class.java).apply {
                putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(CaptureService.EXTRA_DATA, data)
            }
            startForegroundService(service)
            status.text = "Capture démarrée. Analyse : NO SIGNAL (lecture en validation)."
        }
    }
}
