package com.astream

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var btnAction: Button
    private lateinit var etUrl: EditText
    private var isSharing = false
    private val startMediaProjection = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            startScreenService(result.data!!)
            updateUIState(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 200, 50, 50)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }
        etUrl = EditText(this).apply {
            hint = "ws://192.168.0.152:8080/publish"
            setText("ws://192.168.0.152:8080/publish")
        }
        btnAction = Button(this).apply {
            text = "Start Sharing"
            setOnClickListener { toggleShare() }
        }
        layout.addView(etUrl)
        layout.addView(btnAction)
        setContentView(layout)
    }

    private fun toggleShare() {
        if (!isSharing) {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startMediaProjection.launch(projectionManager.createScreenCaptureIntent())
        } else {
            val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
                action = "STOP"
            }
            startService(stopIntent)
            updateUIState(false)
        }
    }

    private fun startScreenService(data: Intent) {
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = "START"
            putExtra("RESULT_DATA", data)
            putExtra("URL", etUrl.text.toString())
        }
        startForegroundService(serviceIntent)
    }

    private fun updateUIState(sharing: Boolean) {
        isSharing = sharing
        btnAction.text = if (sharing) "Stop Sharing" else "Start Sharing"
        etUrl.isEnabled = !sharing
    }
}
