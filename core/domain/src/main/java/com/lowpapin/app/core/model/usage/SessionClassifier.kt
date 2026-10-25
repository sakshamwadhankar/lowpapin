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

import com.lowpapin.app.core.model.SessionPurpose

/**
 * Behavioural verdict for a session: was it the deliberate consumption the user planned, or
 * compulsive browsing that drifted past their intention?
 *
 * The problem statement requires that long screen time is *not* assumed harmful: sessions
 * without a declared intention are classified [SessionClassification.UNCERTAIN] rather than
 * penalised, and deliberate long entertainment ([SessionPurpose.ENTERTAIN]) that stays within
 * its intended duration scores as intentional.
 */
enum class SessionClassification {
    INTENTIONAL,
    UNCERTAIN,
    COMPULSIVE,
}

/**
 * Observable signals for one session, expressed without Room or Android types so the classifier
 * stays a pure function.
 */
data class SessionSignals(
    val declared: Boolean,
    val purpose: SessionPurpose? = null,
    val intendedDurationMillis: Long = 0L,
    val actualTrackedMillis: Long = 0L,
    val promptCount: Int = 0,
    val keepWatchingCount: Int = 0,
    val extensionCount: Int = 0,
    /** Local hour of day the session started, or -1 when unknown. */
    val startedHourOfDay: Int = -1,
)

/**
 * Heuristic behavioural model that scores a session from 0 (compulsive) to 100 (intentional).
 *
 * Signals and weights:
 * - staying within the declared duration is the strongest positive signal
 * - overrunning is penalised proportionally to how far past the intention the session went
 * - repeatedly answering exit prompts with "keep watching" is the clearest relapse signal
 * - extending the declared duration repeatedly weakens the original intention
 * - very late-night sessions are mildly penalised (fatigue correlates with compulsion)
 */
object SessionClassifier {

    /** Undeclared sessions sit at neutral: they neither earn nor lose trust. */
    const val UNDECLARED_SCORE = 50

    fun score(signals: SessionSignals): Int {
        if (!signals.declared || signals.intendedDurationMillis <= 0L) return UNDECLARED_SCORE

        var score = 55

        val overrun = signals.actualTrackedMillis - signals.intendedDurationMillis
        score += if (overrun <= 0L) {
            25
        } else {
            val overrunFraction = overrun.toDouble() / signals.intendedDurationMillis
            -(40.0 * overrunFraction.coerceAtMost(1.5)).toInt().coerceAtMost(40)
        }

        score -= (signals.keepWatchingCount * 8).coerceAtMost(24)
        score -= (signals.extensionCount * 5).coerceAtMost(15)

        if (signals.startedHourOfDay in 0..5) score -= 15

        return score.coerceIn(0, 100)
    }

    fun classify(signals: SessionSignals): SessionClassification = when {
        score(signals) >= 70 -> SessionClassification.INTENTIONAL
        score(signals) >= 40 -> SessionClassification.UNCERTAIN
        else -> SessionClassification.COMPULSIVE
    }
}
