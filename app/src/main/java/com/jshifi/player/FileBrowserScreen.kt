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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
        ?.filter {
            it.isDirectory || isAudioFile(it)
        }
        ?.sortedWith(
            compareBy<File> { !it.isDirectory }
                .thenBy {
                    it.name.lowercase()
                }
        )
        ?: emptyList()
}

Column(
    modifier = Modifier.fillMaxSize()
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 8.dp,
                vertical = 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        IconButton(
            onClick = {
                currentDirectory.parentFile?.let {
                    currentDirectory = it
                }
            },
            enabled = currentDirectory.parentFile != null
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Voltar"
            )
        }

        Text(
            text = currentDirectory.absolutePath,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1
        )
    }

    if (files.isEmpty()) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = "Pasta vazia",
                style = MaterialTheme.typography.bodyLarge
            )
        }

    } else {

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {

            items(
                items = files,
                key = {
                    it.absolutePath
                }
            ) { file ->

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {

                            if (file.isDirectory) {
                                currentDirectory = file
                            } else {
                                onAudioSelected(file)
                            }
                        }
                        .padding(
                            horizontal = 16.dp,
                            vertical = 14.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        text = if (file.isDirectory) {
                            "📁"
                        } else {
                            "🎵"
                        },
                        modifier = Modifier.padding(end = 14.dp)
                    )

                    Text(
                        text = file.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )

                    if (file.isDirectory) {
                        Icon(
                            imageVector =
                                Icons.Default.KeyboardArrowRight,
                            contentDescription = "Abrir pasta"
                        )
                    }
                }
            }
        }
    }
}


}
