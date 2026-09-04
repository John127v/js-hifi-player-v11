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

    /**
     * Analisa o nível de uma faixa de áudio.
     *
     * Retorna:
     *
     * - Peak em dBFS
     * - RMS em dBFS
     *
     * A análise é somente leitura.
     *
     * Não modifica o arquivo original.
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

            /*
             * Localiza a primeira faixa de áudio.
             */
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

            /*
             * Cria o decoder apropriado para o codec encontrado.
             */
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
             * Analisa aproximadamente os primeiros 15 segundos.
             *
             * Isso evita uma análise muito demorada em arquivos grandes.
             */
            val maxAnalysisTimeUs = 15_000_000L

            val firstSampleTimeUs = extractor.sampleTime
                .takeIf { it >= 0L }
                ?: 0L

            while (!outputDone) {

                /*
                 * ---------------------------------------------------------
                 * ALIMENTAÇÃO DO DECODER
                 * ---------------------------------------------------------
                 */
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

                                /*
                                 * Limite aproximado de análise.
                                 */
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

                /*
                 * ---------------------------------------------------------
                 * RECEBE PCM DO DECODER
                 * ---------------------------------------------------------
                 */
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

                        /*
                         * O formato de saída mudou.
                         *
                         * O MediaCodec continua sendo utilizado normalmente.
                         */
                    }

                    outputIndex ==
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {

                        /*
                         * Não encerramos imediatamente quando inputDone
                         * estiver ativo.
                         *
                         * O decoder ainda pode possuir dados pendentes.
                         *
                         * O próximo ciclo tentará novamente.
                         */
                    }
                }
            }

            /*
             * Não foi possível obter nenhum sample.
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

    /**
     * Processa o PCM retornado pelo decoder.
     *
     * O decoder Android normalmente fornece PCM 16-bit,
     * mas verificamos o formato de saída antes de interpretar
     * os dados.
     */
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

        /*
         * A saída PCM do decoder é normalmente little-endian.
         */
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        /*
         * PCM 16-bit:
         *
         * 32768 = amplitude máxima negativa
         * 32767 = amplitude máxima positiva
         */
        while (buffer.remaining() >= 2) {

            val sample = buffer.short.toInt()

            val normalized =
                sample.toDouble() / 32768.0

            onSample(normalized)
        }
    }

    /**
     * Converte amplitude linear para dBFS.
     *
     * Exemplos:
     *
     * 1.0  = 0 dBFS
     * 0.5  ≈ -6 dBFS
     * 0.1  ≈ -20 dBFS
     */
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
