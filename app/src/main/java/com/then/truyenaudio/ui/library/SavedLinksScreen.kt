package com.then.truyenaudio.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.then.truyenaudio.data.local.SavedLinkStore
import com.then.truyenaudio.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedLinksScreen(
    links: List<String>,
    onBack: () -> Unit,
    onLinkClick: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filteredLinks = remember(links, query) {
        links.filter { it.contains(query.trim(), ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Link truyện đã lưu") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "${links.size}/${SavedLinkStore.MAX_LINKS} link đã lưu",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Tìm kiếm link") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Tìm kiếm")
                }
            )
            if (links.isEmpty()) {
                EmptyState(
                    title = "Chưa có link nào",
                    message = "Các link bạn tải sẽ được lưu lại ở đây."
                )
            } else if (filteredLinks.isEmpty()) {
                EmptyState(
                    title = "Không tìm thấy link",
                    message = "Thử tìm bằng một phần tên miền hoặc URL khác."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredLinks, key = { it }) { url ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    url,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = { Text("Nhấn để tải truyện") },
                            leadingContent = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { onDelete(url) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Xóa link đã lưu"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLinkClick(url) }
                        )
                    }
                }
            }
        }
    }
}
