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

import com.lowpapin.app.core.blocking.time.TimeProvider
import com.lowpapin.app.core.data.database.dao.InterventionEventDao
import com.lowpapin.app.core.data.database.model.InterventionEventEntity
import com.lowpapin.app.core.data.database.model.toInterventionEvent
import com.lowpapin.app.core.model.BlockableApp
import com.lowpapin.app.core.model.InterventionEvent
import com.lowpapin.app.core.model.InterventionEventType
import com.lowpapin.app.core.repository.InterventionLogRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class InterventionLogRepositoryImpl @Inject constructor(
    private val interventionEventDao: InterventionEventDao,
    private val timeProvider: TimeProvider,
) : InterventionLogRepository {

    override suspend fun log(
        type: InterventionEventType,
        app: BlockableApp?,
        intentId: Long?,
        detail: String?,
        timestampMillis: Long,
    ) {
        interventionEventDao.insert(
            InterventionEventEntity(
                timestampMillis = if (timestampMillis > 0L) timestampMillis else timeProvider.currentTimeInMillis(),
                type = type,
                app = app,
                intentId = intentId,
                detail = detail,
            ),
        )
    }

    override fun observeEventsSince(sinceMillis: Long): Flow<List<InterventionEvent>> =
        interventionEventDao.observeEventsSince(sinceMillis).map { list -> list.map { it.toInterventionEvent() } }

    override suspend fun getMostRecent(type: InterventionEventType): InterventionEvent? =
        interventionEventDao.getMostRecent(type)?.toInterventionEvent()
}
