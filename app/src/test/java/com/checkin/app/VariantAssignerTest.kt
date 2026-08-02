package com.checkin.app

import com.checkin.app.notify.engagement.VariantAssigner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class VariantAssignerTest {

    /** The wording must not flip between sends for the same user. */
    @Test
    fun `assignment is stable for the same install and campaign`() {
        val id = "install-abc"
        val first = VariantAssigner.assign(id, "NOT_CHECKED_IN_BY", 2)

        repeat(10) {
            assertEquals(first, VariantAssigner.assign(id, "NOT_CHECKED_IN_BY", 2))
        }
    }

    @Test
    fun `assignment is always within range`() {
        repeat(500) {
            val bucket = VariantAssigner.assign(UUID.randomUUID().toString(), "campaign", 3)
            assertTrue("bucket $bucket out of range", bucket in 0..2)
        }
    }

    /** One bucket per variant, so a single-variant nudge can't index past its only copy. */
    @Test
    fun `a single variant always assigns bucket zero`() {
        repeat(50) {
            assertEquals(0, VariantAssigner.assign(UUID.randomUUID().toString(), "c", 1))
        }
    }

    /**
     * Independent bucketing across campaigns: an install shouldn't land in the "A" arm of every
     * experiment just because it landed there once.
     */
    @Test
    fun `the same install buckets independently per campaign`() {
        val id = "install-abc"
        val campaigns = (1..30).map { "campaign-$it" }
        val buckets = campaigns.map { VariantAssigner.assign(id, it, 2) }

        assertTrue("expected both arms across campaigns", buckets.toSet().size > 1)
    }

    @Test
    fun `distribution across installs is roughly even`() {
        val counts = IntArray(2)
        val samples = 2000
        repeat(samples) {
            counts[VariantAssigner.assign(UUID.randomUUID().toString(), "even", 2)]++
        }

        // Generous bound — this guards against a degenerate hash, not statistical noise.
        counts.forEach { assertTrue("skewed split: ${counts.toList()}", it > samples * 0.4) }
    }

    @Test
    fun `different installs do not all collide`() {
        val a = VariantAssigner.assign("install-a", "c", 100)
        val b = VariantAssigner.assign("install-b", "c", 100)

        assertNotEquals(a, b)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a non-positive variant count is rejected`() {
        VariantAssigner.assign("install", "c", 0)
    }
}
