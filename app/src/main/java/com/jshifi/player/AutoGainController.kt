package com.jshifi.player

import kotlin.math.max
import kotlin.math.min

/**
 * Controle automático de ganho do JS HiFi Player.
 *
 * O objetivo é corrigir somente músicas com nível
 * significativamente baixo.
 *
 * Não reduz o volume de nenhuma música.
 * Não altera equalização ou dinâmica.
 *
 * O ganho é calculado usando:
 *
 * - RMS: referência principal do nível da música.
 * - Peak: limite de segurança contra clipping.
 */
class AutoGainController {

    companion object {

        /**
         * Nunca aplicamos ganho negativo.
         */
        private const val MIN_GAIN_DB = 0f

        /**
         * Limite máximo do ganho automático.
         *
         * Evita amplificação exagerada em arquivos
         * extremamente baixos.
         */
        private const val MAX_GAIN_DB = 10f

        /**
         * Nível a partir do qual a música é considerada
         * realmente baixa.
         *
         * Acima deste valor não fazemos correção.
         */
        private const val QUIET_LEVEL_DB = -18f

        /**
         * Nível alvo para as músicas que precisam
         * de correção.
         *
         * Não tentamos deixar todas as músicas nesse
         * nível. Ele serve apenas como referência para
         * calcular o ganho necessário.
         */
        private const val TARGET_RMS_DB = -14f

        /**
         * Mantém aproximadamente 1 dB de margem
         * abaixo de 0 dBFS.
         */
        private const val PEAK_HEADROOM_DB = -1f
    }

    /**
     * Calcula o ganho recomendado em dB.
     *
     * Músicas acima de -18 dB RMS não recebem ganho.
     *
     * Exemplo:
     *
     * RMS = -20 dBFS
     * alvo = -14 dBFS
     * ganho solicitado = +6 dB
     *
     * O Peak é usado para limitar o ganho caso
     * exista risco de clipping.
     */
    fun calculateGain(level: AudioLevel): Float {

        val rms = level.rmsDbFS
        val peak = level.peakDbFS

        /*
         * Proteção contra valores inválidos.
         */
        if (!rms.isFinite() || !peak.isFinite()) {
            return MIN_GAIN_DB
        }

        /*
         * Silêncio ou análise inválida.
         */
        if (rms <= -120f || peak <= -120f) {
            return MIN_GAIN_DB
        }

        /*
         * Música com nível normal:
         * não fazemos absolutamente nenhuma alteração.
         */
        if (rms >= QUIET_LEVEL_DB) {
            return MIN_GAIN_DB
        }

        /*
         * Calcula somente o ganho necessário
         * para aproximar o RMS do alvo.
         */
        val requestedGain =
            TARGET_RMS_DB - rms

        /*
         * Nunca permitimos ganho negativo.
         */
        var gain = max(
            MIN_GAIN_DB,
            requestedGain
        )

        /*
         * Limite máximo do Auto Gain.
         */
        gain = min(
            MAX_GAIN_DB,
            gain
        )

        /*
         * Verifica quanto ganho o pico permite.
         *
         * Exemplo:
         *
         * Peak = -4 dBFS
         * Headroom = -1 dBFS
         *
         * Ganho máximo seguro = +3 dB
         */
        val availableHeadroom =
            PEAK_HEADROOM_DB - peak

        /*
         * Se o ganho solicitado ultrapassar
         * o headroom disponível, usamos somente
         * o ganho seguro.
         */
        if (availableHeadroom < gain) {

            gain = max(
                MIN_GAIN_DB,
                availableHeadroom
            )
        }

        return gain.coerceIn(
            MIN_GAIN_DB,
            MAX_GAIN_DB
        )
    }

    /**
     * Converte dB para fator linear.
     *
     * Exemplos:
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
     * Suaviza a mudança de ganho.
     *
     * Evita mudanças bruscas quando o ganho
     * automático for aplicado durante a reprodução.
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
     * Retorna true somente quando a música
     * está abaixo do nível considerado normal.
     */
    fun isQuietTrack(level: AudioLevel): Boolean {

        if (!level.rmsDbFS.isFinite()) {
            return false
        }

        return level.rmsDbFS < QUIET_LEVEL_DB
    }
}
