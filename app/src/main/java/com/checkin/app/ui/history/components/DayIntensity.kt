package com.checkin.app.ui.history.components

/**
 * How strongly a calendar day reads, as a `0f..1f` fraction of the user's own longest day.
 *
 * This is what replaced the three-colour PRESENT / HALF_DAY / ABSENT scale. That scale needed a
 * target to classify against and rendered most days as a failure, since targets are missed as the
 * normal case. One hue at varying strength keeps the quantity legible — a 45-minute day is a faint
 * mark, a nine-hour day a solid one — without any of them being a verdict. **Nothing renders red.**
 *
 * Normalized against the all-time peak rather than a constant so no hidden bar creeps back in. The
 * consequence is that landing a personal best re-shades the history once; that is acceptable now
 * that a shade is a quantity rather than a judgement.
 */
object DayIntensity {

    /**
     * The floor a day with any recorded time gets, so the shortest session is still visibly a day
     * the user showed up rather than an empty cell. Showing up at all is the thing being counted;
     * fading it to nothing would say the opposite.
     *
     * Set so that after the caller's background scaling the faintest day still lands at roughly the
     * alpha every classified day used to be drawn at — a 45-minute day beside a nine-hour one must
     * read as *quieter*, never as absent.
     */
    const val MIN_FRACTION = 0.35f

    /**
     * [totalMs] as a fraction of [peakMs].
     *
     * Zero only for a day with no recorded time. A non-positive [peakMs] means there is nothing to
     * compare against — the first day ever recorded, or a set of zero-length days — and every day
     * present is given the full fraction rather than dividing by zero or fading them all out.
     */
    fun fractionOf(totalMs: Long, peakMs: Long): Float {
        if (totalMs <= 0L) return 0f
        if (peakMs <= 0L) return 1f
        val raw = totalMs.toFloat() / peakMs.toFloat()
        return raw.coerceIn(MIN_FRACTION, 1f)
    }
}
