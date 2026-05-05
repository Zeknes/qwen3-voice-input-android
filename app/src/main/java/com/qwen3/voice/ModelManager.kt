package com.qwen3.voice

import android.content.Context
import android.util.Log
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "ModelManager"
private const val MODEL_DIR_NAME = "sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25"
private const val ARCHIVE_NAME = "$MODEL_DIR_NAME.tar.bz2"

object ModelManager {

    const val MODEL_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$ARCHIVE_NAME"

    // Total download size (approximate, for progress display)
    private const val EXPECTED_SIZE = 878_702_423L  // ~878MB

    fun getModelDir(context: Context): String {
        return File(context.filesDir, "models/$MODEL_DIR_NAME").absolutePath
    }

    fun isModelReady(context: Context): Boolean {
        val dir = File(getModelDir(context))
        if (!dir.exists() || !dir.isDirectory) return false

        // Check actual model files from the release
        val requiredFiles = listOf(
            "conv_frontend.onnx",
            "encoder.int8.onnx",
            "decoder.int8.onnx",
            File("tokenizer", "vocab.json").path,  // tokenizer/vocab.json
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

                val tempFile = File(modelsDir, "$ARCHIVE_NAME.part")

                // Resume download if partial file exists
                val existingBytes = if (tempFile.exists()) tempFile.length() else 0L

                Log.d(TAG, "Downloading from $MODEL_URL (resume from $existingBytes bytes)")
                downloadFile(MODEL_URL, tempFile, existingBytes) { bytesDownloaded, totalBytes ->
                    val total = if (totalBytes > 0) totalBytes else EXPECTED_SIZE
                    val progress = ((bytesDownloaded * 100) / total).toInt().coerceIn(0, 99)
                    onProgress(progress)
                }

                onProgress(99)
                Log.d(TAG, "Download complete (${tempFile.length()} bytes), extracting...")

                // Extract tar.bz2
                val archiveFile = File(modelsDir, ARCHIVE_NAME)
                tempFile.renameTo(archiveFile)
                extractTarBz2(archiveFile, modelsDir)

                // Clean up archive
                archiveFile.delete()
                Log.d(TAG, "Extraction complete, model at: ${getModelDir(context)}")

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
        resumeFrom: Long,
        onProgress: (Long, Long) -> Unit
    ) {
        var url = URL(urlString)
        var connection = openConnection(url, resumeFrom)

        // Follow redirects manually (GitHub uses 302 to CDN)
        var redirectCount = 0
        while (redirectCount < 15) {
            val code = connection.responseCode
            if (code in 301..308) {
                val location = connection.getHeaderField("Location") ?: break
                connection.disconnect()
                url = URL(location)
                connection = openConnection(url, resumeFrom)
                redirectCount++
            } else if (code == 416) {
                // Range not satisfiable — file already complete
                Log.d(TAG, "File already fully downloaded")
                connection.disconnect()
                return
            } else if (code != 200 && code != 206) {
                connection.disconnect()
                throw RuntimeException("HTTP $code from $url")
            } else {
                break
            }
        }

        val contentLen = connection.contentLength.toLong()
        val totalBytes = if (contentLen > 0) contentLen + resumeFrom else -1L

        var bytesDownloaded = resumeFrom

        RandomAccessFile(outputFile, "rw").use { raf ->
            if (resumeFrom > 0) {
                raf.seek(resumeFrom)
            }

            connection.inputStream.use { input ->
                BufferedInputStream(input, 131072).use { buffered ->
                    val buffer = ByteArray(131072)
                    var bytesRead: Int
                    var lastProgressTime = System.currentTimeMillis()

                    while (buffered.read(buffer).also { bytesRead = it } != -1) {
                        raf.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead

                        // Report progress max once per 500ms
                        val now = System.currentTimeMillis()
                        if (now - lastProgressTime > 500) {
                            onProgress(bytesDownloaded, totalBytes)
                            lastProgressTime = now
                        }
                    }
                }
            }
        }
        connection.disconnect()
        onProgress(bytesDownloaded, totalBytes)
        Log.d(TAG, "Downloaded $bytesDownloaded bytes total")
    }

    private fun openConnection(url: URL, resumeFrom: Long): HttpURLConnection {
        val conn = url.openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false  // We handle redirects manually
        conn.connectTimeout = 60_000          // 60s connect timeout
        conn.readTimeout = 300_000            // 5 min read timeout (large file)
        conn.setRequestProperty("User-Agent", "Qwen3VoiceInput/1.0")
        if (resumeFrom > 0) {
            conn.setRequestProperty("Range", "bytes=$resumeFrom-")
            Log.d(TAG, "Resuming from byte $resumeFrom")
        }
        return conn
    }

    private fun extractTarBz2(archiveFile: File, targetDir: File) {
        val parentDir = targetDir.parentFile ?: archiveFile.parentFile!!

        BZip2CompressorInputStream(BufferedInputStream(archiveFile.inputStream(), 65536), false).use { bzip2Stream ->
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
                    Log.d(TAG, "Extracted: $name")
                    entry = tarStream.nextTarEntry
                }
            }
        }
    }
}
