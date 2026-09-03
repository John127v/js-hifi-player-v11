package com.jshifi.player

import kotlin.math.max
import kotlin.math.min

/**

Controle automático de ganho do JS HiFi Player.

Projetado principalmente para recuperar músicas

que possuem nível significativamente mais baixo.

O algoritmo:

Usa RMS como referência principal de volume percebido.
Usa Peak para proteger contra clipping.
Nunca aplica ganho negativo.
Possui limite máximo de ganho.
Mantém headroom para evitar distorção.
Permite suavização para evitar mudanças bruscas.
*/
class AutoGainController {

companion object {

 /**
  * Ganho mínimo.
  *
  * Nunca reduzimos o volume aqui.
  */
 private const val MIN_GAIN_DB = 0f

 /**
  * Ganho máximo permitido pelo Auto Gain.
  *
  * 12 dB é suficiente para recuperar músicas
  * realmente baixas sem transformar o sistema
  * em um simples "volume no máximo".
  */
 private const val MAX_GAIN_DB = 12f

 /**
  * RMS desejado.
  *
  * Um alvo de -14 dBFS preserva uma quantidade
  * razoável de dinâmica.
  */
 private const val TARGET_RMS_DB = -14f

 /**
  * Acima deste RMS não fazemos boost.
  *
  * Isso evita aumentar músicas que já possuem
  * nível elevado.
  */
 private const val HIGH_LEVEL_DB = -10f

 /**
  * Headroom de segurança.
  *
  * Mantemos aproximadamente 1 dB abaixo de 0 dBFS.
  */
 private const val PEAK_HEADROOM_DB = -1f


}

/**

Calcula o ganho recomendado em dB.

Exemplo:

RMS = -25 dBFS

alvo = -14 dBFS

ganho solicitado = +11 dB

Porém o pico também é verificado antes

de retornar o valor final.
*/
fun calculateGain(level: AudioLevel): Float {

val rms = level.rmsDbFS
val peak = level.peakDbFS

/*

Dados inválidos ou extremamente baixos.
Evitamos tentar compensar silêncio absoluto,
ruído ou arquivos que não puderam ser analisados
corretamente.
*/
if (!rms.isFinite() || !peak.isFinite()) {
return MIN_GAIN_DB
}

if (rms <= -120f || peak <= -120f) {
return MIN_GAIN_DB
}

/*

Música já suficientemente alta.
*/
if (rms >= HIGH_LEVEL_DB) {
return MIN_GAIN_DB
}

/*

Quanto falta para alcançar nosso alvo RMS.
*/
val requestedGain =
TARGET_RMS_DB - rms

/*

Nunca permitimos ganho negativo.
*/
var gain = max(
MIN_GAIN_DB,
requestedGain
)

/*

Limite absoluto.
*/
gain = min(
MAX_GAIN_DB,
gain
)

/*

Proteção contra clipping.
Exemplo:
pico = -4 dBFS
headroom permitido = -1 dBFS
ganho máximo seguro = 3 dB
*/
val availableHeadroom =
PEAK_HEADROOM_DB - peak

/*

Se o pico já estiver alto,
reduzimos o ganho disponível.
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

Converte dB para fator linear.

Exemplos:

0 dB = 1.0

+6 dB ≈ 1.995

+10 dB ≈ 3.162
*/
fun dbToLinear(db: Float): Float {

return Math.pow(
10.0,
db.toDouble() / 20.0
).toFloat()
}

/**

Suaviza a mudança de ganho.

Evita que o volume salte repentinamente

quando uma música muda.
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

Retorna true quando a faixa é considerada

significativamente baixa.

Útil para a interface mostrar futuramente

algo como "AUTO GAIN +8.5 dB".
*/
fun isQuietTrack(level: AudioLevel): Boolean {

if (!level.rmsDbFS.isFinite()) {
return false
}

return level.rmsDbFS < HIGH_LEVEL_DB
}
}
