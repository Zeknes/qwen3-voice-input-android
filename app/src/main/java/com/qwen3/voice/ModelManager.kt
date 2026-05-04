package com.qwen3.voice

import android.content.Context
import android.util.Log
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "ModelManager"
private const val MODEL_DIR_NAME = "sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25"
private const val ARCHIVE_NAME = "$MODEL_DIR_NAME.tar.bz2"

object ModelManager {

    const val MODEL_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$ARCHIVE_NAME"

    fun getModelDir(context: Context): String {
        return File(context.filesDir, "models/$MODEL_DIR_NAME").absolutePath
    }

    fun isModelReady(context: Context): Boolean {
        val dir = File(getModelDir(context))
        if (!dir.exists() || !dir.isDirectory) return false

        // Check that key model files exist
        val requiredFiles = listOf(
            "encoder.onnx",
            "decoder.onnx",
            "tokens.txt",
        )
        return requiredFiles.all { File(dir, it).exists() }
    }

    fun downloadModel(
        context: Context,
        onProgress: (Int) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        Thread {
            try {
                val modelsDir = File(context.filesDir, "models")
                modelsDir.mkdirs()

                val archiveFile = File(modelsDir, ARCHIVE_NAME)
                val targetDir = File(modelsDir, MODEL_DIR_NAME)

                // Download
                Log.d(TAG, "Downloading from $MODEL_URL")
                downloadFile(MODEL_URL, archiveFile) { bytesDownloaded, totalBytes ->
                    if (totalBytes > 0) {
                        val progress = ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 99)
                        onProgress(progress)
                    }
                }
                onProgress(99)
                Log.d(TAG, "Download complete, extracting...")

                // Extract tar.bz2
                extractTarBz2(archiveFile, modelsDir)

                // Clean up archive
                archiveFile.delete()
                Log.d(TAG, "Extraction complete, model at: $targetDir")

                onProgress(100)
                onComplete()
            } catch (e: Exception) {
                Log.e(TAG, "Model download/extraction failed", e)
                onError(e.message ?: "Unknown error")
            }
        }.start()
    }

    private fun downloadFile(
        urlString: String,
        outputFile: File,
        onProgress: (Long, Long) -> Unit
    ) {
        var url = URL(urlString)
        var connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 30_000
        connection.readTimeout = 30_000

        // Follow redirects manually for GitHub
        var redirectCount = 0
        while (redirectCount < 10) {
            val code = connection.responseCode
            if (code in 301..308) {
                val location = connection.getHeaderField("Location") ?: break
                connection.disconnect()
                url = URL(location)
                connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 30_000
                connection.readTimeout = 30_000
                redirectCount++
            } else {
                break
            }
        }

        val totalBytes = connection.contentLength.toLong()
        var bytesDownloaded = 0L

        connection.inputStream.use { input ->
            BufferedInputStream(input).use { buffered ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (buffered.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        onProgress(bytesDownloaded, totalBytes)
                    }
                }
            }
        }
        connection.disconnect()
    }

    private fun extractTarBz2(archiveFile: File, targetDir: File) {
        val parentDir = targetDir.parentFile ?: archiveFile.parentFile!!

        BZip2CompressorInputStream(BufferedInputStream(archiveFile.inputStream()), false).use { bzip2Stream ->
            TarArchiveInputStream(bzip2Stream, 10240).use { tarStream ->
                var entry: TarArchiveEntry? = tarStream.nextTarEntry
                while (entry != null) {
                    val name = entry.name
                    val outFile = File(parentDir, name)

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            tarStream.copyTo(fos)
                        }
                    }
                    entry = tarStream.nextTarEntry
                }
            }
        }
    }
}
