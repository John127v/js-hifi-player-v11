package com.jshifi.player

/**
 * Configurações centrais do processamento de áudio.
 *
 * O ganho é aplicado de forma conservadora:
 *
 * - músicas muito baixas recebem mais ganho;
 * - músicas normais recebem pouco ou nenhum ganho;
 * - existe um limite máximo para evitar amplificação exagerada.
 */
data class AudioSettings(
    val autoGainEnabled: Boolean = true,

    /**
     * Nível alvo aproximado.
     *
     * Valores mais altos deixam o áudio mais forte.
     * Mantemos margem de segurança para reduzir o risco de clipping.
     */
    val targetGainDb: Float = 6f,

    /**
     * Ganho máximo automático.
     *
     * Não permitimos que uma música extremamente baixa
     * receba uma amplificação ilimitada.
     */
    val maxAutoGainDb: Float = 10f,

    /**
     * Margem de segurança.
     *
     * Evita trabalhar constantemente no limite digital.
     */
    val safetyMarginDb: Float = 1.5f
)
