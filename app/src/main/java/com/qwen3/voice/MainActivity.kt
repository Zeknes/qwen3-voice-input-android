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
            startExtract()
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
            statusText.text = "✅ 语音模型已就绪"
            downloadButton.isEnabled = false
            downloadButton.text = "模型已就绪"
            progressBar.visibility = View.GONE
            progressText.visibility = View.GONE
        } else {
            statusText.text = "⏳ 首次使用需要释放模型文件（约 960MB）"
            downloadButton.isEnabled = true
            downloadButton.text = "释放模型"
            progressBar.visibility = View.GONE
            progressText.visibility = View.GONE
        }
    }

    private fun startExtract() {
        downloadButton.isEnabled = false
        downloadButton.text = "释放中..."
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        progressText.visibility = View.VISIBLE
        progressText.text = "正在从应用内释放模型..."

        ModelManager.extractFromAssets(
            context = this,
            onProgress = { progress ->
                runOnUiThread {
                    progressBar.progress = progress
                    progressText.text = "释放中: $progress%"
                }
            },
            onComplete = {
                runOnUiThread {
                    progressBar.progress = 100
                    progressText.text = "✅ 模型释放完成！"
                    updateStatus()
                }
            },
            onError = { error ->
                runOnUiThread {
                    statusText.text = "❌ 释放失败: $error"
                    downloadButton.isEnabled = true
                    downloadButton.text = "重试"
                    progressBar.visibility = View.GONE
                    progressText.text = "错误: $error"
                }
            }
        )
    }
}
