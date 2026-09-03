package com.jshifi.player

import kotlin.math.max
import kotlin.math.min

/**
 * Controlador de ganho automático.
 *
 * Importante:
 *
 * Este componente não altera EQ, graves, agudos ou estéreo.
 * Ele trabalha somente no ganho geral.
 *
 * O objetivo é compensar principalmente arquivos muito baixos.
 */
class AutoGainController(
    private val settings: AudioSettings = AudioSettings()
) {

    /**
     * Calcula o ganho recomendado em dB.
     *
     * @param measuredPeakDbFS pico medido da faixa em dBFS.
     *
     * Exemplo:
     *
     * -6 dBFS  -> pouco ou nenhum ganho
     * -15 dBFS -> algum ganho
     * -30 dBFS -> ganho maior
     *
     * O resultado nunca ultrapassa maxAutoGainDb.
     */
    fun calculateGain(measuredPeakDbFS: Float): Float {

        if (!settings.autoGainEnabled) {
            return 0f
        }

        val peak = measuredPeakDbFS.coerceIn(-60f, 0f)

        /*
         * Se a música já estiver muito próxima de 0 dBFS,
         * não devemos aumentar.
         */
        val availableHeadroom =
            -peak - settings.safetyMarginDb

        if (availableHeadroom <= 0f) {
            return 0f
        }

        /*
         * Preferimos uma compensação moderada.
         */
        val desiredGain =
            min(
                settings.targetGainDb,
                availableHeadroom
            )

        return desiredGain.coerceIn(
            0f,
            settings.maxAutoGainDb
        )
    }

    /**
     * Suaviza a transição de ganho entre músicas.
     *
     * Isso evita uma mudança brusca de volume quando o usuário
     * troca de uma música baixa para uma música normal.
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
     * Converte dB para multiplicador linear.
     */
    fun dbToLinear(db: Float): Float {
        return math.pow(
            10.0,
            db / 20.0
        ).toFloat()
    }
}
