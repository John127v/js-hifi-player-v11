package com.jshifi.player

import kotlin.math.max
import kotlin.math.min

/**
 * Controle automático de ganho do JS HiFi Player.
 *
 * Corrige somente músicas com nível significativamente baixo.
 *
 * Não reduz o volume de músicas normais.
 * Não altera equalização ou dinâmica.
 */
class AutoGainController(
    private val settings: AudioSettings = AudioSettings()
) {

    /**
     * Calcula o ganho recomendado em dB.
     *
     * O RMS determina quanto a música está baixa.
     * O Peak determina quanto podemos aumentar
     * sem ultrapassar a margem de segurança.
     */
    fun calculateGain(level: AudioLevel): Float {

        val rms = level.rmsDbFS
        val peak = level.peakDbFS

        /*
         * Proteção contra valores inválidos.
         */
        if (!rms.isFinite() || !peak.isFinite()) {
            return 0f
        }

        /*
         * Silêncio ou análise inválida.
         */
        if (rms <= -120f || peak <= -120f) {
            return 0f
        }

        /*
         * Se o Auto Gain estiver desligado,
         * não fazemos nenhuma correção.
         */
        if (!settings.autoGainEnabled) {
            return 0f
        }

        /*
         * Música com nível normal:
         * não alteramos absolutamente nada.
         */
        if (rms >= settings.quietLevelDb) {
            return 0f
        }

        /*
         * Calcula somente o ganho necessário
         * para aproximar a música do RMS alvo.
         */
        val requestedGain =
            settings.targetRmsDb - rms

        /*
         * Nunca aplicamos ganho negativo.
         */
        var gain = max(
            0f,
            requestedGain
        )

        /*
         * Respeita o limite configurado.
         */
        gain = min(
            settings.maxAutoGainDb,
            gain
        )

        /*
         * Calcula o ganho máximo permitido pelo Peak.
         *
         * Exemplo:
         *
         * Peak = -4 dBFS
         * Margem = 1 dB
         *
         * Ganho máximo seguro = +3 dB.
         */
        val peakLimitDb =
            -settings.safetyMarginDb - peak

        /*
         * Nunca permitimos que o ganho ultrapasse
         * o headroom disponível.
         */
        if (peakLimitDb < gain) {
            gain = max(
                0f,
                peakLimitDb
            )
        }

        return gain.coerceIn(
            0f,
            settings.maxAutoGainDb
        )
    }

    /**
     * Converte dB para fator linear.
     *
     * 0 dB  = 1.0
     * +6 dB ≈ 1.995
     * +10 dB ≈ 3.162
     */
    fun dbToLinear(db: Float): Float {

        return Math.pow(
            10.0,
            db.toDouble() / 20.0
        ).toFloat()
    }

    /**
     * Suaviza uma mudança de ganho.
     *
     * Evita mudanças bruscas durante a reprodução.
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

    /**
     * Informa se a música possui nível abaixo
     * do limite configurado para correção.
     */
    fun isQuietTrack(level: AudioLevel): Boolean {

        if (!level.rmsDbFS.isFinite()) {
            return false
        }

        return settings.autoGainEnabled &&
            level.rmsDbFS < settings.quietLevelDb
    }
}
