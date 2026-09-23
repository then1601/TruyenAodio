package com.then.truyenaudio.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.then.truyenaudio.MainActivity
import com.then.truyenaudio.R
import com.then.truyenaudio.data.remote.WebNovelParser
import com.then.truyenaudio.tts.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

class PlaybackService : Service() {
    private lateinit var tts: TtsManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val parser = WebNovelParser()
    private val preferences by lazy {
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
    }
    private val sleepTimerHandler = Handler(Looper.getMainLooper())
    private var currentSegments: List<String> = emptyList()
    private var sleepTimerRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        restoreSleepTimer()
        tts = TtsManager(this)
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> playPendingChapter()
            ACTION_PAUSE -> pausePlayback()
            ACTION_RESUME -> resumePlayback()
            ACTION_NEXT -> moveTo(1)
            ACTION_PREVIOUS -> moveTo(-1)
            ACTION_SEEK_BACKWARD -> moveTo(-1)
            ACTION_SEEK_FORWARD -> moveTo(1)
            ACTION_STOP -> stopPlayback()
            ACTION_SET_SPEED -> setSpeed(intent.getFloatExtra(EXTRA_SPEED, DEFAULT_SPEED))
            ACTION_SET_SLEEP_TIMER -> setSleepTimer(intent.getIntExtra(EXTRA_SLEEP_MINUTES, 0))
            ACTION_CLEAR_SLEEP_TIMER -> clearSleepTimer()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        clearSleepTimer()
        sleepTimerHandler.removeCallbacksAndMessages(null)
        serviceScope.cancel()
        tts.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun playPendingChapter() {
        val chapter = PlaybackController.pendingChapter ?: return
        val chapters = PlaybackController.pendingQueue
        currentSegments = splitIntoSegments(chapter.content)
        if (currentSegments.isEmpty()) {
            PlaybackController.updateState(
                chapter = chapter,
                chapters = chapters,
                isPlaying = false,
                isLoading = false,
                error = "Nội dung chương đang trống."
            )
            updateNotification()
            return
        }
        val savedChapter = preferences.getInt(KEY_CHAPTER_NUMBER, Int.MIN_VALUE)
        val savedSegment = preferences.getInt(KEY_SEGMENT_INDEX, 0)
        val startIndex = if (savedChapter == chapter.chapterNumber) {
            savedSegment.coerceIn(currentSegments.indices)
        } else {
            0
        }
        val speed = PlaybackController.pendingSpeed
        PlaybackController.updateState(
            chapter = chapter,
            chapters = chapters,
            isPlaying = true,
            isLoading = false,
            error = null,
            speed = speed,
            pitch = PlaybackController.pendingPitch,
            segmentIndex = startIndex,
            segmentCount = currentSegments.size
        )
        tts.setVoiceByName(preferences.getString(KEY_VOICE_NAME, null))
        startSegment(startIndex)
    }

    private fun startSegment(index: Int) {
        val chapter = PlaybackController.state.value.chapter ?: return
        if (currentSegments.isEmpty() || index !in currentSegments.indices) return

        preferences.edit()
            .putInt(KEY_SEGMENT_INDEX, index)
            .putInt(KEY_CHAPTER_NUMBER, chapter.chapterNumber)
            .putFloat(KEY_SPEED, PlaybackController.state.value.speed)
            .apply()

        PlaybackController.updateState(
            chapter = chapter,
            chapters = PlaybackController.state.value.chapters,
            isPlaying = true,
            isLoading = false,
            segmentIndex = index,
            segmentCount = currentSegments.size
        )
        tts.stop()
        tts.setVoiceByName(preferences.getString(KEY_VOICE_NAME, null))
        tts.setSpeed(PlaybackController.state.value.speed)
        tts.speak(
            currentSegments[index],
            onComplete = {
                val nextIndex = index + 1
                if (nextIndex < currentSegments.size) {
                    startSegment(nextIndex)
                } else {
                    val state = PlaybackController.state.value
                    if (state.autoNext && state.hasNext) {
                        moveTo(1)
                    } else {
                        PlaybackController.updateState(isPlaying = false)
                        updateNotification()
                    }
                }
            },
            onError = { message ->
                PlaybackController.updateState(isPlaying = false, isLoading = false, error = message)
                updateNotification()
            }
        )
        updateNotification()
    }

    private fun pausePlayback() {
        tts.stop()
        PlaybackController.updateState(isPlaying = false, isLoading = false)
        updateNotification()
    }

    private fun resumePlayback() {
        val state = PlaybackController.state.value
        val chapter = state.chapter ?: return
        PlaybackController.pendingChapter = chapter
        PlaybackController.pendingQueue = state.chapters
        PlaybackController.pendingSpeed = state.speed
        PlaybackController.pendingPitch = state.pitch
        playPendingChapter()
    }

