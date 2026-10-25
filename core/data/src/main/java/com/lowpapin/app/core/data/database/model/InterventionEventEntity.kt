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
package com.lowpapin.app.core.data.database.model

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lowpapin.app.core.model.BlockableApp
import com.lowpapin.app.core.model.InterventionEvent
import com.lowpapin.app.core.model.InterventionEventType

/**
 * One delivered intervention or one user response to an intervention.
 */
@Entity(
    tableName = "intervention_events",
    indices = [
        Index(value = ["type", "timestampMillis"]),
        Index(value = ["intentId"]),
    ],
)
@Immutable
data class InterventionEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestampMillis: Long,
    val type: InterventionEventType,
    val app: BlockableApp? = null,
    val intentId: Long? = null,
    val detail: String? = null,
)

fun InterventionEventEntity.toInterventionEvent(): InterventionEvent = InterventionEvent(
    id = id,
    timestampMillis = timestampMillis,
    type = type,
    app = app,
    intentId = intentId,
    detail = detail,
)
