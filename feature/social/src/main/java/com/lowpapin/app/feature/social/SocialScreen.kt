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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lowpapin.app.core.model.AccountabilityPermissionStatus
import com.lowpapin.app.core.model.AccountabilityRequest
import com.lowpapin.app.core.model.SocialFriend
import com.lowpapin.app.core.model.SocialProfile

@Composable
fun SocialScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier, viewModel: SocialViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SocialScreenContent(
        modifier = modifier,
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onEmailChanged = viewModel::onEmailChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onUsernameChanged = viewModel::onUsernameChanged,
        onFriendUsernameChanged = viewModel::onFriendUsernameChanged,
        onSubmitAuth = viewModel::submitAuth,
        onToggleAuthMode = viewModel::toggleAuthMode,
        onAddFriend = viewModel::addFriend,
        onRespondToFriendRequest = viewModel::respondToFriendRequest,
        onRequestAccountability = viewModel::requestAccountability,
        onRespondToAccountability = viewModel::respondToAccountability,
        onSetInstagramLimit = viewModel::setInstagramLimit,
        onSignOut = viewModel::signOut,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SocialScreenContent(
    uiState: SocialUiState,
    onNavigateBack: () -> Unit,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onFriendUsernameChanged: (String) -> Unit,
    onSubmitAuth: () -> Unit,
    onToggleAuthMode: () -> Unit,
    onAddFriend: () -> Unit,
    onRespondToFriendRequest: (String, Boolean) -> Unit,
    onRequestAccountability: (String) -> Unit,
    onRespondToAccountability: (AccountabilityRequest, Boolean) -> Unit,
    onSetInstagramLimit: (String, Int) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.social_title)) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text(stringResource(R.string.social_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!uiState.isConfigured) {
                SetupCard()
            } else if (!uiState.isAuthenticated) {
                AuthCard(
                    uiState = uiState,
                    onEmailChanged = onEmailChanged,
                    onPasswordChanged = onPasswordChanged,
                    onUsernameChanged = onUsernameChanged,
                    onSubmitAuth = onSubmitAuth,
                    onToggleAuthMode = onToggleAuthMode,
                )
            } else {
                SocialContent(
                    uiState = uiState,
                    onFriendUsernameChanged = onFriendUsernameChanged,
                    onAddFriend = onAddFriend,
                    onRespondToFriendRequest = onRespondToFriendRequest,
                    onRequestAccountability = onRequestAccountability,
                    onRespondToAccountability = onRespondToAccountability,
                    onSetInstagramLimit = onSetInstagramLimit,
                    onSignOut = onSignOut,
                )
            }

            uiState.error?.let {
                Text(
                    text = stringResource(R.string.social_error, it),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            uiState.message?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun SetupCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.social_not_configured_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.social_not_configured_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AuthCard(
    uiState: SocialUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onSubmitAuth: () -> Unit,
    onToggleAuthMode: () -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = if (uiState.isSignUp) stringResource(R.string.social_sign_up) else stringResource(R.string.social_sign_in),
                style = MaterialTheme.typography.headlineSmall,
            )
            if (uiState.isSignUp) {
                OutlinedTextField(
                    value = uiState.username,
                    onValueChange = onUsernameChanged,
                    label = { Text(stringResource(R.string.social_username)) },
                    placeholder = { Text(stringResource(R.string.social_username_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = uiState.email,
                onValueChange = onEmailChanged,
                label = { Text(stringResource(R.string.social_email)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.password,
                onValueChange = onPasswordChanged,
                label = { Text(stringResource(R.string.social_password)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onSubmitAuth,
                enabled = uiState.email.isNotBlank() && uiState.password.isNotBlank() &&
                    (!uiState.isSignUp || uiState.username.isNotBlank()),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.social_continue))
            }
            TextButton(onClick = onToggleAuthMode, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (uiState.isSignUp) {
                        stringResource(R.string.social_switch_to_sign_in)
                    } else {
                        stringResource(R.string.social_switch_to_sign_up)
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SocialContent(
    uiState: SocialUiState,
    onFriendUsernameChanged: (String) -> Unit,
    onAddFriend: () -> Unit,
    onRespondToFriendRequest: (String, Boolean) -> Unit,
    onRequestAccountability: (String) -> Unit,
    onRespondToAccountability: (AccountabilityRequest, Boolean) -> Unit,
    onSetInstagramLimit: (String, Int) -> Unit,
    onSignOut: () -> Unit,
) {
    uiState.snapshot.profile?.let { StreakCard(it) }

    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.social_add_friend), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.friendUsername,
                    onValueChange = onFriendUsernameChanged,
                    label = { Text(stringResource(R.string.social_friend_username)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onAddFriend, enabled = uiState.friendUsername.isNotBlank()) {
                    Text(stringResource(R.string.social_send_request))
                }
            }
        }
    }

    if (uiState.snapshot.friendRequests.isNotEmpty()) {
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.social_friend_requests), style = MaterialTheme.typography.titleMedium)
                uiState.snapshot.friendRequests.forEach { request ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(request.requesterUsername, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onRespondToFriendRequest(request.id, false) }) {
                            Text(stringResource(R.string.social_decline))
                        }
                        Button(onClick = { onRespondToFriendRequest(request.id, true) }) {
                            Text(stringResource(R.string.social_accept))
                        }
                    }
                }
            }
        }
    }

    val incomingPermissionRequests = uiState.snapshot.accountabilityRequests.filter {
        it.status == AccountabilityPermissionStatus.PENDING && it.ownerId == uiState.snapshot.profile?.id
    }
    if (incomingPermissionRequests.isNotEmpty()) {
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.social_permission_requests), style = MaterialTheme.typography.titleMedium)
                incomingPermissionRequests.forEach { request ->
                    Text(stringResource(R.string.social_permission_request_body, request.partnerUsername))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { onRespondToAccountability(request, false) }) {
                            Text(stringResource(R.string.social_decline))
                        }
                        Button(onClick = { onRespondToAccountability(request, true) }) {
                            Text(stringResource(R.string.social_accept))
                        }
                    }
                }
            }
        }
    }

    Text(stringResource(R.string.social_friends), style = MaterialTheme.typography.titleLarge)
    if (uiState.snapshot.friends.isEmpty()) {
        Text(stringResource(R.string.social_no_friends), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        uiState.snapshot.friends.forEach { friend ->
            FriendCard(
                friend = friend,
                onRequestAccountability = { onRequestAccountability(friend.id) },
                onSetInstagramLimit = onSetInstagramLimit,
            )
        }
    }

    OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.social_sign_out))
    }
}

