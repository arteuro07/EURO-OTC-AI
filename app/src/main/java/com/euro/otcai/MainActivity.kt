package com.euro.otcai

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView

class MainActivity : Activity() {
    private val captureRequest = 4102
    private val exportRequest = 4103
    private lateinit var status: TextView
    private lateinit var assetSpinner: Spinner
    private lateinit var timeframeSpinner: Spinner
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
        fun spinner(items: List<String>): Spinner {
            return Spinner(this).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items).also {
                    it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        }

        root.addView(label("EURO OTC AI", 28f))
        root.addView(label("Assistant d’observation du graphique Pocket Option"))
        root.addView(label("Actif affiché sur Pocket Option"))
        assetSpinner = spinner(listOf("EUR/USD OTC", "GBP/USD OTC", "USD/JPY OTC", "AUD/USD OTC", "Autre OTC"))
        root.addView(assetSpinner)
        root.addView(label("Unité de temps principale"))
        timeframeSpinner = spinner(listOf("M1", "M5", "M15"))
        root.addView(timeframeSpinner)

        status = label("État : prêt\nAucun signal validé.")
        root.addView(status)
        root.addView(Button(this).apply {
            text = "1. Autoriser la bulle flottante"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else status.text = "Bulle flottante autorisée."
            }
        })
        root.addView(Button(this).apply {
            text = "2. Démarrer la capture d’écran"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    status.text = "Autorise d’abord la superposition."
                } else startActivityForResult(projectionManager.createScreenCaptureIntent(), captureRequest)
            }
        })
        root.addView(Button(this).apply {
            text = "Exporter le diagnostic CSV"
            setOnClickListener {
                if (!getFileStreamPath(DiagnosticsStore.FILE_NAME).exists() || getFileStreamPath(DiagnosticsStore.FILE_NAME).length() == 0L) {
                    status.text = "Aucune donnée enregistrée pour le moment. Lance la capture d’abord."
                } else {
                    val export = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "text/csv"
                        putExtra(Intent.EXTRA_TITLE, DiagnosticsStore.FILE_NAME)
                    }
                    startActivityForResult(export, exportRequest)
                }
            }
        })
        root.addView(Button(this).apply {
            text = "Arrêter l’assistant"
            setOnClickListener {
                stopService(Intent(this@MainActivity, CaptureService::class.java))
                status.text = "Assistant arrêté."
            }
        })
        root.addView(label("Sélectionne le même actif et la même unité de temps que sur Pocket Option. La capture observe uniquement les pixels visibles ; elle ne lit pas le flux OTC interne. Tant que la lecture des bougies n’est pas validée, le résultat reste NO SIGNAL."))
        setContentView(root)
    }

    @Deprecated("Deprecated by Android but retained for compatibility with the screen-capture consent flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == exportRequest && resultCode == RESULT_OK && data?.data != null) {
            try {
                contentResolver.openOutputStream(data.data!!)?.use { output ->
                    openFileInput(DiagnosticsStore.FILE_NAME).use { input -> input.copyTo(output) }
                }
                status.text = "Diagnostic CSV exporté. Tu peux joindre ce fichier à notre conversation."
            } catch (error: Exception) {
                status.text = "Échec de l’export : ${error.message ?: "erreur inconnue"}"
            }
            return
        }
        if (requestCode == captureRequest && resultCode == RESULT_OK && data != null) {
            val service = Intent(this, CaptureService::class.java).apply {
                putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(CaptureService.EXTRA_DATA, data)
                putExtra(CaptureService.EXTRA_ASSET, assetSpinner.selectedItem.toString())
                putExtra(CaptureService.EXTRA_TIMEFRAME, timeframeSpinner.selectedItem.toString())
            }
            startForegroundService(service)
            status.text = "Capture démarrée : ${assetSpinner.selectedItem} • ${timeframeSpinner.selectedItem}\nAnalyse : NO SIGNAL (lecture en validation)."
        }
    }
}
