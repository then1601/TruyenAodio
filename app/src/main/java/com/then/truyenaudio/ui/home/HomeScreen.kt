package com.then.truyenaudio.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.then.truyenaudio.playback.PlaybackController
import com.then.truyenaudio.ui.components.EmptyState
import com.then.truyenaudio.ui.components.ErrorState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onUrlChange: (String) -> Unit,
    onLoad: () -> Unit,
    onOpenNovel: () -> Unit,
    onOpenSettings: () -> Unit,
    savedLinkCount: Int,
    onOpenSavedLinks: () -> Unit
) {
    val playbackState by PlaybackController.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Truyện Audio") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Cài đặt")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Nghe truyện theo cách của bạn", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Tải truyện từ URL và bắt đầu nghe bằng giọng đọc tiếng Việt.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                playbackState.chapter?.let { currentChapter ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Tiếp tục nghe", style = MaterialTheme.typography.labelLarge)
                            Text(
                                currentChapter.title,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Chương ${currentChapter.chapterNumber} · Đoạn ${playbackState.segmentIndex + 1}/${playbackState.segmentCount.coerceAtLeast(1)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = onOpenNovel) {
                                Text("Mở trình đọc")
                            }
                        }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Thêm truyện", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = state.url,
                            onValueChange = onUrlChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Link website truyện") },
                            singleLine = true,
                            placeholder = { Text("https://...") }
                        )
                        Button(
                            onClick = onLoad,
                            enabled = !state.isLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (state.isLoading) "Đang tải..." else "Tải truyện")
                        }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.ListItem(
                        headlineContent = {
                            Text("Thư viện link truyện")
                        },
                        supportingContent = {
                            Text(
                                if (savedLinkCount == 0) {
                                    "Lưu link để lần sau không cần dán lại"
                                } else {
                                    "$savedLinkCount link đã lưu "
                                }
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSavedLinks() }
                    )
                }

                state.error?.let {
                    ErrorState(message = it)
                }
                state.novel?.let { novel ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(novel.title, style = MaterialTheme.typography.titleLarge)
                            Text("${state.chapters.size} chương", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                novel.description.ifBlank { "Truyện đã được tải và sẵn sàng mở danh sách chương." },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(onClick = onOpenNovel, modifier = Modifier.fillMaxWidth()) {
                                Text("Xem danh sách chương")
                            }
                        }
                    }
                } ?: EmptyState(
                    title = "Chưa có truyện nào",
                    message = "Dán link truyện ở trên để bắt đầu thư viện nghe của bạn."
                )
            }
        }
    }
}
