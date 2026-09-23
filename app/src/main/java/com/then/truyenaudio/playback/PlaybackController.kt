package com.then.truyenaudio.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.then.truyenaudio.domain.model.Chapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackState(
    val chapter: Chapter? = null,
    val chapters: List<Chapter> = emptyList(),
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val autoNext: Boolean = true,
    val sleepTimerMinutes: Int = 0,
    val sleepTimerRemainingMs: Long = 0L,
    val segmentIndex: Int = 0,
    val segmentCount: Int = 0
) {
    val currentIndex: Int
        get() = chapters.indexOfFirst { it.chapterNumber == chapter?.chapterNumber }

    val hasPrevious: Boolean
        get() = currentIndex > 0

    val hasNext: Boolean
        get() = currentIndex >= 0 && currentIndex < chapters.lastIndex
}

object PlaybackController {
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    internal var pendingQueue: List<Chapter> = emptyList()
    internal var pendingChapter: Chapter? = null
    internal var pendingSpeed: Float = 1f
    internal var pendingPitch: Float = 1f

    fun play(
        context: Context,
        chapter: Chapter,
        chapters: List<Chapter>,
        speed: Float = preferredSpeed(context),
        pitch: Float = state.value.pitch,
        autoNext: Boolean = state.value.autoNext
    ) {
        context.getSharedPreferences(
            PlaybackService.PREFERENCES_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .putFloat(PlaybackService.KEY_SPEED, speed)
            .apply()
        pendingQueue = chapters
        pendingChapter = chapter
        pendingSpeed = speed
        pendingPitch = pitch
        updateState(
            chapter = chapter,
            chapters = chapters,
            isPlaying = true,
            isLoading = true,
            error = null,
            speed = speed,
            pitch = pitch,
            autoNext = autoNext
        )
        startService(context, PlaybackService.ACTION_PLAY)
    }

    fun toggle(context: Context) {
        startService(
            context,
            if (state.value.isPlaying) PlaybackService.ACTION_PAUSE
            else PlaybackService.ACTION_RESUME
        )
    }

    fun pause(context: Context) {
        startService(context, PlaybackService.ACTION_PAUSE)
    }

    fun next(context: Context) {
        startService(context, PlaybackService.ACTION_NEXT)
    }

    fun previous(context: Context) {
        startService(context, PlaybackService.ACTION_PREVIOUS)
    }

    fun seekBackward(context: Context) {
        startService(context, PlaybackService.ACTION_SEEK_BACKWARD)
    }

    fun seekForward(context: Context) {
        startService(context, PlaybackService.ACTION_SEEK_FORWARD)
    }

    fun setSpeed(context: Context, speed: Float) {
        startService(
            context,
            PlaybackService.ACTION_SET_SPEED,
            putSpeed = speed
        )
    }

    fun setSleepTimer(context: Context, minutes: Int) {
        startService(
            context,
            if (minutes > 0) PlaybackService.ACTION_SET_SLEEP_TIMER else PlaybackService.ACTION_CLEAR_SLEEP_TIMER,
            putSleepMinutes = minutes
        )
    }

    fun clearSleepTimer(context: Context) = setSleepTimer(context, 0)

    internal fun updateSleepTimer(minutes: Int = state.value.sleepTimerMinutes, remainingMs: Long = state.value.sleepTimerRemainingMs) {
        updateState(
            sleepTimerMinutes = minutes,
            sleepTimerRemainingMs = remainingMs
        )
    }

    fun preferredSpeed(context: Context): Float =
        context.getSharedPreferences(
            PlaybackService.PREFERENCES_NAME,
            Context.MODE_PRIVATE
        ).getFloat(PlaybackService.KEY_SPEED, PlaybackService.DEFAULT_SPEED)

    fun savedChapterNumber(context: Context, novelId: Int): Int? {
        val preferences = context.getSharedPreferences(
            PlaybackService.PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        val savedNovelId = preferences.getInt(PlaybackService.KEY_NOVEL_ID, Int.MIN_VALUE)
        if (savedNovelId != Int.MIN_VALUE && savedNovelId != novelId) {
            return null
        }
        val chapterNumber = preferences.getInt(
            PlaybackService.KEY_CHAPTER_NUMBER,
            Int.MIN_VALUE
        )
        return chapterNumber.takeUnless { it == Int.MIN_VALUE }
    }

    fun stop(context: Context) {
        startService(context, PlaybackService.ACTION_STOP)
    }

    internal fun updateState(
        chapter: Chapter? = state.value.chapter,
        chapters: List<Chapter> = state.value.chapters,
        isPlaying: Boolean = state.value.isPlaying,
        isLoading: Boolean = state.value.isLoading,
        error: String? = state.value.error,
        speed: Float = state.value.speed,
        pitch: Float = state.value.pitch,
        autoNext: Boolean = state.value.autoNext,
        sleepTimerMinutes: Int = state.value.sleepTimerMinutes,
        sleepTimerRemainingMs: Long = state.value.sleepTimerRemainingMs,
        segmentIndex: Int = state.value.segmentIndex,
        segmentCount: Int = state.value.segmentCount
    ) {
        _state.value = PlaybackState(
            chapter = chapter,
            chapters = chapters,
            isPlaying = isPlaying,
            isLoading = isLoading,
            error = error,
            speed = speed,
            pitch = pitch,
            autoNext = autoNext,
            sleepTimerMinutes = sleepTimerMinutes,
            sleepTimerRemainingMs = sleepTimerRemainingMs,
            segmentIndex = segmentIndex,
            segmentCount = segmentCount
        )
    }

    private fun startService(
        context: Context,
        action: String,
        putSpeed: Float? = null,
        putSleepMinutes: Int? = null
    ) {
        val intent = Intent(context, PlaybackService::class.java).setAction(action)
        putSpeed?.let { intent.putExtra(PlaybackService.EXTRA_SPEED, it) }
        putSleepMinutes?.let { intent.putExtra(PlaybackService.EXTRA_SLEEP_MINUTES, it) }
        ContextCompat.startForegroundService(context, intent)
    }
}
