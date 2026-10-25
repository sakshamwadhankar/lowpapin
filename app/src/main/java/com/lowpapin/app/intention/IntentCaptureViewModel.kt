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
package com.lowpapin.app.intention

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lowpapin.app.core.model.InterventionEventType
import com.lowpapin.app.core.model.SessionPurpose
import com.lowpapin.app.core.model.usage.ExperimentAssigner
import com.lowpapin.app.core.repository.InterventionLogRepository
import com.lowpapin.app.core.repository.SessionIntentionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Captures the user's declared purpose and intended duration before a session starts, and
 * assigns the session to an exit-prompt variant of the intervention experiment.
 */
@HiltViewModel
class IntentCaptureViewModel @Inject constructor(
    private val sessionIntentionRepository: SessionIntentionRepository,
    private val interventionLogRepository: InterventionLogRepository,
) : ViewModel() {

    fun startSession(purpose: SessionPurpose, intendedDurationMillis: Long, onStarted: () -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val variant = ExperimentAssigner.assign(seed = now)
            val intentId = sessionIntentionRepository.startIntent(
                purpose = purpose,
                intendedDurationMillis = intendedDurationMillis,
                variant = variant,
                startedAtMillis = now,
            )
            interventionLogRepository.log(
                type = InterventionEventType.INTENT_CAPTURED,
                intentId = intentId,
                detail = "${purpose.name} / $intendedDurationMillis ms / ${variant.name}",
            )
            onStarted()
        }
    }

    fun dismiss() {
        viewModelScope.launch {
            interventionLogRepository.log(type = InterventionEventType.INTENT_DISMISSED)
        }
    }
}
