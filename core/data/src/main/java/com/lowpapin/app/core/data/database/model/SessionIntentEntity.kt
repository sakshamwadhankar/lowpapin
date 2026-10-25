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
import com.lowpapin.app.core.model.InterventionVariant
import com.lowpapin.app.core.model.SessionIntent
import com.lowpapin.app.core.model.SessionIntentStatus
import com.lowpapin.app.core.model.SessionPurpose

/**
 * A user's declared intention for one browsing session: purpose, planned duration, and the
 * tracked usage and intervention responses accumulated against that declaration.
 */
@Entity(
    tableName = "session_intents",
    indices = [
        Index(value = ["status"]),
        Index(value = ["startedAtMillis"]),
    ],
)
@Immutable
data class SessionIntentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val purpose: SessionPurpose,
    val intendedDurationMillis: Long,
    val startedAtMillis: Long,
    @ColumnInfo(defaultValue = "0") val endedAtMillis: Long = 0L,
    @ColumnInfo(defaultValue = "ACTIVE") val status: SessionIntentStatus = SessionIntentStatus.ACTIVE,
    @ColumnInfo(defaultValue = "0") val actualTrackedMillis: Long = 0L,
    @ColumnInfo(defaultValue = "0") val promptCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val keepWatchingCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val extensionCount: Int = 0,
    val variant: InterventionVariant,
)

fun SessionIntentEntity.toSessionIntent(): SessionIntent = SessionIntent(
    id = id,
    purpose = purpose,
    intendedDurationMillis = intendedDurationMillis,
    startedAtMillis = startedAtMillis,
    endedAtMillis = endedAtMillis,
    status = status,
    actualTrackedMillis = actualTrackedMillis,
    promptCount = promptCount,
    keepWatchingCount = keepWatchingCount,
    extensionCount = extensionCount,
    variant = variant,
)
