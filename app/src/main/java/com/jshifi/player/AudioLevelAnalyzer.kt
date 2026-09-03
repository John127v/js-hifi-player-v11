package com.jshifi.player

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.log10
import kotlin.math.max

data class AudioLevel(
val peakDbFS: Float,
val rmsDbFS: Float
)

object AudioLevelAnalyzer {

private const val MAX_ANALYSIS_SECONDS = 30L
private const val TIMEOUT_US = 10_000L

/**
 * Localiza a primeira faixa de áudio do arquivo.
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
 * Analisa uma música de forma assíncrona.
 *
 * A análise usa o decoder nativo do Android e trabalha
 * somente com uma pequena parte inicial da música.
 *
 * Isso evita carregar a faixa inteira na memória.
 */
suspend fun analyzeAsync(file: File): AudioLevel? =
    withContext(Dispatchers.IO) {
        analyzeInternal(file)
    }

/**
 * Mantemos analyze() para compatibilidade com código antigo.
 *
 * Não fazemos aqui uma chamada bloqueante ao decoder.
 * O código que estiver em coroutines deve preferir
 * analyzeAsync().
 */
fun analyze(file: File): AudioLevel? {
    return null
}

private fun analyzeInternal(file: File): AudioLevel? {
    if (!file.exists() || !file.isFile) {
        return null
    }

    val extractor = MediaExtractor()
    var codec: MediaCodec? = null

    try {
        extractor.setDataSource(file.absolutePath)

        var audioTrack = -1
        var format: MediaFormat? = null

        for (i in 0 until extractor.trackCount) {
            val candidate = extractor.getTrackFormat(i)
            val mime = candidate.getString(MediaFormat.KEY_MIME)

            if (mime != null && mime.startsWith("audio/")) {
                audioTrack = i
                format = candidate
                break
            }
        }

        if (audioTrack < 0 || format == null) {
            return null
        }

        extractor.selectTrack(audioTrack)

        val mime = format.getString(MediaFormat.KEY_MIME)
            ?: return null

        codec = try {
            MediaCodec.createDecoderByType(mime)
        } catch (_: Exception) {
            return null
        }

        codec.configure(format, null, null, 0)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()

        var inputDone = false
        var outputDone = false

        var totalSamples = 0L
        var sumSquares = 0.0
        var peak = 0.0

        val durationUs =
            if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                Long.MAX_VALUE
            }

        val maxTimeUs =
            minOf(
                durationUs,
                MAX_ANALYSIS_SECONDS * 1_000_000L
            )

        while (!outputDone && totalSamples < 5_000_000L) {

            if (!inputDone) {
                val inputIndex =
                    codec.dequeueInputBuffer(TIMEOUT_US)

                if (inputIndex >= 0) {
                    val inputBuffer =
                        codec.getInputBuffer(inputIndex)

                    if (inputBuffer == null) {
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                    } else {
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
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val sampleTime =
                                extractor.sampleTime

                            if (sampleTime >= maxTimeUs) {
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

            val outputIndex =
                codec.dequeueOutputBuffer(
                    bufferInfo,
                    TIMEOUT_US
                )

            when {
                outputIndex >= 0 -> {
                    val outputBuffer =
                        codec.getOutputBuffer(outputIndex)

                    if (outputBuffer != null &&
                        bufferInfo.size > 0
                    ) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(
                            bufferInfo.offset + bufferInfo.size
                        )

                        /*
                         * A maioria dos decoders Android
                         * entrega PCM 16-bit.
                         *
                         * Se o formato for diferente,
                         * ignoramos este bloco em vez de
                         * interpretar os bytes incorretamente.
                         */
                        val pcmEncoding =
                            if (format.containsKey(
                                    MediaFormat.KEY_PCM_ENCODING
                                )
                            ) {
                                format.getInteger(
                                    MediaFormat.KEY_PCM_ENCODING
                                )
                            } else {
                                2
                            }

                        if (pcmEncoding == 2) {
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
                                    (high shl 8) or low

                                val normalized =
                                    sample / 32768.0

                                val absolute =
                                    kotlin.math.abs(
                                        normalized
                                    )

                                peak =
                                    max(
                                        peak,
                                        absolute
                                    )

                                sumSquares +=
                                    normalized *
                                    normalized

                                totalSamples++
                            }
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
                     * O decoder pode informar o formato PCM
                     * real somente depois de iniciar.
                     *
                     * Não precisamos interromper a análise.
                     */
                }

                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (inputDone) {
                        break
                    }
                }
            }
        }

        if (totalSamples <= 0L) {
            return null
        }

        val rms =
            kotlin.math.sqrt(
                sumSquares / totalSamples.toDouble()
            )

        if (rms <= 0.0000001) {
            return AudioLevel(
                peakDbFS = -120f,
                rmsDbFS = -120f
            )
        }

        val peakDb =
            linearToDb(peak)

        val rmsDb =
            linearToDb(rms)

        return AudioLevel(
            peakDbFS = peakDb,
            rmsDbFS = rmsDb
        )

    } catch (_: Exception) {
        return null
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

private fun linearToDb(value: Double): Float {
    if (value <= 0.0000001) {
        return -120f
    }

    return (
        20.0 * log10(value)
    ).toFloat().coerceIn(
        -120f,
        0f
    )
}


}
