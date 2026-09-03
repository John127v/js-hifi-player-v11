package com.jshifi.player

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Resultado da análise de uma faixa.
 *
 * peakDbFS = pico máximo encontrado.
 * rmsDbFS  = nível médio aproximado.
 */
data class AudioLevel(
    val peakDbFS: Float,
    val rmsDbFS: Float
)

/**
 * Analisa o nível PCM de arquivos de áudio decodificáveis
 * pelo Android.
 *
 * Esta classe não altera o áudio.
 */
object AudioLevelAnalyzer {

    /**
     * Analisa uma faixa.
     *
     * Retorna null quando o formato não puder ser analisado
     * pelo decoder disponível no dispositivo.
     */
    fun analyze(file: File): AudioLevel? {

        if (!file.exists() || !file.isFile) {
            return null
        }

        val extractor = MediaExtractor()

        return try {

            extractor.setDataSource(file.absolutePath)

            val trackIndex = findAudioTrack(extractor)

            if (trackIndex < 0) {
                return null
            }

            extractor.selectTrack(trackIndex)

            val format = extractor.getTrackFormat(trackIndex)

            analyzePcm(extractor, format)

        } catch (_: Exception) {
            null
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun findAudioTrack(
        extractor: MediaExtractor
    ): Int {

        for (i in 0 until extractor.trackCount) {

            val format = extractor.getTrackFormat(i)

            val mime = format.getString(MediaFormat.KEY_MIME)

            if (mime?.startsWith("audio/") == true) {
                return i
            }
        }

        return -1
    }

    /**
     * Esta primeira versão usa os dados disponíveis no extractor
     * para estabelecer uma estrutura segura para a análise.
     *
     * O processamento PCM completo será conectado ao decoder
     * posteriormente.
     */
    private fun analyzePcm(
        extractor: MediaExtractor,
        format: MediaFormat
    ): AudioLevel? {

        /*
         * Ainda não fazemos uma leitura falsa do arquivo.
         *
         * Se não houver PCM disponível diretamente através do
         * extractor, retornamos null.
         *
         * Isso evita produzir um ganho incorreto.
         */

        return null
    }

    private fun toDbFS(linear: Float): Float {

        if (linear <= 0.000001f) {
            return -120f
        }

        return (
            20f * log10(linear.toDouble())
                .toFloat()
            ).coerceIn(-120f, 0f)
    }
}
