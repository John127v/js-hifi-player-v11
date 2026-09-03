package com.jshifi.player

import java.io.File

/**
 * Responsável por navegar no armazenamento acessível ao aplicativo.
 *
 * Não reproduz nem modifica arquivos.
 */
object AudioFileBrowser {

    private val audioExtensions = setOf(
        "mp3",
        "m4a",
        "aac",
        "flac",
        "wav",
        "ogg",
        "opus",
        "amr",
        "3gp"
    )

    /**
     * Retorna os arquivos e pastas existentes dentro do diretório.
     */
    fun list(directory: File): List<AudioFile> {
        if (!directory.exists() || !directory.isDirectory) {
            return emptyList()
        }

        return try {
            directory.listFiles()
                ?.filter { file ->
                    file.isDirectory || isAudioFile(file)
                }
                ?.sortedWith(
                    compareBy<AudioFile> {
                        !it.isDirectory
                    }.thenBy {
                        it.name.lowercase()
                    }
                )
                ?.map { file ->
                    AudioFile(file)
                }
                ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    /**
     * Verifica se o arquivo possui uma extensão de áudio conhecida.
     */
    fun isAudioFile(file: File): Boolean {
        if (!file.exists() || !file.isFile) {
            return false
        }

        return file.extension.lowercase() in audioExtensions
    }

    /**
     * Retorna o diretório pai, quando existir.
     */
    fun parentOf(directory: File): File? {
        if (!directory.exists()) {
            return null
        }

        return directory.parentFile
    }
}
