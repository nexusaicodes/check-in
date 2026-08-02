package com.checkin.app.notify.engagement

/**
 * Assigns a device to a copy variant, deterministically and offline.
 *
 * The same install always lands in the same bucket for a given campaign, so a user doesn't see the
 * wording flip between sends; different campaigns bucket independently, so an install unlucky in one
 * experiment isn't systematically in the "A" arm of every other.
 */
object VariantAssigner {

    private const val FNV_OFFSET_BASIS = -0x340d631b7bdddcdbL
    private const val FNV_PRIME = 0x100000001b3L
    private const val BYTE_MASK = 0xffL

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
        var hash = FNV_OFFSET_BASIS
        for (byte in value.encodeToByteArray()) {
            hash = hash xor (byte.toLong() and BYTE_MASK)
            hash *= FNV_PRIME
        }
        return hash and Long.MAX_VALUE
    }
}
