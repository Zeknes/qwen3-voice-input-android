package com.qwen3.voice

import android.content.Context
import android.util.Log
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

private const val TAG = "ModelManager"
private const val MODEL_DIR_NAME = "sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25"
private const val ASSETS_MODEL_DIR = "models/$MODEL_DIR_NAME"

object ModelManager {

    fun getModelDir(context: Context): String {
        return File(context.filesDir, "models/$MODEL_DIR_NAME").absolutePath
    }

    fun isModelReady(context: Context): Boolean {
        val dir = File(getModelDir(context))
        if (!dir.exists() || !dir.isDirectory) return false

        val requiredFiles = listOf(
            "conv_frontend.onnx",
            "encoder.int8.onnx",
            "decoder.int8.onnx",
            File("tokenizer", "vocab.json").path,
        )
        return requiredFiles.all { File(dir, it).exists() }
    }

    /**
     * Extract model files from APK assets to internal storage.
     * Only copies files that don't already exist (first-launch extraction).
     */
    fun extractFromAssets(
        context: Context,
        onProgress: (Int) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        Thread {
            try {
                val targetDir = File(context.filesDir, "models/$MODEL_DIR_NAME")
                targetDir.mkdirs()

                val assetManager = context.assets
                val allFiles = mutableListOf<String>()
                collectAssetFiles(assetManager, ASSETS_MODEL_DIR, allFiles)

                Log.d(TAG, "Extracting ${allFiles.size} model files from assets...")

                for ((index, assetPath) in allFiles.withIndex()) {
                    val relativePath = assetPath.removePrefix("$ASSETS_MODEL_DIR/")
                    val outFile = File(targetDir, relativePath)

                    if (outFile.exists()) {
                        Log.d(TAG, "Skip (exists): $relativePath")
                    } else {
                        outFile.parentFile?.mkdirs()
                        assetManager.open(assetPath).use { input ->
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output, bufferSize = 131072)
                            }
                        }
                        Log.d(TAG, "Extracted: $relativePath (${outFile.length()} bytes)")
                    }

                    val progress = ((index + 1) * 100 / allFiles.size)
                    onProgress(progress)
                }

                Log.d(TAG, "All model files extracted to: ${targetDir.absolutePath}")
                onProgress(100)
                onComplete()
            } catch (e: Exception) {
                Log.e(TAG, "Asset extraction failed", e)
                onError(e.message ?: "Unknown error")
            }
        }.start()
    }

    private fun collectAssetFiles(assetManager: android.content.res.AssetManager, path: String, result: MutableList<String>) {
        val entries = assetManager.list(path) ?: return
        if (entries.isEmpty()) {
            // It's a file
            result.add(path)
        } else {
            // It's a directory
            for (entry in entries) {
                collectAssetFiles(assetManager, "$path/$entry", result)
            }
        }
    }
}
