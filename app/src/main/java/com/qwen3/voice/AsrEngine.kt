package com.qwen3.voice

import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineQwen3AsrModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig

private const val TAG = "AsrEngine"

class AsrEngine(private val modelDir: String) {

    private var recognizer: OfflineRecognizer? = null

    @Volatile
    var isInitialized: Boolean = false
        private set

    fun initialize() {
        Log.d(TAG, "Initializing with modelDir=$modelDir")

        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(
                sampleRate = 16000,
                featureDim = 80,
            ),
            modelConfig = OfflineModelConfig(
                qwen3Asr = OfflineQwen3AsrModelConfig(
                    convFrontend = "$modelDir/conv-frontend.onnx",
                    encoder = "$modelDir/encoder.onnx",
                    decoder = "$modelDir/decoder.onnx",
                    tokenizer = "$modelDir/tokens.txt",
                    maxNewTokens = 512,
                ),
                tokens = "$modelDir/tokens.txt",
                numThreads = 4,
                debug = false,
                provider = "cpu",
            ),
            decodingMethod = "greedy_search",
        )

        recognizer = OfflineRecognizer(config = config)
        isInitialized = true
        Log.d(TAG, "Initialization complete")
    }

    fun transcribe(audioData: FloatArray): String {
        val rec = recognizer ?: throw IllegalStateException("AsrEngine not initialized")

        val stream = rec.createStream()
        stream.acceptWaveform(audioData, sampleRate = 16000)
        rec.decode(stream)

        val result = rec.getResult(stream)
        stream.release()

        Log.d(TAG, "Transcription result: '${result.text}'")
        return result.text
    }

    fun release() {
        Log.d(TAG, "Releasing resources")
        recognizer?.release()
        recognizer = null
        isInitialized = false
    }
}
