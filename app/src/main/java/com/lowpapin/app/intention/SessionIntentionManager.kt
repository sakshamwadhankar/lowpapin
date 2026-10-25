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

import com.lowpapin.app.core.model.BlockableApp
import com.lowpapin.app.core.model.InterventionEventType
import com.lowpapin.app.core.model.SessionIntent
import com.lowpapin.app.core.model.SessionIntentStatus
import com.lowpapin.app.core.repository.InterventionLogRepository
import com.lowpapin.app.core.repository.SessionIntentionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The infinite-scroll intervention engine.
 *
 * Orchestrates the intention lifecycle against actual usage:
 * 1. When a tracked app opens without a declared intention, the user is asked to declare one.
 * 2. Tracked usage is accumulated against the active intention.
 * 3. When usage diverges from the declared duration (an overrun), a non-blocking exit prompt is
 *    delivered — preferably at a natural stopping point (the start of a new video) rather than
 *    mid-video.
 * 4. Every intervention and response is logged, so repeated "keep watching" answers surface as
 *    relapse signals and outcome measurement has data to work with.
 *
 * Nothing here blocks access: configured blocking modes keep working independently as the
 * user's own fallback.
 */
@Singleton
class SessionIntentionManager @Inject constructor(
    private val sessionIntentionRepository: SessionIntentionRepository,
    private val interventionLogRepository: InterventionLogRepository,
    private val notifier: InterventionNotifier,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _activeIntent = MutableStateFlow<SessionIntent?>(null)
    val activeIntent: StateFlow<SessionIntent?> = _activeIntent

    private var lastIntentRequestAtMillis = 0L
    private var lastPromptAtMillis = 0L
    private var snoozeUntilMillis = 0L
    private var overrunDetectedAtMillis = 0L
    private var finalizeJob: Job? = null

    init {
        scope.launch {
            sessionIntentionRepository.observeActiveIntent().collect { _activeIntent.value = it }
        }
    }

    /** A tracked app came to the foreground; ask for an intention when none is active. */
    fun onTrackedAppOpened(app: BlockableApp) {
        finalizeJob?.cancel()
        val active = _activeIntent.value
        if (active != null) return

        val now = System.currentTimeMillis()
        if (now - lastIntentRequestAtMillis < INTENT_REQUEST_THROTTLE_MILLIS) return
        lastIntentRequestAtMillis = now
        scope.launch {
            Timber.i("No active intention; requesting one for %s", app)
            interventionLogRepository.log(InterventionEventType.INTENT_REQUESTED, app = app)
            notifier.notifyIntentRequest(app)
        }
    }

    /** A tracked app left the foreground; the intention session ends after an idle period. */
    fun onTrackedAppClosed() {
        finalizeJob?.cancel()
        finalizeJob = scope.launch {
            delay(SESSION_IDLE_FINALIZE_DELAY_MILLIS)
            finalizeActiveIntent()
        }
    }

    /** Viewing time was recorded; add it to the active intention's actual usage. */
    fun onUsageRecorded(millis: Long, app: BlockableApp) {
        val active = _activeIntent.value ?: return
        finalizeJob?.cancel()
        scope.launch {
            sessionIntentionRepository.addTrackedUsage(active.id, millis)
        }
    }

    /**
     * A new video/screen started inside a tracked app — a natural stopping point. If the session
     * is already overrunning, this is the context-aware moment to offer an exit.
     */
    fun onContentSessionStarted(app: BlockableApp) {
        val active = _activeIntent.value ?: return
        if (!active.isOverrunning) return
        scope.launch {
            if (shouldPromptNow()) deliverExitPrompt(active, app)
        }
    }

    /** Periodic check while content is visible. Fires a fallback prompt if no natural break came. */
    fun evaluateOverrun(app: BlockableApp) {
        val active = _activeIntent.value ?: return
        if (!active.isOverrunning) {
            overrunDetectedAtMillis = 0L
            return
        }
        if (overrunDetectedAtMillis == 0L) overrunDetectedAtMillis = System.currentTimeMillis()
        val fallbackDue = System.currentTimeMillis() - overrunDetectedAtMillis >= PROMPT_FALLBACK_DELAY_MILLIS
        if (!fallbackDue) return
        scope.launch {
            if (shouldPromptNow()) deliverExitPrompt(active, app)
        }
    }

    /** Configured blocking modes still enforce; the log keeps outcome measurement honest about it. */
    fun onHardBlock(app: BlockableApp) {
        scope.launch {
            interventionLogRepository.log(InterventionEventType.HARD_BLOCK, app = app, intentId = _activeIntent.value?.id)
        }
    }

    /** User answered "I'm done" — end the session as completed. */
    fun onUserDone() {
        scope.launch {
            finalizeActiveIntent(userDone = true)
        }
    }

    /** User answered "+5 minutes" — extend the declared duration instead of fighting the user. */
    fun onExtendRequested(extraMillis: Long) {
        val active = _activeIntent.value ?: return
        scope.launch {
            sessionIntentionRepository.extendIntent(active.id, extraMillis)
            interventionLogRepository.log(
                InterventionEventType.EXTENDED,
                intentId = active.id,
                detail = "+$extraMillis",
            )
            snoozeUntilMillis = 0L
        }
    }

    /** User answered "keep watching" — respect it, log the relapse signal, and back off for a while. */
    fun onKeepWatching() {
        val active = _activeIntent.value ?: return
        scope.launch {
            sessionIntentionRepository.incrementKeepWatchingCount(active.id)
            interventionLogRepository.log(InterventionEventType.KEEP_WATCHING, intentId = active.id)
            snoozeUntilMillis = System.currentTimeMillis() + PROMPT_THROTTLE_MILLIS
        }
    }

    /** The user closed the capture prompt without declaring an intention. */
    fun onIntentRequestDismissed() {
        scope.launch {
            interventionLogRepository.log(InterventionEventType.INTENT_DISMISSED)
        }
    }

    private suspend fun shouldPromptNow(): Boolean {
        val now = System.currentTimeMillis()
        if (now < snoozeUntilMillis) return false
        if (now - lastPromptAtMillis < PROMPT_THROTTLE_MILLIS) return false
        return true
    }

    private suspend fun deliverExitPrompt(intent: SessionIntent, app: BlockableApp) {
        Timber.i("Session overrunning intention; delivering exit prompt (app=%s)", app)
        lastPromptAtMillis = System.currentTimeMillis()
        sessionIntentionRepository.incrementPromptCount(intent.id)
        interventionLogRepository.log(InterventionEventType.EXIT_PROMPTED, app = app, intentId = intent.id)
        notifier.notifyExitPrompt(intent, app)
    }

    private suspend fun finalizeActiveIntent(userDone: Boolean = false) {
        val active = _activeIntent.value ?: return
        val status = if (userDone || !active.isOverrunning) SessionIntentStatus.COMPLETED else SessionIntentStatus.OVERRUN
        Timber.i("Finalizing intention %d as %s (actual=%d, intended=%d)", active.id, status, active.actualTrackedMillis, active.intendedDurationMillis)
        sessionIntentionRepository.finishIntent(
            intentId = active.id,
            status = status,
            actualTrackedMillis = active.actualTrackedMillis,
            endedAtMillis = System.currentTimeMillis(),
        )
        interventionLogRepository.log(
            InterventionEventType.SESSION_ENDED,
            intentId = active.id,
            detail = status.name,
        )
        notifier.cancelAll()
        overrunDetectedAtMillis = 0L
    }

    private companion object {
        const val INTENT_REQUEST_THROTTLE_MILLIS = 3 * 60_000L
        const val PROMPT_THROTTLE_MILLIS = 2 * 60_000L
        const val SESSION_IDLE_FINALIZE_DELAY_MILLIS = 10 * 60_000L

        /** If no natural break (new video) occurs, prompt anyway after this much overrun. */
        const val PROMPT_FALLBACK_DELAY_MILLIS = 60_000L
    }
}
