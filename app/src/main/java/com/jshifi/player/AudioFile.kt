package com.jshifi.player

import java.io.File

/**
 * Representa um arquivo de áudio exibido pelo navegador.
 */
data class AudioFile(
    val file: File,
    val name: String = file.name,
    val isDirectory: Boolean = file.isDirectory
) {
    val path: String
        get() = file.absolutePath

    val extension: String
        get() = file.extension.lowercase()
}
