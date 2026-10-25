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
 * Every intervention the engine delivers and every user response to one. This event log is the
 * data source for relapse detection (repeated keep-watching responses) and for measuring whether
 * interventions change outcomes.
 */
enum class InterventionEventType {

    /** A tracked app opened with no active intention; the user was asked to declare one. */
    INTENT_REQUESTED,

    /** The user declared a purpose and duration. */
    INTENT_CAPTURED,

    /** The user closed the capture prompt without declaring an intention. */
    INTENT_DISMISSED,

    /** An exit prompt was delivered (at an overrun, or at a natural stopping point). */
    EXIT_PROMPTED,

    /** The user answered an exit prompt with "I'm done". */
    USER_DONE,

    /** The user answered an exit prompt by extending their intended duration. */
    EXTENDED,

    /** The user answered an exit prompt with "keep watching" — a relapse signal. */
    KEEP_WATCHING,

    /** A viewing session ended with no further tracked usage. */
    SESSION_ENDED,

    /** A configured blocking mode enforced a block (hard block, kept for mode users). */
    HARD_BLOCK,
}

@Immutable
data class InterventionEvent(
    val id: Long,
    val timestampMillis: Long,
    val type: InterventionEventType,
    val app: BlockableApp? = null,
    val intentId: Long? = null,
    val detail: String? = null,
)
