package com.jshifi.player

import kotlin.math.min
import kotlin.math.pow

/**
 * Controlador de ganho automático.
 *
 * Responsável apenas pelo cálculo do ganho.
 * Não modifica EQ, estéreo ou características tonais.
 */
class AutoGainController(
    private val settings: AudioSettings = AudioSettings()
) {

    /**
     * Calcula o ganho automático em dB.
     *
     * @param measuredPeakDbFS pico medido em dBFS.
     *
     * Quanto mais baixa estiver a gravação,
     * maior poderá ser a compensação.
     */
    fun calculateGain(measuredPeakDbFS: Float): Float {

        if (!settings.autoGainEnabled) {
            return 0f
        }

        val peak = measuredPeakDbFS.coerceIn(-60f, 0f)

        // Espaço disponível antes de chegar ao limite digital.
        val headroom =
            -peak - settings.safetyMarginDb

        if (headroom <= 0f) {
            return 0f
        }

        val desiredGain =
            min(settings.targetGainDb, headroom)

        return desiredGain.coerceIn(
            0f,
            settings.maxAutoGainDb
        )
    }

    /**
     * Suaviza alterações de ganho.
     *
     * Evita mudanças bruscas de volume entre músicas.
     */
    fun smoothGain(
        currentGainDb: Float,
        targetGainDb: Float,
        smoothing: Float = 0.15f
    ): Float {

        val factor = smoothing.coerceIn(0.01f, 1f)

        return currentGainDb +
                (targetGainDb - currentGainDb) * factor
    }

    /**
     * Converte decibéis para ganho linear.
     *
     * Exemplo:
     *
     * 0 dB  = 1.0
     * 6 dB  ≈ 1.995
     * 10 dB ≈ 3.162
     */
    fun dbToLinear(db: Float): Float {
        return 10.0.pow(db.toDouble()).toFloat()
    }
}
