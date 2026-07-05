package com.checkin.app

import com.checkin.app.data.local.CheckInSession
import com.checkin.app.service.ServiceReconciler
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceReconcilerTest {

    @Test
    fun `no active session reconciles to Stop`() {
        assertEquals(ServiceReconciler.Result.Stop, ServiceReconciler.reconcile(null))
    }

    @Test
    fun `an active session is adopted with its DB values`() {
        val active = CheckInSession(
            id = 7,
            startedAt = 1000L,
            dateKey = "2026-06-15",
            pausedMs = 250L
        )

        val result = ServiceReconciler.reconcile(active)

        assertEquals(
            ServiceReconciler.Result.Adopt(sessionId = 7, startTime = 1000L, pausedMs = 250L, pauseStartedAt = null),
            result
        )
    }

    @Test
    fun `an open pause is carried through so the restored ticker stays frozen`() {
        val paused = CheckInSession(
            id = 3,
            startedAt = 1000L,
            dateKey = "2026-06-15",
            pausedMs = 100L,
            pauseStartedAt = 4000L
        )

        val result = ServiceReconciler.reconcile(paused) as ServiceReconciler.Result.Adopt

        assertEquals(4000L, result.pauseStartedAt)
        assertEquals(100L, result.pausedMs)
    }
}
