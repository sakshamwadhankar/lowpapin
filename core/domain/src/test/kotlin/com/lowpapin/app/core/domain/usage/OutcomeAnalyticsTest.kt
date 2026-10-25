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
package com.lowpapin.app.core.domain.usage

import com.lowpapin.app.core.model.InterventionEventType
import com.lowpapin.app.core.model.InterventionVariant
import com.lowpapin.app.core.model.SessionIntent
import com.lowpapin.app.core.model.SessionIntentStatus
import com.lowpapin.app.core.model.SessionPurpose
import com.lowpapin.app.core.model.usage.DailyUsageTotal
import com.lowpapin.app.core.model.usage.InterventionEventTuple
import com.lowpapin.app.core.model.usage.OutcomeAnalytics
import com.lowpapin.app.core.model.usage.SessionClassification
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class OutcomeAnalyticsTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 25)
    private val zone: ZoneId = ZoneId.systemDefault()

    private fun millisAt(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun intent(
        startedAt: Long,
        status: SessionIntentStatus,
        variant: InterventionVariant,
        actualMinutes: Long,
    ) = SessionIntent(
        id = startedAt,
        purpose = SessionPurpose.ENTERTAIN,
        intendedDurationMillis = TimeUnit.MINUTES.toMillis(15),
        startedAtMillis = startedAt,
        status = status,
        actualTrackedMillis = TimeUnit.MINUTES.toMillis(actualMinutes),
        variant = variant,
    )

    private fun dailyTotals(vararg entries: Pair<LocalDate, Long>): List<DailyUsageTotal> =
        entries.map { DailyUsageTotal(it.first, it.second) }

    @Test
    fun `goal attainment counts only days inside the 14 day window under the goal`() {
        val inWindowUnder = today.minusDays(2)
        val inWindowOver = today.minusDays(3)
        val outsideWindow = today.minusDays(20)
        val goal = TimeUnit.MINUTES.toMillis(30)

        val report = OutcomeAnalytics.compute(
            dailyTotals = dailyTotals(
                inWindowUnder to TimeUnit.MINUTES.toMillis(10),
                inWindowOver to TimeUnit.MINUTES.toMillis(90),
                outsideWindow to TimeUnit.MINUTES.toMillis(1),
            ),
            intents = emptyList(),
            events = emptyList(),
            goalMillis = goal,
            today = today,
            clock = { it.atStartOfDay(zone).toInstant() },
        )

        assertEquals(1, report.daysMeetingGoal)
        // Only days with recorded usage count as tracked days.
        assertEquals(2, report.daysTracked)
    }

    @Test
    fun `weekly trend compares last 7 days against previous 7 days`() {
        val totals = (0L..13L).map { offset ->
            val date = today.minusDays(offset)
            val usage = if (offset <= 6L) 20L else 60L
            DailyUsageTotal(date, TimeUnit.MINUTES.toMillis(usage))
        }

        val report = OutcomeAnalytics.compute(
            dailyTotals = totals,
            intents = emptyList(),
            events = emptyList(),
            goalMillis = 0L,
            today = today,
            clock = { it.atStartOfDay(zone).toInstant() },
        )

        assertEquals(TimeUnit.MINUTES.toMillis(20) - TimeUnit.MINUTES.toMillis(60), report.weeklyTrendMillis)
    }

    @Test
    fun `adherence and classification come from declared sessions in the window`() {
        val recent = millisAt(today.minusDays(1))
        val stale = millisAt(today.minusDays(30))

        val report = OutcomeAnalytics.compute(
            dailyTotals = emptyList(),
            intents = listOf(
                intent(recent, SessionIntentStatus.COMPLETED, InterventionVariant.SIMPLE_PROMPT, actualMinutes = 14),
                intent(recent + 1, SessionIntentStatus.OVERRUN, InterventionVariant.STATS_PROMPT, actualMinutes = 45),
                intent(stale, SessionIntentStatus.COMPLETED, InterventionVariant.SIMPLE_PROMPT, actualMinutes = 14),
            ),
            events = listOf(
                InterventionEventTuple(InterventionEventType.EXIT_PROMPTED),
                InterventionEventTuple(InterventionEventType.EXIT_PROMPTED),
                InterventionEventTuple(InterventionEventType.KEEP_WATCHING),
            ),
            goalMillis = 0L,
            today = today,
            clock = { d: LocalDate -> Instant.ofEpochMilli(millisAt(d)) },
        )

        assertEquals(2, report.intentsTotal)
        assertEquals(1, report.intentsCompletedWithinIntended)
        assertEquals(1, report.classification.intentional)
        assertEquals(1, report.classification.compulsive)
        assertEquals(0, report.classification.uncertain)
        assertEquals(2, report.promptsDelivered)
        assertEquals(1, report.promptsDismissed)
    }

    @Test
    fun `experiment variants report separate completion rates`() {
        val recent = millisAt(today.minusDays(1))
        val report = OutcomeAnalytics.compute(
            dailyTotals = emptyList(),
            intents = listOf(
                intent(recent, SessionIntentStatus.COMPLETED, InterventionVariant.SIMPLE_PROMPT, actualMinutes = 10),
                intent(recent + 1, SessionIntentStatus.OVERRUN, InterventionVariant.SIMPLE_PROMPT, actualMinutes = 60),
                intent(recent + 2, SessionIntentStatus.COMPLETED, InterventionVariant.STATS_PROMPT, actualMinutes = 12),
            ),
            events = emptyList(),
            goalMillis = 0L,
            today = today,
            clock = { d: LocalDate -> Instant.ofEpochMilli(millisAt(d)) },
        )

        val simple = report.variantOutcomes.first { it.variant == InterventionVariant.SIMPLE_PROMPT }
        val stats = report.variantOutcomes.first { it.variant == InterventionVariant.STATS_PROMPT }

        assertEquals(2, simple.sessionsStarted)
        assertEquals(1, simple.sessionsCompletedWithinIntended)
        assertEquals(0.5f, simple.completionRate)
        assertEquals(1, stats.sessionsStarted)
        assertEquals(1.0f, stats.completionRate)
    }
}
