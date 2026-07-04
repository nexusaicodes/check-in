package com.checkin.app.ui.camera

/** Decides when the device-biometric fallback becomes available after repeated face failures. */
object AuthGate {

    /** Biometric fallback unlocks at this many consecutive face-detection failures. */
    const val BIOMETRIC_FALLBACK_AFTER = 3

    /** The countdown hint starts showing from this failure count. */
    const val HINT_FROM_FAILURE = 2

    fun shouldOfferBiometric(failCount: Int): Boolean = failCount >= BIOMETRIC_FALLBACK_AFTER

    fun shouldShowHint(failCount: Int): Boolean =
        failCount in HINT_FROM_FAILURE until BIOMETRIC_FALLBACK_AFTER

    /** Remaining face attempts before the fallback unlocks; 0 once available. */
    fun attemptsLeft(failCount: Int): Int = (BIOMETRIC_FALLBACK_AFTER - failCount).coerceAtLeast(0)
}