    private fun moveTo(offset: Int) {
        val state = PlaybackController.state.value
        val targetSegment = state.segmentIndex + offset
        if (targetSegment in currentSegments.indices) {
            startSegment(targetSegment)
            return
        }

        val targetChapter = state.chapters.getOrNull(state.currentIndex + offset) ?: return
        tts.stop()
        PlaybackController.updateState(
            chapter = targetChapter,
            chapters = state.chapters,
            isPlaying = true,
            isLoading = true,
            error = null,
            segmentIndex = 0,
            segmentCount = 0
        )
        updateNotification()
        serviceScope.launch {
            runCatching { parser.parseChapter(targetChapter) }
                .onSuccess { loadedChapter ->
                    PlaybackController.pendingChapter = loadedChapter
                    PlaybackController.pendingQueue = state.chapters
                    PlaybackController.pendingSpeed = state.speed
                    PlaybackController.pendingPitch = state.pitch
                    playPendingChapter()
                }
                .onFailure { error ->
                    PlaybackController.updateState(
                        isPlaying = false,
                        isLoading = false,
                        error = error.message ?: "Không thể tải chương tiếp theo."
                    )
                    updateNotification()
                }
        }
    }

    private fun setSpeed(requestedSpeed: Float) {
        val safeSpeed = SUPPORTED_SPEEDS.minByOrNull { abs(it - requestedSpeed) } ?: DEFAULT_SPEED
        preferences.edit().putFloat(KEY_SPEED, safeSpeed).apply()
        PlaybackController.updateState(speed = safeSpeed)
        if (PlaybackController.state.value.isPlaying) {
            startSegment(PlaybackController.state.value.segmentIndex)
        } else {
            tts.setSpeed(safeSpeed)
            updateNotification()
        }
    }

