package com.qwen3.voice

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var downloadButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var settingsButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.tv_status)
        downloadButton = findViewById(R.id.btn_download)
        progressBar = findViewById(R.id.progress_bar)
        progressText = findViewById(R.id.tv_progress)
        settingsButton = findViewById(R.id.btn_settings)

        updateStatus()

        downloadButton.setOnClickListener {
            startDownload()
        }

        settingsButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val modelReady = ModelManager.isModelReady(this)
        if (modelReady) {
            statusText.text = "✅ Speech model downloaded and ready"
            downloadButton.isEnabled = false
            downloadButton.text = "Model Downloaded"
            progressBar.visibility = View.GONE
            progressText.visibility = View.GONE
        } else {
            statusText.text = "⚠️ Speech model needs to be downloaded (~500MB)"
            downloadButton.isEnabled = true
            downloadButton.text = "Download Model"
            progressBar.visibility = View.GONE
            progressText.visibility = View.GONE
        }
    }

    private fun startDownload() {
        downloadButton.isEnabled = false
        downloadButton.text = "Downloading..."
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        progressText.visibility = View.VISIBLE
        progressText.text = "Starting download..."

        ModelManager.downloadModel(
            context = this,
            onProgress = { progress ->
                runOnUiThread {
                    progressBar.progress = progress
                    progressText.text = "Downloading: $progress%"
                }
            },
            onComplete = {
                runOnUiThread {
                    progressBar.progress = 100
                    progressText.text = "Download complete!"
                    updateStatus()
                }
            },
            onError = { error ->
                runOnUiThread {
                    statusText.text = "❌ Download failed: $error"
                    downloadButton.isEnabled = true
                    downloadButton.text = "Retry Download"
                    progressBar.visibility = View.GONE
                    progressText.text = "Error: $error"
                }
            }
        )
    }
}
