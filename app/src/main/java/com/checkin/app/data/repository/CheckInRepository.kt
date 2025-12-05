package com.checkin.app.data.repository

import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.local.CheckInSessionDao
import kotlinx.coroutines.flow.Flow

class CheckInRepository(private val dao: CheckInSessionDao) {
    val allSessions: Flow<List<CheckInSession>> = dao.getAllSessions()

    suspend fun startSession(description: String): Long {
        val session = CheckInSession(
            startTimestamp = System.currentTimeMillis(),
            description = description
        )
        return dao.insertSession(session)
    }

    suspend fun stopSession(sessionId: Long, endTimestamp: Long) {
        val sessions = dao.getAllSessionsForExport()
        val session = sessions.find { it.id == sessionId }
        session?.let {
            val duration = endTimestamp - it.startTimestamp
            val updatedSession = it.copy(
                endTimestamp = endTimestamp,
                durationMillis = duration
            )
            dao.updateSession(updatedSession)
        }
    }

    suspend fun getActiveSession(): CheckInSession? = dao.getActiveSession()

    suspend fun getAllSessionsForExport(): List<CheckInSession> = dao.getAllSessionsForExport()
}
