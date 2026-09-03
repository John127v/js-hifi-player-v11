package com.jshifi.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.MusicNote
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

private val audioExtensions = setOf(
"mp3",
"m4a",
"aac",
"flac",
"wav",
"ogg",
"opus",
"3gp",
"amr"
)

private fun isAudioFile(file: File): Boolean {
return file.isFile &&
file.extension.lowercase() in audioExtensions
}

@Composable
fun FileBrowserScreen(
initialDirectory: File,
onAudioSelected: (File) -> Unit = {}
) {
var currentDirectory by remember {
mutableStateOf(
if (initialDirectory.exists() && initialDirectory.isDirectory) {
initialDirectory
} else {
File("/")
}
)
}

val files = remember(currentDirectory) {
    currentDirectory
        .listFiles()
        ?.filter { it.isDirectory || isAudioFile(it) }
        ?.sortedWith(
            compareBy<File> { !it.isDirectory }
                .thenBy { it.name.lowercase() }
        )
        ?: emptyList()
}

Column(
    modifier = Modifier.fillMaxSize()
) {

    BrowserHeader(
        currentDirectory = currentDirectory,
        onBack = {
            currentDirectory.parentFile?.let {
                currentDirectory = it
            }
        }
    )

    if (files.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null
            )

            Text(
                text = "Nenhuma música ou pasta encontrada.",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = files,
                key = { it.absolutePath }
            ) { file ->

                FileBrowserItem(
                    file = file,
                    onClick = {
                        if (file.isDirectory) {
                            currentDirectory = file
                        } else {
                            onAudioSelected(file)
                        }
                    }
                )
            }
        }
    }
}


}

@Composable
private fun BrowserHeader(
currentDirectory: File,
onBack: () -> Unit
) {
Row(
modifier = Modifier
.fillMaxWidth()
.padding(horizontal = 8.dp, vertical = 6.dp),
verticalAlignment = Alignment.CenterVertically
) {

    IconButton(
        onClick = onBack,
        enabled = currentDirectory.parentFile != null
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowLeft,
            contentDescription = "Voltar"
        )
    }

    Text(
        text = currentDirectory.name.ifEmpty {
            currentDirectory.absolutePath
        },
        modifier = Modifier.padding(start = 4.dp),
        style = MaterialTheme.typography.titleMedium
    )
}


}

@Composable
private fun FileBrowserItem(
file: File,
onClick: () -> Unit
) {
Row(
modifier = Modifier
.fillMaxWidth()
.clickable(onClick = onClick)
.padding(
horizontal = 16.dp,
vertical = 14.dp
),
verticalAlignment = Alignment.CenterVertically
) {

    Icon(
        imageVector = if (file.isDirectory) {
            Icons.Default.Folder
        } else {
            Icons.Default.AudioFile
        },
        contentDescription = if (file.isDirectory) {
            "Pasta"
        } else {
            "Arquivo de áudio"
        }
    )

    Text(
        text = file.name,
        modifier = Modifier
            .padding(start = 16.dp)
            .weight(1f),
        style = MaterialTheme.typography.bodyLarge
    )
}


}
