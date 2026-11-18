package app.childrensimulator.sim

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Independent random streams. Each process samples from its own stream so that adding a draw
 * in one process never shifts the values another process sees (docs/11 §3.6).
 */
enum class Stream(val salt: Long) {
    TEMPERAMENT(0x1001), MILK(0x1002), DIAPER(0x1003), SLEEP(0x1004), ESCALATION(0x1005),
    GAS(0x1006), COMFORT(0x1007), SCENARIO(0x1008), HELPER(0x1009), SESSION(0x100A), PLAY(0x100B),
}

/** Persisted counters: the RNG is a pure function of (seed, stream, counter). */
@Serializable
class RngState(val counters: MutableMap<String, Long> = mutableMapOf())

class Rng(private val seed: Long, private val state: RngState) {

    fun nextLong(stream: Stream): Long {
        val key = stream.name
        val counter = state.counters[key] ?: 0L
        state.counters[key] = counter + 1
        return mix(seed xor mix(stream.salt * GOLDEN) xor mix(counter + 0x5DEECE66DL))
    }

    /** Uniform in [0, 1). */
    fun uniform(stream: Stream): Double = (nextLong(stream) ushr 11) * DOUBLE_UNIT

    fun range(stream: Stream, lo: Double, hi: Double): Double = lo + (hi - lo) * uniform(stream)

    fun intRange(stream: Stream, lo: Int, hiInclusive: Int): Int =
        lo + ((hiInclusive - lo + 1) * uniform(stream)).toInt().coerceAtMost(hiInclusive - lo)

    fun chance(stream: Stream, p: Double): Boolean = uniform(stream) < p

    /** Standard normal via Box-Muller (uses two draws). */
    fun normal(stream: Stream): Double {
        val u1 = max(uniform(stream), 1e-12)
        val u2 = uniform(stream)
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }

    /** Mean-one multiplicative jitter with log-sigma [sigma]. */
    fun jitter(stream: Stream, sigma: Double): Double = exp(sigma * normal(stream) - sigma * sigma / 2.0)

    /** Exponential with the given mean. */
    fun exponential(stream: Stream, mean: Double): Double = -mean * ln(max(1.0 - uniform(stream), 1e-12))

    companion object {
        private const val GOLDEN = -0x61c8864680b583ebL
        private const val DOUBLE_UNIT = 1.0 / (1L shl 53)

        fun mix(z0: Long): Long {
            var z = z0 + GOLDEN
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }
    }
}
