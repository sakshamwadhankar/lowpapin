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
package com.lowpapin.app.feature.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lowpapin.app.core.model.AccountabilityRequest
import com.lowpapin.app.core.model.SocialSnapshot
import com.lowpapin.app.core.repository.BlockingConfigRepository
import com.lowpapin.app.core.repository.SessionSegmentStore
import com.lowpapin.app.core.repository.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SocialViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
    private val sessionSegmentStore: SessionSegmentStore,
    private val blockingConfigRepository: BlockingConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SocialUiState(isConfigured = socialRepository.isConfigured),
    )
    val uiState: StateFlow<SocialUiState> = _uiState.asStateFlow()

    private val authenticated = socialRepository.isAuthenticated.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false,
    )

    init {
        viewModelScope.launch {
            authenticated.collect { isAuthenticated ->
                _uiState.value = _uiState.value.copy(isAuthenticated = isAuthenticated)
                if (isAuthenticated) loadSnapshot(recordCheckIn = true) else {
                    _uiState.value = _uiState.value.copy(snapshot = SocialSnapshot(), isLoading = false)
                }
            }
        }
    }

    fun onEmailChanged(value: String) {
        _uiState.value = _uiState.value.copy(email = value, error = null)
    }

    fun onPasswordChanged(value: String) {
        _uiState.value = _uiState.value.copy(password = value, error = null)
    }

    fun onUsernameChanged(value: String) {
        _uiState.value = _uiState.value.copy(username = value, error = null)
    }

    fun onFriendUsernameChanged(value: String) {
        _uiState.value = _uiState.value.copy(friendUsername = value, error = null)
    }

    fun toggleAuthMode() {
        _uiState.value = _uiState.value.copy(isSignUp = !_uiState.value.isSignUp, error = null)
    }

    fun submitAuth() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            val result = if (state.isSignUp) {
                socialRepository.signUp(state.email, state.password, state.username)
            } else {
                socialRepository.signIn(state.email, state.password)
            }
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = if (state.isSignUp) {
                        "Account created. Check your email if confirmation is enabled, then sign in."
                    } else {
                        null
                    },
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(isLoading = false, error = error.userMessage())
            }
        }
    }

    fun addFriend() {
        val username = _uiState.value.friendUsername.trim()
        if (username.isBlank()) return
        launchAction(
            action = { socialRepository.sendFriendRequest(username) },
            successMessage = "Friend request sent",
            after = { _uiState.value = _uiState.value.copy(friendUsername = "") },
        )
    }

    fun respondToFriendRequest(id: String, accept: Boolean) {
        launchAction(action = { socialRepository.respondToFriendRequest(id, accept) })
    }

    fun requestAccountability(friendId: String) {
        launchAction(
            action = { socialRepository.requestAccountabilityPermission(friendId) },
            successMessage = "Permission request sent",
        )
    }

    fun respondToAccountability(request: AccountabilityRequest, accept: Boolean) {
        launchAction(action = { socialRepository.respondToAccountabilityPermission(request.id, accept) })
    }

    fun setInstagramLimit(permissionId: String, minutes: Int) {
        val safeMinutes = minutes.coerceIn(1, 24 * 60)
        launchAction(
            action = { socialRepository.setInstagramLimit(permissionId, safeMinutes.minutes.inWholeMilliseconds) },
            successMessage = "Instagram limit synced",
        )
    }

    fun signOut() {
        launchAction(action = { socialRepository.signOut() })
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, error = null)
    }

    private fun loadSnapshot(recordCheckIn: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            socialRepository.refreshRemoteLimit()
            val result = socialRepository.loadSnapshot()
            result.onSuccess { snapshot ->
                _uiState.value = _uiState.value.copy(snapshot = snapshot, isLoading = false, error = null)
                if (recordCheckIn && snapshot.profile != null) {
                    val config = blockingConfigRepository.getConfig()
                    val goalMillis = config.settings.dailyLimitMillis.takeIf { it > 0L } ?: 30.minutes.inWholeMilliseconds
                    val todayUsageMillis = sessionSegmentStore.getTodayTotalDurationSnapshot()
                    socialRepository.recordDailyCheckIn(todayUsageMillis <= goalMillis)
                    val refreshed = socialRepository.loadSnapshot().getOrNull()
                    if (refreshed != null) _uiState.value = _uiState.value.copy(snapshot = refreshed)
                }
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(isLoading = false, error = error.userMessage())
            }
        }
    }

    private fun launchAction(action: suspend () -> Result<Unit>, successMessage: String? = null, after: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            action()
                .onSuccess {
                    after()
                    _uiState.value = _uiState.value.copy(message = successMessage, isLoading = false)
                    loadSnapshot(recordCheckIn = false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = error.userMessage())
                }
        }
    }
}

data class SocialUiState(
    val isConfigured: Boolean = false,
    val isAuthenticated: Boolean = false,
    val isLoading: Boolean = false,
    val isSignUp: Boolean = true,
    val email: String = "",
    val password: String = "",
    val username: String = "",
    val friendUsername: String = "",
    val snapshot: SocialSnapshot = SocialSnapshot(),
    val message: String? = null,
    val error: String? = null,
)

private fun Throwable.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Try again."
