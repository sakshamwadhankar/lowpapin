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
 * What the user says they opened a tracked app for. Declared before a session starts.
 */
enum class SessionPurpose {
    /** Checking messages or replies from specific people. */
    COMMUNICATE,

    /** Learning something specific (a tutorial, a how-to, research). */
    LEARN,

    /** Deliberate entertainment: "I want to relax and watch for a while". */
    ENTERTAIN,

    /** Posting or updating something of their own. */
    CREATE,

    /** Anything else the user did not bucket. */
    OTHER,
}

/**
 * Lifecycle of a declared session intent.
 */
enum class SessionIntentStatus {

    /** Declared and currently governing a session. */
    ACTIVE,

    /** The user ended the session themselves (or it ended within the intended duration). */
    COMPLETED,

    /** The session consumed more tracked time than the user intended. */
    OVERRUN,

    /** The user closed the capture prompt and never started the session. */
    ABANDONED,
}

/**
 * Which exit-prompt style a session was assigned to by the intervention experiment.
 *
 * Both variants deliver the same non-blocking prompt; [STATS_PROMPT] additionally shows the
 * planned vs elapsed numbers so the experiment can compare which phrasing users act on.
 */
enum class InterventionVariant {
    SIMPLE_PROMPT,
    STATS_PROMPT,
}

/**
 * A user's declared intention for a browsing session: what they came for and how long they
 * meant to stay. Session overruns, exit prompts, and outcome measurements are all evaluated
 * against this declaration rather than against a generic time limit.
 */
@Immutable
data class SessionIntent(
    val id: Long,
    val purpose: SessionPurpose,
    val intendedDurationMillis: Long,
    val startedAtMillis: Long,
    val endedAtMillis: Long = 0L,
    val status: SessionIntentStatus = SessionIntentStatus.ACTIVE,
    val actualTrackedMillis: Long = 0L,
    val promptCount: Int = 0,
    val keepWatchingCount: Int = 0,
    val extensionCount: Int = 0,
    val variant: InterventionVariant = InterventionVariant.SIMPLE_PROMPT,
) {
    /** Milliseconds of tracked usage beyond what the user intended; negative while within plan. */
    val overrunMillis: Long
        get() = (actualTrackedMillis - intendedDurationMillis).coerceAtLeast(0L)

    val isOverrunning: Boolean
        get() = actualTrackedMillis > intendedDurationMillis
}
