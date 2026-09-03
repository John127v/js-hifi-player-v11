package com.jshifi.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun FileBrowserScreen(
    initialDirectory: File,
    onAudioFileClick: (File) -> Unit = {}
) {
    var currentDirectory by remember {
        mutableStateOf(initialDirectory)
    }

    val entries = remember(currentDirectory) {
        AudioFileBrowser.list(currentDirectory)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    AudioFileBrowser
                        .parentOf(currentDirectory)
                        ?.let { parent ->
                            currentDirectory = parent
                        }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Voltar"
                )
            }

            IconButton(
                onClick = {
                    currentDirectory = initialDirectory
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Pasta inicial"
                )
            }

            Text(
                text = currentDirectory.name.ifBlank {
                    currentDirectory.absolutePath
                },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )
        }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        if (entries.isEmpty()) {

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Nenhum arquivo de áudio encontrado",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

        } else {

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = entries,
                    key = { it.path }
                ) { audioFile ->

                    FileBrowserItem(
                        audioFile = audioFile,
                        onClick = {
                            if (audioFile.isDirectory) {
                                currentDirectory = audioFile.file
                            } else {
                                onAudioFileClick(audioFile.file)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FileBrowserItem(
    audioFile: AudioFile,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = 12.dp,
                vertical = 14.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            imageVector = if (audioFile.isDirectory) {
                Icons.Default.Folder
            } else {
                Icons.Default.AudioFile
            },
            contentDescription = if (audioFile.isDirectory) {
                "Pasta"
            } else {
                "Arquivo de áudio"
            }
        )

        Spacer(
            modifier = Modifier.padding(horizontal = 6.dp)
        )

        Text(
            text = audioFile.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1
        )
    }
}
