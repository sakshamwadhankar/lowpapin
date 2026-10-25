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
package com.lowpapin.app.outcomes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lowpapin.app.core.model.AppUsagePattern
import com.lowpapin.app.core.model.SessionIntent
import com.lowpapin.app.core.model.usage.InterventionEventTuple
import com.lowpapin.app.core.model.usage.OutcomeAnalytics
import com.lowpapin.app.core.model.usage.OutcomeReport
import com.lowpapin.app.core.repository.InterventionLogRepository
import com.lowpapin.app.core.repository.SessionIntentionRepository
import com.lowpapin.app.core.repository.SessionSegmentStore
import com.lowpapin.app.core.repository.UsagePatternsRepository
import com.lowpapin.app.core.repository.UserSettingsStore
import com.lowpapin.app.intention.SessionIntentionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Aggregates declared intentions, intervention events, and usage history into the outcome
 * report: whether interventions improved the user's own goal, how well sessions stuck to their
 * declared intentions, the intentional/compulsive behaviour mix, and the exit-prompt experiment
 * results.
 */
@HiltViewModel
class OutcomesViewModel @Inject constructor(
    private val sessionIntentionRepository: SessionIntentionRepository,
    private val interventionLogRepository: InterventionLogRepository,
    private val sessionSegmentStore: SessionSegmentStore,
    private val userSettingsStore: UserSettingsStore,
    private val usagePatternsRepository: UsagePatternsRepository,
    private val sessionIntentionManager: SessionIntentionManager,
) : ViewModel() {

    data class UiState(
        val activeIntent: SessionIntent? = null,
        val report: OutcomeReport = OutcomeReport(),
        val patterns: List<AppUsagePattern> = emptyList(),
        val usageAccessGranted: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    init {
        val today = LocalDate.now()
        val windowStart = today.minusDays(REPORT_WINDOW_DAYS - 1L)
        val sinceMillis = windowStart.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        viewModelScope.launch {
            combine(
                sessionIntentionRepository.observeActiveIntent(),
                sessionIntentionRepository.observeIntentsSince(sinceMillis),
                interventionLogRepository.observeEventsSince(sinceMillis),
                sessionSegmentStore.getDailyUsageTotals(windowStart, today),
                userSettingsStore.getDailyGoalMillis(),
            ) { activeIntent, intents, events, dailyTotals, goalMillis ->
                val report = OutcomeAnalytics.compute(
                    dailyTotals = dailyTotals,
                    intents = intents,
                    events = events.map { InterventionEventTuple(it.type) },
                    goalMillis = goalMillis,
                    today = today,
                )
                _uiState.value.copy(activeIntent = activeIntent, report = report)
            }.collect { state -> _uiState.value = state }
        }

        refreshUsagePatterns()
    }

    fun refreshUsagePatterns() {
        viewModelScope.launch {
            val granted = usagePatternsRepository.isUsageAccessGranted()
            val patterns = if (granted) usagePatternsRepository.getTodayPatterns() else emptyList()
            _uiState.update { it.copy(patterns = patterns, usageAccessGranted = granted) }
        }
    }

    fun setDailyGoal(goalMillis: Long) {
        viewModelScope.launch { userSettingsStore.setDailyGoalMillis(goalMillis) }
    }

    fun onSessionDone() {
        sessionIntentionManager.onUserDone()
    }

    fun onExtendSession() {
        sessionIntentionManager.onExtendRequested(EXTEND_STEP_MILLIS)
    }

    companion object {
        const val REPORT_WINDOW_DAYS = 14L
        val EXTEND_STEP_MILLIS: Long = TimeUnit.MINUTES.toMillis(5)
    }
}
