package com.checkin.app.data.repository

import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.local.CheckInSessionDao
import kotlinx.coroutines.flow.Flow

class CheckInRepository(private val dao: CheckInSessionDao) {

    suspend fun getCompletedSessions(limit: Int, offset: Int): List<CheckInSession> {
        return dao.getCompletedSessions(limit = limit, offset = offset)
    }

    fun getCompletedSessionsFlow(limit: Int): Flow<List<CheckInSession>> {
        return dao.getCompletedSessionsFlow(limit)
    }

    suspend fun startSession(description: String): Long {
        val session = CheckInSession(
            startedAt = System.currentTimeMillis(),
            description = description
        )
        return dao.insertSession(session)
    }

    suspend fun stopSession(sessionId: Long, endTimestamp: Long) {
        val session = dao.getSessionById(sessionId)
        session?.let {
            val duration = endTimestamp - it.startedAt
            val updatedSession = it.copy(
                stoppedAt = endTimestamp,
                duration = duration
            )
            dao.updateSession(updatedSession)
        }
    }

    suspend fun getActiveSession(): CheckInSession? = dao.getActiveSession()

    suspend fun updateSessionDescription(sessionId: Long, newDescription: String) {
        val session = dao.getSessionById(sessionId)
        session?.let {
            val updatedSession = it.copy(description = newDescription)
            dao.updateSession(updatedSession)
        }
    }
}