@Composable
private fun StreakCard(profile: SocialProfile) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.social_my_streak), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.social_days, profile.currentStreak), style = MaterialTheme.typography.displaySmall)
            Text(stringResource(R.string.social_best_streak, profile.bestStreak), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (profile.completedToday) {
                    stringResource(R.string.social_today_done)
                } else {
                    stringResource(R.string.social_today_not_done)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun FriendCard(friend: SocialFriend, onRequestAccountability: () -> Unit, onSetInstagramLimit: (String, Int) -> Unit) {
    var showLimitDialog by remember(friend.permissionId) { mutableStateOf(false) }

    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(friend.displayName, style = MaterialTheme.typography.titleMedium)
                    Text("@${friend.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.social_days, friend.currentStreak))
            }
            Text(
                text = if (friend.completedToday) {
                    stringResource(R.string.social_today_done)
                } else {
                    stringResource(R.string.social_today_not_done)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                friend.permissionStatus == null -> {
                    OutlinedButton(onClick = onRequestAccountability, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.social_permission_request))
                    }
                }

                friend.permissionStatus == AccountabilityPermissionStatus.PENDING -> {
                    Text(stringResource(R.string.social_permission_pending))
                }

                friend.permissionStatus == AccountabilityPermissionStatus.APPROVED && friend.canSetLimit -> {
                    Button(onClick = { showLimitDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.social_set_limit))
                    }
                }

                friend.permissionStatus == AccountabilityPermissionStatus.APPROVED -> {
                    Text(stringResource(R.string.social_permission_active))
                }
            }
        }
    }

    val permissionId = friend.permissionId
    if (showLimitDialog && permissionId != null) {
        SetLimitDialog(
            onDismiss = { showLimitDialog = false },
            onSave = { minutes ->
                showLimitDialog = false
                onSetInstagramLimit(permissionId, minutes)
            },
        )
    }
}

@Composable
private fun SetLimitDialog(onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var minutes by remember { mutableStateOf("30") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.social_set_limit)) },
        text = {
            OutlinedTextField(
                value = minutes,
                onValueChange = { minutes = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.social_limit_minutes)) },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { onSave(minutes.toIntOrNull()?.coerceIn(1, 1440) ?: 30) }) {
                Text(stringResource(R.string.social_save_limit))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.social_decline)) } },
    )
}
