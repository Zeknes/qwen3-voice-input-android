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

        val qwen3Config = OfflineQwen3AsrModelConfig()
        qwen3Config.convFrontend = "$modelDir/conv_frontend.onnx"
        qwen3Config.encoder = "$modelDir/encoder.int8.onnx"
        qwen3Config.decoder = "$modelDir/decoder.int8.onnx"
        qwen3Config.tokenizer = "$modelDir/tokenizer"
        qwen3Config.maxTotalLen = 512
        qwen3Config.maxNewTokens = 128

        val modelConfig = OfflineModelConfig()
        modelConfig.qwen3Asr = qwen3Config
        modelConfig.tokens = ""
        modelConfig.numThreads = 4
        modelConfig.debug = false
        modelConfig.provider = "cpu"

        val config = OfflineRecognizerConfig()
        config.featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80)
        config.modelConfig = modelConfig
        config.decodingMethod = "greedy_search"

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
