package com.jshifi.player

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Resultado da análise de nível de uma música.
 *
 * peakDbFS:
 *     pico máximo encontrado.
 *
 * rmsDbFS:
 *     nível médio aproximado.
 */
data class AudioLevel(
    val peakDbFS: Float,
    val rmsDbFS: Float
)

/**
 * Analisa o áudio decodificando alguns trechos do arquivo para PCM.
 *
 * A análise é somente leitura.
 * Nenhum áudio é modificado.
 */
object AudioLevelAnalyzer {

    private const val TIMEOUT_US = 10_000L

    /*
     * Para não gastar CPU analisando uma música inteira,
     * usamos uma janela inicial e algumas posições posteriores.
     */
    private const val MAX_ANALYSIS_SECONDS = 90L

    /**
     * Analisa uma música.
     *
     * Deve ser chamada fora da UI thread.
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

    private fun findAudioTrack(
        extractor: MediaExtractor
    ): Int {

        for (index in 0 until extractor.trackCount) {

            val format = extractor.getTrackFormat(index)

            val mime =
                format.getString(MediaFormat.KEY_MIME)

            if (mime?.startsWith("audio/") == true) {
                return index
            }
        }

        return -1
    }

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
        var sawOutput = false

        try {

            codec.configure(
                format,
                null,
                null,
                0
            )

            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()

            var inputFinished = false
            var outputFinished = false

            val startUs = 0L
            val maxUs =
                MAX_ANALYSIS_SECONDS * 1_000_000L

            extractor.seekTo(
                startUs,
                MediaExtractor.SEEK_TO_CLOSEST_SYNC
            )

            while (!outputFinished) {

                if (!inputFinished) {

                    val inputIndex =
                        codec.dequeueInputBuffer(TIMEOUT_US)

                    if (inputIndex >= 0) {

                        val inputBuffer =
                            codec.getInputBuffer(inputIndex)

                        if (inputBuffer == null) {
                            inputFinished = true
                        } else {

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
                                sampleTime > maxUs
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

                val outputIndex =
                    codec.dequeueOutputBuffer(
                        bufferInfo,
                        TIMEOUT_US
                    )

                when {

                    outputIndex >= 0 -> {

                        val outputBuffer =
                            codec.getOutputBuffer(outputIndex)

                        if (
                            outputBuffer != null &&
                            bufferInfo.size > 0
                        ) {

                            outputBuffer.position(
                                bufferInfo.offset
                            )

                            outputBuffer.limit(
                                bufferInfo.offset +
                                        bufferInfo.size
                            )

                            /*
                             * A saída PCM típica do Android
                             * é PCM 16-bit little endian.
                             */
                            while (
                                outputBuffer.remaining() >= 2
                            ) {

                                val low =
                                    outputBuffer.get()
                                        .toInt() and 0xFF

                                val high =
                                    outputBuffer.get()
                                        .toInt()

                                val sample =
                                    (
                                        low or
                                            (high shl 8)
                                        ).toShort()

                                val normalized =
                                    sample / 32768f

                                val absolute =
                                    kotlin.math.abs(
                                        normalized
                                    )

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
                        // O novo formato será usado pelo decoder.
                    }

                    outputIndex ==
                            MediaCodec.INFO_TRY_AGAIN_LATER -> {

                        if (inputFinished) {
                            /*
                             * Damos algumas oportunidades ao
                             * decoder para terminar o processamento.
                             */
                        }
                    }
                }
            }

            if (!sawOutput || sampleCount == 0L) {
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
