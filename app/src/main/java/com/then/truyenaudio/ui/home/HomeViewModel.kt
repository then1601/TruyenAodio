package com.then.truyenaudio.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.then.truyenaudio.data.remote.WebNovelParser
import com.then.truyenaudio.domain.model.Chapter
import com.then.truyenaudio.domain.model.Novel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val url: String = "",
    val novel: Novel? = null,
    val chapters: List<Chapter> = emptyList(),
    val selectedChapter: Chapter? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class HomeViewModel(
    private val parser: WebNovelParser = WebNovelParser()
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun setUrl(url: String) {
        _uiState.value = _uiState.value.copy(url = url, error = null)
    }

    fun loadNovel() {
        val url = _uiState.value.url.trim()
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Vui lòng nhập link truyện.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                val novel = parser.parseNovel(url)
                novel to parser.parseChapters(novel)
            }.onSuccess { result ->
                val novel = result.first
                val chapters = result.second
                _uiState.value = _uiState.value.copy(
                    novel = novel,
                    chapters = chapters,
                    isLoading = false,
                    error = if (chapters.isEmpty()) {
                        "Không tìm thấy chương trên website này."
                    } else null
                )
                Log.d(TAG, "Loaded novel chapters=${chapters.size}")
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error.message ?: "Không thể tải website."
                )
            }
        }
    }

    fun loadChapter(chapter: Chapter, onLoaded: (Chapter) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { parser.parseChapter(chapter) }
                .onSuccess {
                    Log.d(TAG, "Loaded chapter=${it.chapterNumber}, contentLength=${it.content.length}")
                    _uiState.value = _uiState.value.copy(
                        selectedChapter = it,
                        isLoading = false
                    )
                    onLoaded(it)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Không thể tải nội dung chương."
                    )
                }
        }
    }

    private companion object {
        const val TAG = "TruyenAudioReader"
    }
}
