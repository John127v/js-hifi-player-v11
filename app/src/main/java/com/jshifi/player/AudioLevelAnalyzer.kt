package com.jshifi.player

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

data class AudioLevel(
val peakDbFS: Float,
val rmsDbFS: Float
)

object AudioLevelAnalyzer {

/**
 * Localiza a primeira faixa de áudio do arquivo.
 *
 * Esta função somente consulta o arquivo.
 * O áudio original não é alterado.
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
 * Analisa o nível de uma faixa de áudio.
 *
 * O objetivo é obter uma estimativa segura de:
 *
 * - Peak em dBFS
 * - RMS em dBFS
 *
 * A análise é somente leitura.
 *
 * Se determinado codec/formato não puder ser decodificado
 * pelo MediaCodec do aparelho, retornamos null em vez de
 * inventar valores.
 */
fun analyze(file: File): AudioLevel? {

    if (!file.exists() || !file.isFile) {
        return null
    }

    val extractor = MediaExtractor()

    var codec: MediaCodec? = null

    return try {

        extractor.setDataSource(file.absolutePath)

        var audioFormat: MediaFormat? = null

        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)

            if (mime != null && mime.startsWith("audio/")) {
                audioFormat = format
                extractor.selectTrack(i)
                break
            }
        }

        if (audioFormat == null) {
            return null
        }

        val mime = audioFormat.getString(MediaFormat.KEY_MIME)
            ?: return null

        codec = try {
            MediaCodec.createDecoderByType(mime)
        } catch (_: Exception) {
            null
        }

        if (codec == null) {
            return null
        }

        codec.configure(
            audioFormat,
            null,
            null,
            0
        )

        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()

        var inputDone = false
        var outputDone = false

        var peak = 0.0
        var sumSquares = 0.0
        var sampleCount = 0L

        val startTimeUs = extractor.sampleTime
        val maxAnalysisTimeUs = 15_000_000L

        while (!outputDone) {

            /*
             * Alimenta o decoder.
             */
            if (!inputDone) {

                val inputIndex = codec.dequeueInputBuffer(10_000)

                if (inputIndex >= 0) {

                    val inputBuffer = codec.getInputBuffer(inputIndex)

                    if (inputBuffer == null) {
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                    } else {

                        inputBuffer.clear()

                        val sampleSize = extractor.readSampleData(
                            inputBuffer,
                            0
                        )

                        if (sampleSize < 0) {

                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )

                            inputDone = true

                        } else {

                            val sampleTime = extractor.sampleTime

                            /*
                             * Limitamos a aproximadamente 15 segundos
                             * para evitar análise excessivamente longa
                             * de arquivos grandes.
                             */
                            if (
                                startTimeUs >= 0 &&
                                sampleTime >= startTimeUs + maxAnalysisTimeUs
                            ) {

                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    sampleTime,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )

                                inputDone = true

                            } else {

                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    sampleSize,
                                    sampleTime,
                                    0
                                )

                                extractor.advance()
                            }
                        }
                    }
                }
            }

            /*
             * Obtém PCM decodificado.
             */
            val outputIndex = codec.dequeueOutputBuffer(
                bufferInfo,
                10_000
            )

            when {

                outputIndex >= 0 -> {

                    val outputBuffer = codec.getOutputBuffer(outputIndex)

                    if (outputBuffer != null && bufferInfo.size > 0) {

                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(
                            bufferInfo.offset + bufferInfo.size
                        )

                        /*
                         * A maioria dos decoders Android fornece PCM
                         * de 16 bits quando configurado dessa forma.
                         *
                         * Fazemos uma leitura conservadora dos samples.
                         */
                        while (outputBuffer.remaining() >= 2) {

                            val low = outputBuffer.get().toInt() and 0xFF
                            val high = outputBuffer.get().toInt()

                            val sample =
                                ((high shl 8) or low).toShort().toInt()

                            val normalized =
                                abs(sample.toDouble()) / 32768.0

                            if (normalized > peak) {
                                peak = normalized
                            }

                            sumSquares += normalized * normalized
                            sampleCount++
                        }
                    }

                    codec.releaseOutputBuffer(
                        outputIndex,
                        false
                    )

                    if (
                        bufferInfo.flags and
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    ) {
                        outputDone = true
                    }
                }

                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    /*
                     * O decoder mudou o formato de saída.
                     * Não precisamos fazer nada aqui.
                     */
                }

                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    /*
                     * Nenhum buffer disponível neste momento.
                     *
                     * Se a entrada já terminou, encerramos para evitar
                     * loop infinito em codecs problemáticos.
                     */
                    if (inputDone) {
                        outputDone = true
                    }
                }
            }
        }

        if (sampleCount <= 0) {
            null
        } else {

            val rms = sqrt(
                sumSquares / sampleCount.toDouble()
            )

            AudioLevel(
                peakDbFS = amplitudeToDb(peak),
                rmsDbFS = amplitudeToDb(rms)
            )
        }

    } catch (_: Exception) {
        null

    } finally {

        try {
            codec?.stop()
        } catch (_: Exception) {
        }

        try {
            codec?.release()
        } catch (_: Exception) {
        }

        try {
            extractor.release()
        } catch (_: Exception) {
        }
    }
}

/**
 * Converte amplitude linear para dBFS.
 *
 * - 1.0 = 0 dBFS
 * - 0.5 ≈ -6 dBFS
 * - 0.1 ≈ -20 dBFS
 */
private fun amplitudeToDb(amplitude: Double): Float {

    if (amplitude <= 0.000001) {
        return -120f
    }

    return (20.0 * log10(amplitude))
        .toFloat()
        .coerceIn(-120f, 0f)
}


}