    private fun stopPlayback() {
        clearSleepTimer()
        tts.stop()
        PlaybackController.updateState(isPlaying = false, isLoading = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun setSleepTimer(minutes: Int) {
        if (minutes <= 0) {
            clearSleepTimer()
            return
        }
        val durationMs = minutes.toLong() * 60_000L
        val deadline = SystemClock.elapsedRealtime() + durationMs
        preferences.edit()
            .putInt(KEY_SLEEP_TIMER_MINUTES, minutes)
            .putLong(KEY_SLEEP_TIMER_DEADLINE, deadline)
            .apply()
        PlaybackController.updateSleepTimer(minutes, durationMs)

        val runnable = object : Runnable {
            override fun run() {
                val remainingMs = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                if (remainingMs > 0L) {
                    PlaybackController.updateSleepTimer(minutes, remainingMs)
                    sleepTimerHandler.postDelayed(this, 1_000L)
                    return
                }
                sleepTimerRunnable = null
                stopPlayback()
            }
        }
        sleepTimerHandler.removeCallbacksAndMessages(null)
        sleepTimerRunnable = runnable
        sleepTimerHandler.postDelayed(runnable, 1_000L)
    }

    private fun clearSleepTimer() {
        sleepTimerHandler.removeCallbacksAndMessages(null)
        sleepTimerRunnable = null
        preferences.edit()
            .remove(KEY_SLEEP_TIMER_MINUTES)
            .remove(KEY_SLEEP_TIMER_DEADLINE)
            .apply()
        PlaybackController.updateSleepTimer(0, 0L)
    }

    private fun restoreSleepTimer() {
        val savedMinutes = preferences.getInt(KEY_SLEEP_TIMER_MINUTES, 0)
        val deadline = preferences.getLong(KEY_SLEEP_TIMER_DEADLINE, 0L)
        val remainingMs = if (savedMinutes > 0 && deadline > 0L) {
            (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        } else {
            0L
        }
        if (savedMinutes <= 0 || remainingMs <= 0L) {
            preferences.edit().remove(KEY_SLEEP_TIMER_MINUTES).remove(KEY_SLEEP_TIMER_DEADLINE).apply()
            PlaybackController.updateSleepTimer(0, 0L)
            return
        }
        PlaybackController.updateSleepTimer(savedMinutes, remainingMs)
        val runnable = object : Runnable {
            override fun run() {
                val timeLeft = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                if (timeLeft > 0L) {
                    PlaybackController.updateSleepTimer(savedMinutes, timeLeft)
                    sleepTimerHandler.postDelayed(this, 1_000L)
                    return
                }
                sleepTimerRunnable = null
                stopPlayback()
            }
        }
        sleepTimerRunnable = runnable
        sleepTimerHandler.postDelayed(runnable, 1_000L)
    }

    private fun splitIntoSegments(text: String): List<String> {
        val result = mutableListOf<String>()
        var remaining = text.trim()
        while (remaining.length > MAX_SEGMENT_LENGTH) {
            var splitAt = remaining.lastIndexOfAny(SPLIT_CHARACTERS, MAX_SEGMENT_LENGTH)
            if (splitAt < MIN_SEGMENT_LENGTH) {
                splitAt = remaining.lastIndexOf(' ', MAX_SEGMENT_LENGTH)
            }
            if (splitAt < MIN_SEGMENT_LENGTH) {
                splitAt = MAX_SEGMENT_LENGTH
            }
            result += remaining.substring(0, splitAt).trim()
            remaining = remaining.substring(splitAt).trim()
        }
        if (remaining.isNotBlank()) {
            result += remaining
        }
        return result
    }

    private fun updateNotification() {
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val state = PlaybackController.state.value
        val chapter = state.chapter
        val text = when {
            state.error != null -> state.error
            state.isLoading -> "Đang chuẩn bị phát"
            state.isPlaying -> "Đang phát • Đoạn ${state.segmentIndex + 1}/${state.segmentCount}"
            else -> "Đã tạm dừng • Đoạn ${state.segmentIndex + 1}/${state.segmentCount}"
        }
        val openAppIntent = PendingIntent.getActivity(
            this,
            REQUEST_OPEN_APP,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(chapter?.title ?: "TruyenAudio")
            .setContentText(text)
            .setContentIntent(openAppIntent)
            .setOngoing(state.isPlaying || state.isLoading)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                R.mipmap.ic_launcher,
                "Lùi đoạn",
                actionIntent(ACTION_SEEK_BACKWARD, REQUEST_SEEK_BACKWARD)
            )
            .addAction(
                R.mipmap.ic_launcher,
                if (state.isPlaying) "Tạm dừng" else "Phát",
                actionIntent(if (state.isPlaying) ACTION_PAUSE else ACTION_RESUME, REQUEST_PLAY_PAUSE)
            )
            .addAction(
                R.mipmap.ic_launcher,
                "Tới đoạn",
                actionIntent(ACTION_SEEK_FORWARD, REQUEST_SEEK_FORWARD)
            )
            .build()
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TruyenAudio playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Điều khiển đọc truyện bằng giọng nói"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "truyenaudio_playback"
        const val NOTIFICATION_ID = 1001
        const val REQUEST_OPEN_APP = 2001
        const val REQUEST_PREVIOUS = 2002
        const val REQUEST_PLAY_PAUSE = 2003
        const val REQUEST_NEXT = 2004
        const val REQUEST_SEEK_BACKWARD = 2005
        const val REQUEST_SEEK_FORWARD = 2006
        const val ACTION_PLAY = "com.then.truyenaudio.action.PLAY"
        const val ACTION_PAUSE = "com.then.truyenaudio.action.PAUSE"
        const val ACTION_RESUME = "com.then.truyenaudio.action.RESUME"
        const val ACTION_PREVIOUS = "com.then.truyenaudio.action.PREVIOUS"
        const val ACTION_NEXT = "com.then.truyenaudio.action.NEXT"
        const val ACTION_SEEK_BACKWARD = "com.then.truyenaudio.action.SEEK_BACKWARD"
        const val ACTION_SEEK_FORWARD = "com.then.truyenaudio.action.SEEK_FORWARD"
        const val ACTION_STOP = "com.then.truyenaudio.action.STOP"
        const val ACTION_SET_SPEED = "com.then.truyenaudio.action.SET_SPEED"
        const val ACTION_SET_SLEEP_TIMER = "com.then.truyenaudio.action.SET_SLEEP_TIMER"
        const val ACTION_CLEAR_SLEEP_TIMER = "com.then.truyenaudio.action.CLEAR_SLEEP_TIMER"
        const val EXTRA_SPEED = "extra_speed"
        const val EXTRA_SLEEP_MINUTES = "extra_sleep_minutes"
        const val PREFERENCES_NAME = "playback_preferences"
        const val KEY_SPEED = "speed"
        const val KEY_VOICE_NAME = "voice_name"
        const val KEY_SEGMENT_INDEX = "segment_index"
        const val KEY_CHAPTER_NUMBER = "chapter_number"
        const val KEY_SLEEP_TIMER_MINUTES = "sleep_timer_minutes"
        const val KEY_SLEEP_TIMER_DEADLINE = "sleep_timer_deadline"
        const val DEFAULT_SPEED = 1f
        const val MAX_SEGMENT_LENGTH = 2800
        const val MIN_SEGMENT_LENGTH = 500
        val SUPPORTED_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        val SPLIT_CHARACTERS = charArrayOf('.', '!', '?', '。', '！', '？', '\n')
    }
}
