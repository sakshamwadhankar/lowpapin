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
package com.lowpapin.app.core.repository

import com.lowpapin.app.core.model.BlockableApp
import com.lowpapin.app.core.model.InterventionEvent
import com.lowpapin.app.core.model.InterventionEventType
import kotlinx.coroutines.flow.Flow

/**
 * Append-only log of interventions and the user's responses to them.
 */
interface InterventionLogRepository {

    suspend fun log(
        type: InterventionEventType,
        app: BlockableApp? = null,
        intentId: Long? = null,
        detail: String? = null,
        timestampMillis: Long = 0L,
    )

    fun observeEventsSince(sinceMillis: Long): Flow<List<InterventionEvent>>

    /** Most recent event of [type], or null when none was ever logged. Used for prompt throttling. */
    suspend fun getMostRecent(type: InterventionEventType): InterventionEvent?
}
