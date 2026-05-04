package com.qwen3.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.inputmethodservice.InputMethodService

private const val TAG = "Qwen3VoiceIME"
private const val SAMPLE_RATE = 16000
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

class Qwen3VoiceIME : InputMethodService() {

    private var asrEngine: AsrEngine? = null
    private var audioRecord: AudioRecord? = null
    @Volatile
    private var isRecording = false
    private var recordingThread: Thread? = null
    private val audioBuffer = mutableListOf<Float>()
    private val audioBufferLock = Object()

    private var statusText: TextView? = null
    private var micButton: ImageButton? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var engineReady = false

    override fun onCreate() {
        super.onCreate()
        initAsrEngine()
    }

    private fun initAsrEngine() {
        Thread {
            try {
                if (!ModelManager.isModelReady(this)) {
                    Log.w(TAG, "Model not ready, ASR engine will not be initialized")
                    updateStatus(getString(R.string.error_model))
                    return@Thread
                }

                val modelDir = ModelManager.getModelDir(this)
                Log.d(TAG, "Initializing ASR engine with modelDir=$modelDir")
                val engine = AsrEngine(modelDir)
                engine.initialize()
                asrEngine = engine
                engineReady = true
                Log.d(TAG, "ASR engine ready")
                updateStatus(getString(R.string.tap_to_speak))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize ASR engine", e)
                updateStatus("Model init failed: ${e.message}")
            }
        }.start()
    }

    private fun updateStatus(text: String) {
        mainHandler.post {
            statusText?.text = text
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.voice_keyboard, null)
        statusText = view.findViewById(R.id.status_text)
        micButton = view.findViewById(R.id.mic_button)

        if (!engineReady) {
            statusText?.text = getString(R.string.processing)
        }

        micButton?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (engineReady) {
                        startRecording()
                    } else {
                        statusText?.text = getString(R.string.error_model)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isRecording) {
                        stopRecording()
                    }
                    true
                }
                else -> false
            }
        }

        return view
    }

    private fun startRecording() {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size: $bufferSize")
            updateStatus(getString(R.string.error_audio))
            return
        }

        try {
            @SuppressLint("MissingPermission")
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                updateStatus(getString(R.string.error_audio))
                recorder.release()
                return
            }

            audioRecord = recorder
            synchronized(audioBufferLock) {
                audioBuffer.clear()
            }
            isRecording = true

            recorder.startRecording()
            updateStatus(getString(R.string.listening))
            Log.d(TAG, "Recording started")

            // Read audio data in background thread
            recordingThread = Thread {
                val readBuffer = ShortArray(bufferSize / 2)
                while (isRecording) {
                    val shortsRead = recorder.read(readBuffer, 0, readBuffer.size)
                    if (shortsRead > 0) {
                        synchronized(audioBufferLock) {
                            for (i in 0 until shortsRead) {
                                audioBuffer.add(readBuffer[i].toFloat() / 32768.0f)
                            }
                        }
                    }
                }
            }.apply { start() }

        } catch (e: SecurityException) {
            Log.e(TAG, "AudioRecord permission denied", e)
            updateStatus("Microphone permission required")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            updateStatus(getString(R.string.error_audio))
        }
    }

    private fun stopRecording() {
        if (!isRecording) return

        isRecording = false
        recordingThread?.join(2000)
        recordingThread = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null

        // Get recorded audio data
        val audioData: FloatArray
        synchronized(audioBufferLock) {
            audioData = audioBuffer.toFloatArray()
            audioBuffer.clear()
        }

        if (audioData.isEmpty()) {
            updateStatus(getString(R.string.tap_to_speak))
            return
        }

        // Transcribe
        updateStatus(getString(R.string.processing))
        Log.d(TAG, "Audio recorded: ${audioData.size} samples, transcribing...")

        Thread {
            try {
                val engine = asrEngine ?: throw IllegalStateException("ASR engine not available")
                val text = engine.transcribe(audioData)
                Log.d(TAG, "Transcription: '$text'")

                mainHandler.post {
                    if (text.isNotBlank()) {
                        currentInputConnection?.commitText(text, 1)
                        statusText?.text = text
                    } else {
                        statusText?.text = getString(R.string.tap_to_speak)
                    }
                }

                // Reset status after a delay
                mainHandler.postDelayed({
                    statusText?.text = getString(R.string.tap_to_speak)
                }, 2000)

            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                updateStatus("Transcription error: ${e.message}")
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord", e)
        }
        audioRecord = null

        asrEngine?.release()
        asrEngine = null
        Log.d(TAG, "IME destroyed, resources released")
    }
}
