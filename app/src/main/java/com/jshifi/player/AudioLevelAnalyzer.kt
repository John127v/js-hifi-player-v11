package com.jshifi.player

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Resultado da análise de volume da música.
 *
 * peakDbFS = pico máximo encontrado.
 * rmsDbFS  = nível médio do áudio.
 */
data class AudioLevel(
    val peakDbFS: Float,
    val rmsDbFS: Float
)

/**
 * Analisador de áudio.
 *
 * IMPORTANTE:
 * - Não altera o arquivo.
 * - Não altera o áudio.
 * - Deve ser executado fora da UI thread.
 * - Retorna null se o Android não conseguir decodificar o arquivo.
 */
object AudioLevelAnalyzer {

    private const val TIMEOUT_US = 10_000L

    /**
     * Tempo máximo analisado.
     *
     * Limitamos a análise para evitar espera excessiva
     * antes da reprodução.
     */
    private const val MAX_ANALYSIS_SECONDS = 90L

    /**
     * Analisa o arquivo de áudio.
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

            decodeAndMeasure(
                extractor = extractor,
                format = format
            )

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
     * Localiza a primeira faixa de áudio.
     */
    private fun findAudioTrack(
        extractor: MediaExtractor
    ): Int {

        for (index in 0 until extractor.trackCount) {

            val format =
                extractor.getTrackFormat(index)

            val mime =
                format.getString(MediaFormat.KEY_MIME)

            if (mime?.startsWith("audio/") == true) {
                return index
            }
        }

        return -1
    }

    /**
     * Decodifica o áudio para PCM e calcula Peak + RMS.
     */
    private fun decodeAndMeasure(
        extractor: MediaExtractor,
        format: MediaFormat
    ): AudioLevel? {

        val mime =
            format.getString(MediaFormat.KEY_MIME)
                ?: return null

        val codec = try {

            MediaCodec.createDecoderByType(mime)

        } catch (_: Exception) {

            return null
        }

        var peak = 0f
        var sumSquares = 0.0
        var sampleCount = 0L

        var inputFinished = false
        var outputFinished = false
        var sawOutput = false

        try {

            codec.configure(
                format,
                null,
                null,
                0
            )

            codec.start()

            val bufferInfo =
                MediaCodec.BufferInfo()

            val maxAnalysisUs =
                MAX_ANALYSIS_SECONDS * 1_000_000L

            extractor.seekTo(
                0L,
                MediaExtractor.SEEK_TO_CLOSEST_SYNC
            )

            while (!outputFinished) {

                /*
                 * Alimenta o decoder.
                 */
                if (!inputFinished) {

                    val inputIndex =
                        codec.dequeueInputBuffer(
                            TIMEOUT_US
                        )

                    if (inputIndex >= 0) {

                        val inputBuffer =
                            codec.getInputBuffer(
                                inputIndex
                            )

                        if (inputBuffer == null) {

                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )

                            inputFinished = true

                        } else {

                            inputBuffer.clear()

                            val sampleSize =
                                extractor.readSampleData(
                                    inputBuffer,
                                    0
                                )

                            val sampleTime =
                                extractor.sampleTime

                            if (
                                sampleSize < 0 ||
                                sampleTime < 0 ||
                                sampleTime > maxAnalysisUs
                            ) {

                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )

                                inputFinished = true

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

                /*
                 * Recebe PCM do decoder.
                 */
                val outputIndex =
                    codec.dequeueOutputBuffer(
                        bufferInfo,
                        TIMEOUT_US
                    )

                when {

                    outputIndex >= 0 -> {

                        val outputBuffer =
                            codec.getOutputBuffer(
                                outputIndex
                            )

                        if (
                            outputBuffer != null &&
                            bufferInfo.size > 0
                        ) {

                            val start =
                                bufferInfo.offset

                            val end =
                                bufferInfo.offset +
                                        bufferInfo.size

                            if (
                                start >= 0 &&
                                end <= outputBuffer.capacity() &&
                                start < end
                            ) {

                                outputBuffer.position(start)
                                outputBuffer.limit(end)

                                /*
                                 * PCM 16-bit little endian.
                                 */
                                while (
                                    outputBuffer.remaining() >= 2
                                ) {

                                    val low =
                                        outputBuffer
                                            .get()
                                            .toInt() and 0xFF

                                    val high =
                                        outputBuffer
                                            .get()
                                            .toInt()

                                    val sample =
                                        (
                                            low or
                                                (high shl 8)
                                            ).toShort()

                                    val normalized =
                                        sample / 32768f

                                    val absolute =
                                        abs(normalized)

                                    if (absolute > peak) {
                                        peak = absolute
                                    }

                                    sumSquares +=
                                        normalized.toDouble() *
                                        normalized.toDouble()

                                    sampleCount++
                                }
                            }

                            sawOutput = true
                        }

                        codec.releaseOutputBuffer(
                            outputIndex,
                            false
                        )

                        if (
                            bufferInfo.flags and
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        ) {

                            outputFinished = true
                        }
                    }

                    outputIndex ==
                            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {

                        /*
                         * O decoder mudou o formato de saída.
                         * Não precisamos alterar nada aqui porque
                         * continuamos lendo PCM.
                         */
                    }

                    outputIndex ==
                            MediaCodec.INFO_TRY_AGAIN_LATER -> {

                        /*
                         * O decoder ainda não possui dados.
                         * O loop continua.
                         */
                    }
                }

                /*
                 * Segurança contra um decoder que não encerre
                 * corretamente.
                 */
                if (
                    inputFinished &&
                    !sawOutput &&
                    sampleCount == 0L
                ) {
                    break
                }
            }

            if (
                !sawOutput ||
                sampleCount <= 0L
            ) {
                return null
            }

            val rms =
                sqrt(
                    sumSquares /
                            sampleCount.toDouble()
                ).toFloat()

            AudioLevel(
                peakDbFS = toDbFS(peak),
                rmsDbFS = toDbFS(rms)
            )

        } catch (_: Exception) {

            null

        } finally {

            try {
                codec.stop()
            } catch (_: Exception) {
            }

            try {
                codec.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Converte amplitude linear para dBFS.
     */
    private fun toDbFS(
        linear: Float
    ): Float {

        if (linear <= 0.000001f) {
            return -120f
        }

        return (
            20.0 *
                log10(linear.toDouble())
            ).toFloat().coerceIn(
                -120f,
                0f
            )
    }
}
