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
package com.lowpapin.app.core.model

import androidx.compose.runtime.Immutable

/**
 * App-level usage pattern for one tracked app, sourced from Android's UsageStatsManager.
 * Complements the accessibility-derived viewing time with how often apps are opened —
 * many short opens is a different pattern from one long session.
 */
@Immutable
data class AppUsagePattern(
    val app: BlockableApp,
    val usageMillis: Long,
    val openCount: Int,
)
