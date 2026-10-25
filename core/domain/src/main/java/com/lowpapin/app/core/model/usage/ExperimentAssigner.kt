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
package com.lowpapin.app.core.model.usage

import com.lowpapin.app.core.model.InterventionVariant
import kotlin.random.Random

/**
 * Assigns each declared session to one exit-prompt variant of the intervention experiment.
 *
 * Assignment is random per session (seeded so tests can force outcomes) and the chosen variant
 * is persisted with the session, so completion rates per variant stay comparable afterwards.
 */
object ExperimentAssigner {

    fun assign(seed: Long): InterventionVariant = if (Random(seed).nextBoolean()) {
        InterventionVariant.STATS_PROMPT
    } else {
        InterventionVariant.SIMPLE_PROMPT
    }
}
