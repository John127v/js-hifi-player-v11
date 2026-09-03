package com.jshifi.player

import kotlin.math.max
import kotlin.math.min

/**
 * Controla o ganho automático do JS HiFi Player.
 *
 * O objetivo principal é levantar músicas muito baixas,
 * evitando ganho exagerado em músicas que já possuem
 * nível elevado.
 */
class AutoGainController {

    companion object {
        private const val MIN_GAIN_DB = 0f
        private const val MAX_GAIN_DB = 10f

        /*
         * Nível RMS desejado.
         *
         * Não tentamos transformar todas as músicas em
         * volume máximo. O alvo é moderado para preservar
         * dinâmica.
         */
        private const val TARGET_RMS_DB = -14f

        /*
         * Quando a música já estiver acima desse nível,
         * não aumentamos.
         */
        private const val HIGH_LEVEL_DB = -10f
    }

    /**
     * Calcula o ganho necessário.
     */
    fun calculateGain(level: AudioLevel): Float {

        val rms = level.rmsDbFS
        val peak = level.peakDbFS

        /*
         * Música já suficientemente alta.
         */
        if (rms >= HIGH_LEVEL_DB) {
            return 0f
        }

        /*
         * Calcula quanto falta para chegar ao alvo.
         */
        val requestedGain =
            TARGET_RMS_DB - rms

        /*
         * Nunca permitimos ganho negativo.
         */
        var gain =
            max(
                MIN_GAIN_DB,
                requestedGain
            )

        /*
         * Limite máximo absoluto.
         */
        gain =
            min(
                MAX_GAIN_DB,
                gain
            )

        /*
         * Proteção adicional contra clipping.
         *
         * Se o pico já estiver muito próximo de 0 dBFS,
         * reduzimos o ganho permitido.
         */
        val peakHeadroom =
            -1f - peak

        if (peakHeadroom < gain) {
            gain =
                max(
                    MIN_GAIN_DB,
                    peakHeadroom
                )
        }

        return gain.coerceIn(
            MIN_GAIN_DB,
            MAX_GAIN_DB
        )
    }

    /**
     * Converte dB para fator linear.
     */
fun dbToLinear(db: Float): Float {
    return Math.pow(
        10.0,
        db.toDouble() / 20.0
    ).toFloat()
}

    /**
     * Suaviza a mudança de ganho.
     *
     * Isso evita saltos bruscos entre músicas.
     */
    fun smoothGain(
        currentDb: Float,
        targetDb: Float,
        amount: Float = 0.15f
    ): Float {

        val safeAmount =
            amount.coerceIn(0.01f, 1f)

        return currentDb +
                (targetDb - currentDb) *
                safeAmount
    }
}
