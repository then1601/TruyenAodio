package com.then.truyenaudio.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.then.truyenaudio.domain.model.Chapter
import com.then.truyenaudio.domain.model.Novel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelDetailScreen(
    novel: Novel?,
    chapters: List<Chapter>,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onChapterClick: (Chapter) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(novel?.title ?: "Chi tiết truyện") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
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
                    .widthIn(max = 720.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                novel?.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it)
                }
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    Text("Danh sách chương", style = MaterialTheme.typography.titleMedium)
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(chapters) { chapter ->
                            ListItem(
                                headlineContent = { Text(chapter.title) },
                                supportingContent = { Text("Chương ${chapter.chapterNumber}") },
                                modifier = Modifier.clickable { onChapterClick(chapter) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
