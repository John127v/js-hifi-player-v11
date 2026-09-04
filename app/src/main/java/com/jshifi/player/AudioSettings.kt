package com.jshifi.player

/**
 * Configurações centrais do ganho automático do JS HiFi Player.
 *
 * O Auto Gain atua somente sobre músicas com nível
 * significativamente baixo.
 *
 * Não reduz o volume de músicas que já possuem nível normal.
 */
data class AudioSettings(

    /**
     * Ativa ou desativa o ganho automático.
     */
    val autoGainEnabled: Boolean = true,

    /**
     * Nível RMS abaixo do qual a música será considerada baixa.
     *
     * Músicas com RMS igual ou acima deste valor
     * não recebem correção automática.
     */
    val quietLevelDb: Float = -18f,

    /**
     * Nível RMS utilizado como referência para calcular
     * a quantidade de ganho necessária.
     */
    val targetRmsDb: Float = -14f,

    /**
     * Limite máximo de ganho automático.
     */
    val maxAutoGainDb: Float = 10f,

    /**
     * Margem de segurança abaixo de 0 dBFS.
     *
     * Mantém espaço para evitar clipping.
     */
    val safetyMarginDb: Float = 1f
)
