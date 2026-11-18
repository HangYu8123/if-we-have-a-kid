package app.childrensimulator.sim

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

const val SECOND = 1000L
const val MINUTE = 60 * SECOND
const val HOUR = 60 * MINUTE
const val DAY = 24 * HOUR

/** A daily local-time window, start inclusive, end exclusive. It may wrap midnight. Start == end is empty. */
@Serializable
data class TimeWindow(val startMinute: Int, val endMinute: Int) {
    fun contains(minuteOfDay: Int): Boolean = when {
        startMinute == endMinute -> false
        startMinute < endMinute -> minuteOfDay in startMinute until endMinute
        else -> minuteOfDay >= startMinute || minuteOfDay < endMinute
    }

    val durationMinutes: Int
        get() = if (endMinute >= startMinute) endMinute - startMinute else 1440 - startMinute + endMinute

    fun label(): String = "${hhmm(startMinute)}–${hhmm(endMinute)}"

    companion object {
        /** The reference quiet window used by the age profiles (23:00–07:00). */
        val REFERENCE_NIGHT = TimeWindow(23 * 60, 7 * 60)
        fun hhmm(minute: Int): String = "%02d:%02d".format((minute / 60) % 24, minute % 60)
    }
}

/**
 * Maps the simulation's logical clock to the device's wall clock and local time.
 * Logical time freezes during a pause, so `wall = logical + delta` where delta grows by each pause.
 */
class LocalClock(val zone: ZoneId, val delta: Long) {
    fun wall(logical: Long): Long = logical + delta
    fun logical(wall: Long): Long = wall - delta

    fun zoned(logical: Long): ZonedDateTime = Instant.ofEpochMilli(wall(logical)).atZone(zone)

    fun minuteOfDay(logical: Long): Int = zoned(logical).let { it.hour * 60 + it.minute }

    fun inWindow(logical: Long, window: TimeWindow): Boolean = window.contains(minuteOfDay(logical))

    /**
     * The next logical instant strictly after [logical] at which the local clock reads [minute].
     * A skipped local time (DST gap) resolves to the first valid time after it; a repeated time uses
     * the earlier occurrence (docs/01 §6).
     */
    fun nextLocalMinute(logical: Long, minute: Int): Long {
        var date = zoned(logical).toLocalDate().minusDays(1)
        repeat(5) {
            val candidate = ZonedDateTime.of(date, LocalTime.of((minute / 60) % 24, minute % 60), zone)
            val l = logical(candidate.toInstant().toEpochMilli())
            if (l > logical) return l
            date = date.plusDays(1)
        }
        error("No local time $minute after $logical")
    }

    fun nextWindowBoundary(logical: Long, window: TimeWindow): Long =
        minOf(nextLocalMinute(logical, window.startMinute), nextLocalMinute(logical, window.endMinute))

    /** Stable identity of the window instance containing [logical] (its local start date), or null outside it. */
    fun windowKey(logical: Long, window: TimeWindow): String? {
        if (!inWindow(logical, window)) return null
        val z = zoned(logical)
        val mod = z.hour * 60 + z.minute
        val startDate = if (window.startMinute > window.endMinute && mod < window.endMinute) z.toLocalDate().minusDays(1) else z.toLocalDate()
        // Keyed by the night's start date only, so editing the window cannot replenish its alert budget (docs/05 §3).
        return startDate.toString()
    }

    /** End of the window instance containing [logical]. */
    fun windowEnd(logical: Long, window: TimeWindow): Long = nextLocalMinute(logical, window.endMinute)

    /** The next start of [window] at or after [logical] (now if already inside). */
    fun nextWindowStart(logical: Long, window: TimeWindow): Long =
        if (inWindow(logical, window)) logical else nextLocalMinute(logical, window.startMinute)
}
