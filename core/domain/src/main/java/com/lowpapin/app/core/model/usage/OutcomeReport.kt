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

/** How many sessions of a window fell into each behavioural class. */
data class ClassificationSummary(
    val intentional: Int = 0,
    val uncertain: Int = 0,
    val compulsive: Int = 0,
) {
    val total: Int get() = intentional + uncertain + compulsive

    fun fractionOf(classification: SessionClassification): Float {
        if (total == 0) return 0f
        val count = when (classification) {
            SessionClassification.INTENTIONAL -> intentional
            SessionClassification.UNCERTAIN -> uncertain
            SessionClassification.COMPULSIVE -> compulsive
        }
        return count.toFloat() / total
    }
}

/** Completion rate for one experiment variant, used to compare prompt phrasings. */
data class VariantOutcome(
    val variant: InterventionVariant,
    val sessionsStarted: Int,
    val sessionsCompletedWithinIntended: Int,
) {
    val completionRate: Float
        get() = if (sessionsStarted == 0) 0f else sessionsCompletedWithinIntended.toFloat() / sessionsStarted
}

/**
 * Aggregated answer to "did the interventions help?" — everything here is measured against
 * user-declared intentions and the user's own outcome goal, not against raw screen time.
 */
data class OutcomeReport(
    /** The user-defined daily tracked-usage goal; 0 when the user has not set one. */
    val goalMillis: Long = 0L,

    /** Days in the evaluated window whose tracked usage stayed at or under [goalMillis]. */
    val daysMeetingGoal: Int = 0,

    /** Days in the evaluated window with any tracked usage. */
    val daysTracked: Int = 0,

    val avgDailyUsageLast7Days: Long = 0L,

    val avgDailyUsagePrevious7Days: Long = 0L,

    /** Declared sessions in the window. */
    val intentsTotal: Int = 0,

    /** Declared sessions that ended without overrun. */
    val intentsCompletedWithinIntended: Int = 0,

    val classification: ClassificationSummary = ClassificationSummary(),

    val variantOutcomes: List<VariantOutcome> = emptyList(),

    /** Exit prompts delivered in the window. */
    val promptsDelivered: Int = 0,

    /** Exit prompts answered with "keep watching" — repeated ones are the relapse signal. */
    val promptsDismissed: Int = 0,
) {
    val goalAttainmentFraction: Float
        get() = if (daysTracked == 0) 0f else daysMeetingGoal.toFloat() / daysTracked

    val intentAdherenceFraction: Float
        get() = if (intentsTotal == 0) 0f else intentsCompletedWithinIntended.toFloat() / intentsTotal

    val weeklyTrendMillis: Long
        get() = avgDailyUsageLast7Days - avgDailyUsagePrevious7Days
}
