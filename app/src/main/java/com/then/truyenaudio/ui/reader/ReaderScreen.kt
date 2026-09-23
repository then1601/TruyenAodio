package com.then.truyenaudio.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.then.truyenaudio.domain.model.Chapter
import com.then.truyenaudio.playback.PlaybackController
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    chapter: Chapter?,
    chapters: List<Chapter> = emptyList(),
    onChapterChange: (Chapter) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val playbackState by PlaybackController.state.collectAsState()
    val displayedChapter = playbackState.chapter ?: chapter
    val content = displayedChapter?.content.orEmpty()
    val selectedSpeed = if (playbackState.chapter == null) {
        PlaybackController.preferredSpeed(context)
    } else {
        playbackState.speed
    }
    val excerpt = content.trim().take(120).ifBlank { "Chưa có nội dung chương." }
    val currentChapterIndex = remember(displayedChapter, chapters) {
        chapters.indexOfFirst { it.chapterNumber == displayedChapter?.chapterNumber }
    }
    val nextChapter = remember(chapter, chapters) {
        chapters.getOrNull(currentChapterIndex + 1)
    }

    var speedMenuExpanded by remember { mutableStateOf(false) }
    var sleepMenuExpanded by remember { mutableStateOf(false) }
    var autoNextEnabled by remember { mutableStateOf(playbackState.autoNext) }
    val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    val sleepOptions = listOf(0, 5, 10, 15, 30, 45, 60)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Đọc truyện") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
                    }
                }
            )
        }
    ) { padding ->
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Đang đọc",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Chương ${displayedChapter?.chapterNumber ?: 1}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = displayedChapter?.title ?: "Chưa chọn chương",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(20.dp))
                if (playbackState.isLoading) {
                    Text(
                        text = "Đang chuẩn bị phát...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                playbackState.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "\"$excerpt${if (content.length > 120) "..." else ""}\"",
                        modifier = Modifier.padding(18.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (playbackState.segmentCount > 0) {
                        "Đoạn ${playbackState.segmentIndex + 1}/${playbackState.segmentCount}"
                    } else {
                        "Chưa bắt đầu đọc"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { PlaybackController.seekBackward(context) },
                        enabled = playbackState.segmentIndex > 0 || playbackState.hasPrevious
                    ) {
                        Icon(Icons.Default.FastRewind, contentDescription = "Lùi đoạn")
                    }
                    Spacer(Modifier.width(24.dp))
                    IconButton(
                        onClick = {
                            if (playbackState.isPlaying) {
                                PlaybackController.pause(context)
                            } else if (displayedChapter != null && content.isNotBlank()) {
                                PlaybackController.play(
                                    context = context,
                                    chapter = displayedChapter,
                                    chapters = chapters,
                                    speed = selectedSpeed,
                                    pitch = playbackState.pitch,
                                    autoNext = autoNextEnabled
                                )
                            }
                        },
                        modifier = Modifier.size(64.dp),
                        enabled = playbackState.isPlaying ||
                            (displayedChapter != null && content.isNotBlank())
                    ) {
                        Icon(
                            if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Tạm dừng" else "Đọc"
                        )
                    }
                    Spacer(Modifier.width(24.dp))
                    IconButton(
                        onClick = { PlaybackController.seekForward(context) },
                        enabled = playbackState.segmentIndex + 1 < playbackState.segmentCount ||
                            playbackState.hasNext
                    ) {
                        Icon(Icons.Default.FastForward, contentDescription = "Tới đoạn")
                    }
                }

                Spacer(Modifier.height(14.dp))
                Box {
                    OutlinedButton(onClick = { speedMenuExpanded = true }) {
                        Text("Tốc độ ${"%.2f".format(selectedSpeed)}x")
                    }
                    DropdownMenu(
                        expanded = speedMenuExpanded,
                        onDismissRequest = { speedMenuExpanded = false }
                    ) {
                        speedOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text("${"%.2f".format(option)}x") },
                                onClick = {
                                    speedMenuExpanded = false
                                    PlaybackController.setSpeed(context, option)
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (nextChapter != null) "Tự động chuyển chương" else "Tự động chuyển chương (chưa có tiếp theo)",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Switch(
                            checked = autoNextEnabled && nextChapter != null,
                            onCheckedChange = { autoNextEnabled = it },
                            enabled = nextChapter != null
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Hẹn giờ ngủ",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Box {
                                OutlinedButton(onClick = { sleepMenuExpanded = true }) {
                                    Text(
                                        if (playbackState.sleepTimerMinutes > 0) {
                                            formatSleepRemaining(playbackState.sleepTimerRemainingMs)
                                        } else {
                                            "Tắt"
                                        }
                                    )
                                }
                                DropdownMenu(
                                    expanded = sleepMenuExpanded,
                                    onDismissRequest = { sleepMenuExpanded = false }
                                ) {
                                    sleepOptions.forEach { minutes ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (minutes == 0) "Tắt" else "${minutes} phút"
                                                )
                                            },
                                            onClick = {
                                                sleepMenuExpanded = false
                                                PlaybackController.setSleepTimer(context, minutes)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        if (playbackState.sleepTimerMinutes > 0) {
                            Text(
                                text = "Phát nhạc sẽ tự dừng sau ${formatSleepRemaining(playbackState.sleepTimerRemainingMs)}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Tốc độ đọc", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Các mức hỗ trợ: 0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 1.75x, 2.0x",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

private fun formatSleepRemaining(remainingMs: Long): String {
    val totalSeconds = (remainingMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
