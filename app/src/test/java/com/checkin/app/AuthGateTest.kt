package com.checkin.app

import com.checkin.app.ui.camera.AuthGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthGateTest {

    @Test
    fun `biometric unlocks only at the threshold`() {
        assertFalse(AuthGate.shouldOfferBiometric(0))
        assertFalse(AuthGate.shouldOfferBiometric(2))
        assertTrue(AuthGate.shouldOfferBiometric(3))
        assertTrue(AuthGate.shouldOfferBiometric(4))
    }

    @Test
    fun `hint shows only between its start and the threshold`() {
        assertFalse(AuthGate.shouldShowHint(1))
        assertTrue(AuthGate.shouldShowHint(2))
        assertFalse(AuthGate.shouldShowHint(3))
    }

    @Test
    fun `attempts left counts down and floors at zero`() {
        assertEquals(3, AuthGate.attemptsLeft(0))
        assertEquals(2, AuthGate.attemptsLeft(1))
        assertEquals(1, AuthGate.attemptsLeft(2))
        assertEquals(0, AuthGate.attemptsLeft(3))
        assertEquals(0, AuthGate.attemptsLeft(4))
    }
}
