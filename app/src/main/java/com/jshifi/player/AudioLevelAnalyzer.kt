package com.jshifi.player

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File

data class AudioLevel(
    val peakDbFS: Float,
    val rmsDbFS: Float
)

object AudioLevelAnalyzer {

    /**
     * Localiza a primeira faixa de áudio do arquivo.
     *
     * Esta etapa NÃO modifica o áudio.
     */
    fun findAudioTrack(file: File): MediaFormat? {
        if (!file.exists() || !file.isFile) {
            return null
        }

        val extractor = MediaExtractor()

        return try {
            extractor.setDataSource(file.absolutePath)

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)

                if (mime != null && mime.startsWith("audio/")) {
                    return format
                }
            }

            null
        } catch (_: Exception) {
            null
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Analisa o arquivo.
     *
     * A decodificação PCM será conectada na próxima etapa.
     *
     * Enquanto isso, retornamos null em vez de inventar
     * valores de volume.
     */
    fun analyze(file: File): AudioLevel? {
        val format = findAudioTrack(file)

        if (format == null) {
            return null
        }

        return null
    }
}
