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
package com.lowpapin.app.core.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.lowpapin.app.core.data.database.model.SessionIntentEntity
import com.lowpapin.app.core.model.SessionIntentStatus
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SessionIntentDao : BaseDao<SessionIntentEntity> {

    @Query("SELECT * FROM session_intents WHERE status = 'ACTIVE' ORDER BY startedAtMillis DESC LIMIT 1")
    abstract fun observeActiveIntent(): Flow<SessionIntentEntity?>

    @Query("SELECT * FROM session_intents WHERE status = 'ACTIVE' ORDER BY startedAtMillis DESC LIMIT 1")
    abstract suspend fun getActiveIntent(): SessionIntentEntity?

    @Query("SELECT * FROM session_intents WHERE startedAtMillis >= :sinceMillis ORDER BY startedAtMillis ASC")
    abstract fun observeIntentsSince(sinceMillis: Long): Flow<List<SessionIntentEntity>>

    @Query("UPDATE session_intents SET intendedDurationMillis = intendedDurationMillis + :extraMillis, extensionCount = extensionCount + 1 WHERE id = :intentId")
    abstract suspend fun extendIntent(intentId: Long, extraMillis: Long)

    @Query(
        """
        UPDATE session_intents
        SET status = :status, actualTrackedMillis = :actualTrackedMillis, endedAtMillis = :endedAtMillis
        WHERE id = :intentId
        """,
    )
    abstract suspend fun finishIntent(intentId: Long, status: SessionIntentStatus, actualTrackedMillis: Long, endedAtMillis: Long)

    @Query("UPDATE session_intents SET actualTrackedMillis = actualTrackedMillis + :millis WHERE id = :intentId")
    abstract suspend fun addTrackedUsage(intentId: Long, millis: Long)

    @Query("UPDATE session_intents SET promptCount = promptCount + 1 WHERE id = :intentId")
    abstract suspend fun incrementPromptCount(intentId: Long)

    @Query("UPDATE session_intents SET keepWatchingCount = keepWatchingCount + 1 WHERE id = :intentId")
    abstract suspend fun incrementKeepWatchingCount(intentId: Long)
}
