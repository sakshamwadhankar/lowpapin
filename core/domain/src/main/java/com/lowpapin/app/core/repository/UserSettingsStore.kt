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
package com.lowpapin.app.core.repository

import com.lowpapin.app.core.model.ThemeMode
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface UserSettingsStore {

    suspend fun setTimerOverlayToggle(enabled: Boolean)
    fun getTimerOverlayEnabled(): Flow<Boolean>

    fun getTimerOverlayPositionY(): StateFlow<Int>
    suspend fun setTimerOverlayPositionY(positionY: Int)

    fun getTimerOverlayPositionX(): StateFlow<Int>
    suspend fun setTimerOverlayPositionX(positionX: Int)

    fun getWaitingForAccessibility(): Flow<Boolean>
    suspend fun setWaitingForAccessibility(waiting: Boolean)

    fun getHasSeenAccessibilityExplainer(): Flow<Boolean>
    suspend fun setHasSeenAccessibilityExplainer(seen: Boolean)

    fun getPauseUntil(): Flow<Long>
    suspend fun setPauseUntil(pauseUntil: Long)

    fun getPauseDuration(): Flow<Long>
    suspend fun setPauseDuration(durationMillis: Long)

    fun getAllowVideosSentByDm(): Flow<Boolean>
    suspend fun setAllowVideosSentByDm(checked: Boolean)

    fun getIncludeStories(): Flow<Boolean>
    suspend fun setIncludeStories(enabled: Boolean)

    fun getThemeMode(): Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    /**
     * Whether the palette should be derived from the device wallpaper. Only honoured on
     * Android 12+; older releases always use the static brand scheme.
     */
    fun getDynamicColorEnabled(): Flow<Boolean>
    suspend fun setDynamicColorEnabled(enabled: Boolean)

    fun getFirstLaunchAt(): Flow<Long>
    fun getFirstLaunchDate(): Flow<LocalDate?>

    fun getHasSeenReviewPrompt(): Flow<Boolean>
    suspend fun setHasSeenReviewPrompt(seen: Boolean)

    fun getReviewPromptAttemptCount(): Flow<Int>
    suspend fun setReviewPromptAttemptCount(count: Int)
    fun getReviewPromptLastAttemptAt(): Flow<Long>
    suspend fun setReviewPromptLastAttemptAt(timestamp: Long)

    /**
     * The user's own outcome goal for daily tracked usage. 0 when no goal is set. Interventions
     * are measured against this, never against a hard ceiling.
     */
    fun getDailyGoalMillis(): Flow<Long>
    suspend fun setDailyGoalMillis(goalMillis: Long)
}

suspend fun UserSettingsStore.setTimerOverlayPosition(positionX: Int, positionY: Int) {
    setTimerOverlayPositionX(positionX)
    setTimerOverlayPositionY(positionY)
}
