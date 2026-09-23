package com.then.truyenaudio.ui.settings

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.then.truyenaudio.playback.PlaybackService
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences(
            PlaybackService.PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
    }
    val ttsHolder = remember { arrayOfNulls<TextToSpeech>(1) }
    var voices by remember { mutableStateOf<List<Voice>>(emptyList()) }
    var selectedVoice by remember {
        mutableStateOf(preferences.getString(PlaybackService.KEY_VOICE_NAME, null))
    }
    var isLoading by remember { mutableStateOf(true) }

    val settingsTts = remember {
        TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = ttsHolder[0]
                if (engine != null) {
                    engine.setLanguage(Locale.forLanguageTag("vi-VN"))
                    voices = engine.voices
                        .filter { it.locale.language == "vi" }
                        .sortedBy { it.name }
                }
            }
            isLoading = false
        }.also { ttsHolder[0] = it }
    }

    DisposableEffect(settingsTts) {
        onDispose {
            settingsTts.stop()
            settingsTts.shutdown()
            ttsHolder[0] = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cài đặt giọng đọc") },
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
                "Chọn giọng tiếng Việt dùng cho Text-to-Speech",
                style = MaterialTheme.typography.titleMedium
            )
            if (isLoading) {
                CircularProgressIndicator()
            } else if (voices.isEmpty()) {
                Text(
                    "Không tìm thấy giọng tiếng Việt. Hãy cài dữ liệu Tiếng Việt trong cài đặt Text-to-Speech của Android.",
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(voices, key = { it.name }) { voice ->
                        ListItem(
                            headlineContent = { Text(voice.displayName()) },
                            supportingContent = { Text(voice.name) },
                            leadingContent = {
                                RadioButton(
                                    selected = selectedVoice == voice.name,
                                    onClick = {
                                        selectedVoice = voice.name
                                        preferences.edit()
                                            .putString(
                                                PlaybackService.KEY_VOICE_NAME,
                                                voice.name
                                            )
                                            .apply()
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

private fun Voice.displayName(): String =
    name.substringAfter(':').replace('_', ' ').ifBlank { name }
