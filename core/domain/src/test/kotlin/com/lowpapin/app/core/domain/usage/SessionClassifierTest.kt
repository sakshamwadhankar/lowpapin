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

import com.lowpapin.app.core.model.SessionPurpose
import com.lowpapin.app.core.model.usage.SessionClassification
import com.lowpapin.app.core.model.usage.SessionClassifier
import com.lowpapin.app.core.model.usage.SessionSignals
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionClassifierTest {

    private fun signals(
        declared: Boolean = true,
        intendedMinutes: Long = 15L,
        actualMinutes: Long = 15L,
        keepWatching: Int = 0,
        extensions: Int = 0,
        startHour: Int = 14,
    ) = SessionSignals(
        declared = declared,
        purpose = SessionPurpose.ENTERTAIN,
        intendedDurationMillis = TimeUnit.MINUTES.toMillis(intendedMinutes),
        actualTrackedMillis = TimeUnit.MINUTES.toMillis(actualMinutes),
        keepWatchingCount = keepWatching,
        extensionCount = extensions,
        startedHourOfDay = startHour,
    )

    @Test
    fun `undeclared sessions are never labelled compulsive`() {
        // The PS requires not assuming all long screen time is harmful: without a declaration
        // there is nothing to diverge from.
        assertEquals(SessionClassification.UNCERTAIN, SessionClassifier.classify(signals(declared = false)))
        assertEquals(SessionClassifier.UNDECLARED_SCORE, SessionClassifier.score(signals(declared = false)))
    }

    @Test
    fun `session within its declared duration is intentional`() {
        assertEquals(SessionClassification.INTENTIONAL, SessionClassifier.classify(signals(actualMinutes = 12L)))
    }

    @Test
    fun `long deliberate entertainment within plan is still intentional`() {
        assertEquals(
            SessionClassification.INTENTIONAL,
            SessionClassifier.classify(signals(intendedMinutes = 90L, actualMinutes = 85L)),
        )
    }

    @Test
    fun `slight overrun stays uncertain rather than punishing the user`() {
        assertEquals(
            SessionClassification.UNCERTAIN,
            SessionClassifier.classify(signals(intendedMinutes = 15L, actualMinutes = 17L)),
        )
    }

    @Test
    fun `heavy overrun with repeated keep-watching is compulsive`() {
        assertEquals(
            SessionClassification.COMPULSIVE,
            SessionClassifier.classify(
                signals(intendedMinutes = 15L, actualMinutes = 30L, keepWatching = 2),
            ),
        )
    }

    @Test
    fun `late night start lowers the score`() {
        val dayScore = SessionClassifier.score(signals(startHour = 14))
        val nightScore = SessionClassifier.score(signals(startHour = 2))
        assertEquals(dayScore - 15, nightScore)
    }

    @Test
    fun `repeated extensions weaken the declared intention`() {
        val noExtensions = SessionClassifier.score(signals(extensions = 0))
        val manyExtensions = SessionClassifier.score(signals(extensions = 5))
        assertEquals(noExtensions - 15, manyExtensions)
    }
}
