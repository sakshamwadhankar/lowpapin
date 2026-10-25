/*
 * Copyright (C) 2026 Lowpapin
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.lowpapin.app.core.data.repository

import com.lowpapin.app.core.data.database.dao.SessionIntentDao
import com.lowpapin.app.core.data.database.model.SessionIntentEntity
import com.lowpapin.app.core.data.database.model.toSessionIntent
import com.lowpapin.app.core.model.InterventionVariant
import com.lowpapin.app.core.model.SessionIntent
import com.lowpapin.app.core.model.SessionIntentStatus
import com.lowpapin.app.core.model.SessionPurpose
import com.lowpapin.app.core.repository.SessionIntentionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SessionIntentionRepositoryImpl @Inject constructor(
    private val sessionIntentDao: SessionIntentDao,
) : SessionIntentionRepository {

    // Intention updates come from the accessibility service, notification actions, and the UI.
    private val mutex = Mutex()

    override fun observeActiveIntent(): Flow<SessionIntent?> =
        sessionIntentDao.observeActiveIntent().map { it?.toSessionIntent() }

    override suspend fun getActiveIntent(): SessionIntent? = mutex.withLock {
        sessionIntentDao.getActiveIntent()?.toSessionIntent()
    }

    override suspend fun startIntent(
        purpose: SessionPurpose,
        intendedDurationMillis: Long,
        variant: InterventionVariant,
        startedAtMillis: Long,
    ): Long = mutex.withLock {
        // Only one intention can govern a session; closing any stale active one keeps the
        // invariant "at most one ACTIVE row" even if a previous session was never finalized.
        sessionIntentDao.getActiveIntent()?.let { stale ->
            sessionIntentDao.finishIntent(
                intentId = stale.id,
                status = SessionIntentStatus.ABANDONED,
                actualTrackedMillis = stale.actualTrackedMillis,
                endedAtMillis = startedAtMillis,
            )
        }
        sessionIntentDao.insert(
            SessionIntentEntity(
                purpose = purpose,
                intendedDurationMillis = intendedDurationMillis,
                startedAtMillis = startedAtMillis,
                variant = variant,
            ),
        )
    }

    override suspend fun extendIntent(intentId: Long, extraDurationMillis: Long) =
        sessionIntentDao.extendIntent(intentId, extraDurationMillis)

    override suspend fun finishIntent(
        intentId: Long,
        status: SessionIntentStatus,
        actualTrackedMillis: Long,
        endedAtMillis: Long,
    ) {
        sessionIntentDao.finishIntent(intentId, status, actualTrackedMillis, endedAtMillis)
    }

    override suspend fun addTrackedUsage(intentId: Long, millis: Long) {
        sessionIntentDao.addTrackedUsage(intentId, millis)
    }

    override suspend fun incrementPromptCount(intentId: Long) {
        sessionIntentDao.incrementPromptCount(intentId)
    }

    override suspend fun incrementKeepWatchingCount(intentId: Long) {
        sessionIntentDao.incrementKeepWatchingCount(intentId)
    }

    override fun observeIntentsSince(sinceMillis: Long): Flow<List<SessionIntent>> =
        sessionIntentDao.observeIntentsSince(sinceMillis).map { list -> list.map { it.toSessionIntent() } }
}
