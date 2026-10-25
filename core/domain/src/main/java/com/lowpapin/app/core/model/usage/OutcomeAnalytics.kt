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

import com.lowpapin.app.core.model.InterventionEventType
import com.lowpapin.app.core.model.InterventionVariant
import com.lowpapin.app.core.model.SessionIntent
import com.lowpapin.app.core.model.SessionIntentStatus
import java.time.LocalDate

/**
 * Pure aggregation of session segments, declared intentions, and intervention events into an
 * [OutcomeReport]. No I/O, no Android types — the inputs are plain domain snapshots.
 */
object OutcomeAnalytics {

    /**
     * @param dailyTotals one [DailyUsageTotal] per day with tracked usage, covering at least the
     *   14 days ending [today].
     * @param intents declared sessions with [SessionIntent.startedAtMillis] inside the window.
     * @param events intervention events inside the window.
     */
    fun compute(
        dailyTotals: List<DailyUsageTotal>,
        intents: List<SessionIntent>,
        events: List<InterventionEventTuple>,
        goalMillis: Long,
        today: LocalDate,
        clock: (LocalDate) -> java.time.Instant = { it.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant() },
    ): OutcomeReport {
        val last7Start = today.minusDays(6)
        val previous7Start = today.minusDays(13)

        fun usageBetween(start: LocalDate, endInclusive: LocalDate): List<Long> {
            val dates = generateSequence(start) { it.plusDays(1) }
                .takeWhile { !it.isAfter(endInclusive) }
                .toList()
            val totalsByDate = dailyTotals.associateBy { it.date }
            return dates.map { totalsByDate[it]?.totalMillis ?: 0L }
        }

        val last7 = usageBetween(last7Start, today)
        val previous7 = usageBetween(previous7Start, last7Start.minusDays(1))

        fun average(values: List<Long>): Long =
            if (values.isEmpty()) 0L else values.sum() / values.size

        val windowStartMillis = clock(previous7Start).toEpochMilli()
        val windowIntents = intents.filter { it.startedAtMillis >= windowStartMillis }

        val classifications = windowIntents.map { intent ->
            SessionClassifier.classify(
                SessionSignals(
                    declared = true,
                    purpose = intent.purpose,
                    intendedDurationMillis = intent.intendedDurationMillis,
                    actualTrackedMillis = intent.actualTrackedMillis,
                    promptCount = intent.promptCount,
                    keepWatchingCount = intent.keepWatchingCount,
                    extensionCount = intent.extensionCount,
                    startedHourOfDay = -1,
                ),
            )
        }

        val variantOutcomes = InterventionVariant.entries.map { variant ->
            val variantIntents = windowIntents.filter { it.variant == variant }
            VariantOutcome(
                variant = variant,
                sessionsStarted = variantIntents.size,
                sessionsCompletedWithinIntended = variantIntents.count {
                    it.status == SessionIntentStatus.COMPLETED
                },
            )
        }

        return OutcomeReport(
            goalMillis = goalMillis,
            daysMeetingGoal = dailyTotals.count {
                goalMillis > 0L && !it.date.isAfter(today) && !it.date.isBefore(previous7Start) && it.totalMillis <= goalMillis
            },
            daysTracked = dailyTotals.count { !it.date.isAfter(today) && !it.date.isBefore(previous7Start) },
            avgDailyUsageLast7Days = average(last7),
            avgDailyUsagePrevious7Days = average(previous7),
            intentsTotal = windowIntents.size,
            intentsCompletedWithinIntended = windowIntents.count { it.status == SessionIntentStatus.COMPLETED },
            classification = ClassificationSummary(
                intentional = classifications.count { it == SessionClassification.INTENTIONAL },
                uncertain = classifications.count { it == SessionClassification.UNCERTAIN },
                compulsive = classifications.count { it == SessionClassification.COMPULSIVE },
            ),
            variantOutcomes = variantOutcomes,
            promptsDelivered = events.count { it.type == InterventionEventType.EXIT_PROMPTED },
            promptsDismissed = events.count { it.type == InterventionEventType.KEEP_WATCHING },
        )
    }
}

/** Minimal event shape so this stays a pure function independent of persistence. */
data class InterventionEventTuple(
    val type: InterventionEventType,
)
