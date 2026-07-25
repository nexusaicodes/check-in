package com.checkin.app.notify.experiment

/**
 * Assigns a device to a copy variant, deterministically and offline.
 *
 * The same install always lands in the same bucket for a given campaign, so a user doesn't see the
 * wording flip between sends; different campaigns bucket independently, so an install unlucky in one
 * experiment isn't systematically in the "A" arm of every other.
 */
object VariantAssigner {

    fun assign(installId: String, campaign: String, variantCount: Int): Int {
        require(variantCount > 0) { "variantCount must be positive, was $variantCount" }
        return (stableHash("$installId/$campaign") % variantCount).toInt()
    }

    /**
     * FNV-1a, 64-bit, masked to stay non-negative. Deliberately not [String.hashCode] — that carries
     * no cross-version stability guarantee, and a bucketing change on upgrade would silently
     * reassign every user mid-experiment.
     */
    private fun stableHash(value: String): Long {
        var hash = -0x340d631b7bdddcdbL // FNV offset basis
        for (byte in value.encodeToByteArray()) {
            hash = hash xor (byte.toLong() and 0xff)
            hash *= 0x100000001b3L // FNV prime
        }
        return hash and Long.MAX_VALUE
    }
}
