package com.then.truyenaudio.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

enum class TtsStatus {
    INITIALIZING,
    READY,
    ERROR,
    RELEASED
}

class TtsManager(
    context: Context
) : TextToSpeech.OnInitListener {

    private val applicationContext = context.applicationContext
    private val _status = MutableStateFlow(TtsStatus.INITIALIZING)
    private var tts: TextToSpeech? = null
    private var currentCompletion: (() -> Unit)? = null
    private var currentError: ((String) -> Unit)? = null
    private var pendingText: String? = null
    private var pendingCompletion: (() -> Unit)? = null
    private var pendingError: ((String) -> Unit)? = null
    private var chunkCount = 0
    private var completedChunks = 0
    private var released = false

    val status: StateFlow<TtsStatus> = _status.asStateFlow()

    init {
        Log.d(TAG, "Creating TextToSpeech manager")
        tts = TextToSpeech(applicationContext, this)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                Log.d(TAG, "TTS started utterance=$utteranceId")
            }

            override fun onDone(utteranceId: String) {
                if (utteranceId.startsWith(CHUNK_PREFIX)) {
                    completedChunks++
                    Log.d(TAG, "TTS completed chunk $completedChunks/$chunkCount")
                    if (completedChunks >= chunkCount) {
                        currentCompletion?.invoke()
                        clearCallbacks()
                    }
                }
            }

            @Deprecated("Required by the Android TextToSpeech API")
            override fun onError(utteranceId: String) {
                handleUtteranceError("TextToSpeech engine error for utterance=$utteranceId")
            }

            override fun onError(utteranceId: String, errorCode: Int) {
                handleUtteranceError(
                    "TextToSpeech engine error code=$errorCode for utterance=$utteranceId"
                )
            }
        })
    }

    override fun onInit(status: Int) {
        Log.d(TAG, "TextToSpeech onInit status=$status")
        if (released) {
            return
        }
        if (status != TextToSpeech.SUCCESS) {
            setError("Không thể khởi tạo bộ đọc văn bản trên thiết bị.")
            return
        }

        val engine = tts ?: run {
            setError("Bộ đọc văn bản không khả dụng.")
            return
        }
        val languageStatus = engine.setLanguage(VIETNAMESE_LOCALE)
        Log.d(TAG, "Vietnamese language status=$languageStatus")
        if (languageStatus == TextToSpeech.LANG_MISSING_DATA ||
            languageStatus == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            setError("Thiết bị chưa hỗ trợ hoặc chưa cài dữ liệu giọng tiếng Việt.")
            return
        }

        engine.setSpeechRate(1.0f)
        engine.setPitch(1.0f)
        _status.value = TtsStatus.READY
        Log.d(TAG, "TextToSpeech is ready")
        flushPendingSpeak()
    }

    fun speak(
        text: String,
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    ): Boolean {
        val normalizedText = text.trim()
        Log.d(TAG, "speak requested length=${normalizedText.length}, status=${_status.value}")
        if (normalizedText.isBlank()) {
            val message = "Nội dung chương đang trống, không thể đọc."
            Log.e(TAG, message)
            onError(message)
            return false
        }
        if (_status.value == TtsStatus.INITIALIZING) {
            pendingText = normalizedText
            pendingCompletion = onComplete
            pendingError = onError
            Log.d(TAG, "TTS is initializing; speak request queued")
            return true
        }
        if (_status.value != TtsStatus.READY || tts == null) {
            val message = "Bộ đọc văn bản chưa sẵn sàng."
            Log.e(TAG, message)
            onError(message)
            return false
        }
        return speakReady(normalizedText, onComplete, onError)
    }

    fun stop() {
        Log.d(TAG, "Stopping TTS")
        tts?.stop()
        clearCallbacks()
    }

    fun setSpeed(speed: Float) {
        tts?.setSpeechRate(speed)
    }

    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch)
    }

    fun getVietnameseVoices() =
        tts?.voices
            ?.filter { it.locale.language == VIETNAMESE_LOCALE.language }
            .orEmpty()

    fun setVoice(voice: android.speech.tts.Voice) {
        if (_status.value == TtsStatus.READY) {
            tts?.voice = voice
            Log.d(TAG, "Selected voice=${voice.name}")
        }
    }

    fun setVoiceByName(name: String?) {
        if (_status.value != TtsStatus.READY || name.isNullOrBlank()) {
            return
        }
        val voice = tts?.voices?.firstOrNull { it.name == name }
        if (voice != null) {
            setVoice(voice)
        } else {
            Log.w(TAG, "Saved voice is unavailable: $name")
        }
    }

    fun release() {
        if (released) {
            return
        }
        released = true
        Log.d(TAG, "Releasing TextToSpeech")
        clearCallbacks()
        tts?.stop()
        tts?.shutdown()
        tts = null
        _status.value = TtsStatus.RELEASED
    }

    private fun speakReady(
        text: String,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        val engine = tts ?: return false
        val chunks = splitIntoChunks(text)
        chunkCount = chunks.size
        completedChunks = 0
        currentCompletion = onComplete
        currentError = onError
        Log.d(TAG, "Speaking ${chunks.size} chunk(s), totalLength=${text.length}")

        chunks.forEachIndexed { index, chunk ->
            val utteranceId = "$CHUNK_PREFIX${UUID.randomUUID()}-$index"
            val queueMode = if (index == 0) {
                TextToSpeech.QUEUE_FLUSH
            } else {
                TextToSpeech.QUEUE_ADD
            }
            val result = engine.speak(chunk, queueMode, null, utteranceId)
            Log.d(TAG, "speak chunk=${index + 1}/${chunks.size}, length=${chunk.length}, result=$result")
            if (result == TextToSpeech.ERROR) {
                handleUtteranceError("Không thể đưa nội dung vào hàng đợi đọc.")
                return false
            }
        }
        return true
    }

    private fun splitIntoChunks(text: String): List<String> {
        val chunks = mutableListOf<String>()
        var remaining = text.trim()
        while (remaining.length > MAX_CHUNK_LENGTH) {
            var splitAt = remaining.lastIndexOfAny(SPLIT_CHARACTERS, MAX_CHUNK_LENGTH)
            if (splitAt < MIN_CHUNK_LENGTH) {
                splitAt = remaining.lastIndexOf(' ', MAX_CHUNK_LENGTH)
            }
            if (splitAt < MIN_CHUNK_LENGTH) {
                splitAt = MAX_CHUNK_LENGTH
            }
            chunks += remaining.substring(0, splitAt).trim()
            remaining = remaining.substring(splitAt).trim()
        }
        if (remaining.isNotBlank()) {
            chunks += remaining
        }
        return chunks
    }

    private fun flushPendingSpeak() {
        val text = pendingText ?: return
        val completion = pendingCompletion ?: {}
        val error = pendingError ?: {}
        pendingText = null
        pendingCompletion = null
        pendingError = null
        speakReady(text, completion, error)
    }

    private fun setError(message: String) {
        Log.e(TAG, message)
        _status.value = TtsStatus.ERROR
        pendingError?.invoke(message)
        pendingText = null
        pendingCompletion = null
        pendingError = null
    }

    private fun handleUtteranceError(message: String) {
        Log.e(TAG, message)
        currentError?.invoke(message)
        clearCallbacks()
    }

    private fun clearCallbacks() {
        currentCompletion = null
        currentError = null
        pendingText = null
        pendingCompletion = null
        pendingError = null
        chunkCount = 0
        completedChunks = 0
    }

    private companion object {
        const val TAG = "TruyenAudioTTS"
        const val CHUNK_PREFIX = "chapter-"
        const val MAX_CHUNK_LENGTH = 3000
        const val MIN_CHUNK_LENGTH = 500
        val SPLIT_CHARACTERS = charArrayOf('.', '!', '?', '。', '！', '？', '\n')
        val VIETNAMESE_LOCALE: Locale = Locale.forLanguageTag("vi-VN")
    }
}
