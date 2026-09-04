package com.jshifi.player

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

data class AudioLevel(
    val peakDbFS: Float,
    val rmsDbFS: Float
)

object AudioLevelAnalyzer {

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

                if (mime?.startsWith("audio/") == true) {
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

                if (mime?.startsWith("audio/") == true) {

                    audioFormat = format
                    extractor.selectTrack(i)

                    break
                }
            }

            if (audioFormat == null) {
                return null
            }

            val mime = audioFormat
                .getString(MediaFormat.KEY_MIME)
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

            /*
             * Analisa no máximo os primeiros 15 segundos.
             */
            val maxAnalysisTimeUs = 15_000_000L

            /*
             * Limite de segurança para impedir que um decoder
             * problemático fique preso indefinidamente.
             *
             * O limite é contado em ciclos do processamento,
             * não altera o áudio analisado.
             */
            val maxProcessingCycles = 5000
            var processingCycles = 0

            val firstSampleTimeUs = extractor.sampleTime
                .takeIf { it >= 0L }
                ?: 0L

            while (!outputDone) {

                processingCycles++

                /*
                 * Proteção contra loop infinito do MediaCodec.
                 */
                if (processingCycles > maxProcessingCycles) {
                    break
                }

                if (!inputDone) {

                    val inputIndex = codec.dequeueInputBuffer(10_000)

                    if (inputIndex >= 0) {

                        val inputBuffer =
                            codec.getInputBuffer(inputIndex)

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

                            val sampleSize =
                                extractor.readSampleData(
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

                                val sampleTime =
                                    extractor.sampleTime

                                if (
                                    sampleTime >=
                                    firstSampleTimeUs +
                                    maxAnalysisTimeUs
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

                val outputIndex = codec.dequeueOutputBuffer(
                    bufferInfo,
                    10_000
                )

                when {

                    outputIndex >= 0 -> {

                        val outputBuffer =
                            codec.getOutputBuffer(outputIndex)

                        if (
                            outputBuffer != null &&
                            bufferInfo.size > 0
                        ) {

                            processPcmBuffer(
                                outputBuffer = outputBuffer,
                                bufferInfo = bufferInfo,
                                onSample = { normalized ->

                                    val absolute =
                                        abs(normalized)

                                    if (absolute > peak) {
                                        peak = absolute
                                    }

                                    sumSquares +=
                                        normalized * normalized

                                    sampleCount++
                                }
                            )
                        }

                        val endOfStream =
                            bufferInfo.flags and
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0

                        codec.releaseOutputBuffer(
                            outputIndex,
                            false
                        )

                        if (endOfStream) {
                            outputDone = true
                        }
                    }

                    outputIndex ==
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // O formato de saída será tratado pelo decoder.
                    }

                    outputIndex ==
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        /*
                         * Não encerramos a análise imediatamente.
                         *
                         * O decoder pode simplesmente ainda não ter
                         * produzido um bloco de áudio.
                         */
                    }
                }
            }

            /*
             * Se não conseguimos obter nenhuma amostra PCM,
             * a análise não é considerada válida.
             */
            if (sampleCount <= 0L) {
                return null
            }

            val rms = sqrt(
                sumSquares /
                    sampleCount.toDouble()
            )

            AudioLevel(
                peakDbFS = amplitudeToDb(peak),
                rmsDbFS = amplitudeToDb(rms)
            )

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

    private fun processPcmBuffer(
        outputBuffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo,
        onSample: (Double) -> Unit
    ) {

        val buffer = outputBuffer.duplicate()

        buffer.position(bufferInfo.offset)

        val endPosition =
            (bufferInfo.offset + bufferInfo.size)
                .coerceAtMost(buffer.limit())

        buffer.limit(endPosition)

        buffer.order(ByteOrder.LITTLE_ENDIAN)

        while (buffer.remaining() >= 2) {

            val sample = buffer.short.toInt()

            val normalized =
                sample.toDouble() / 32768.0

            onSample(normalized)
        }
    }

    private fun amplitudeToDb(
        amplitude: Double
    ): Float {

        if (
            amplitude <= 0.000001 ||
            !amplitude.isFinite()
        ) {
            return -120f
        }

        return (
            20.0 * log10(amplitude)
        )
            .toFloat()
            .coerceIn(-120f, 0f)
    }
}
