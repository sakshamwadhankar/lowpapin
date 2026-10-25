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
import com.lowpapin.app.core.data.database.model.InterventionEventEntity
import com.lowpapin.app.core.model.InterventionEventType
import kotlinx.coroutines.flow.Flow

@Dao
abstract class InterventionEventDao : BaseDao<InterventionEventEntity> {

    @Query("SELECT * FROM intervention_events WHERE timestampMillis >= :sinceMillis ORDER BY timestampMillis ASC")
    abstract fun observeEventsSince(sinceMillis: Long): Flow<List<InterventionEventEntity>>

    @Query("SELECT * FROM intervention_events WHERE type = :type ORDER BY timestampMillis DESC LIMIT 1")
    abstract suspend fun getMostRecent(type: InterventionEventType): InterventionEventEntity?
}
