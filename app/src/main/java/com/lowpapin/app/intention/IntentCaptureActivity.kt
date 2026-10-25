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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.lowpapin.app.R
import com.lowpapin.app.core.model.SessionPurpose
import com.lowpapin.app.designsystem.theme.LowpapinTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit

/**
 * Transparent dialog activity asking the user to declare their purpose and intended duration
 * before a session. Launched as a full-screen intent from the intention-request notification,
 * and from the outcomes dashboard to plan a session manually.
 */
@AndroidEntryPoint
class IntentCaptureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LowpapinTheme {
                IntentCaptureDialog(
                    onStartSession = { finish() },
                    onDismiss = { finish() },
                )
            }
        }
    }
}

private val DURATION_OPTIONS_MINUTES = listOf(5L, 10L, 15L, 30L, 45L, 60L)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntentCaptureDialog(
    onStartSession: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: IntentCaptureViewModel = hiltViewModel(),
) {
    var selectedPurpose by remember { mutableStateOf<SessionPurpose?>(null) }
    var selectedDurationMinutes by remember { mutableStateOf(0L) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.intention_capture_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.intention_capture_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = stringResource(R.string.intention_purpose_heading),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SessionPurpose.entries.forEach { purpose ->
                        FilterChip(
                            selected = selectedPurpose == purpose,
                            onClick = { selectedPurpose = purpose },
                            label = { Text(stringResource(purpose.labelRes())) },
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.intention_duration_heading),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DURATION_OPTIONS_MINUTES.forEach { minutes ->
                        FilterChip(
                            selected = selectedDurationMinutes == minutes,
                            onClick = { selectedDurationMinutes = minutes },
                            label = {
                                Text(
                                    stringResource(
                                        R.string.duration_minutes_short,
                                        minutes,
                                    ),
                                )
                            },
                        )
                    }
                }

                Button(
                    onClick = {
                        val purpose = selectedPurpose ?: return@Button
                        val duration = selectedDurationMinutes.takeIf { it > 0L } ?: return@Button
                        viewModel.startSession(
                            purpose = purpose,
                            intendedDurationMillis = TimeUnit.MINUTES.toMillis(duration),
                            onStarted = onStartSession,
                        )
                    },
                    enabled = selectedPurpose != null && selectedDurationMinutes > 0L,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.intention_start_session))
                }

                TextButton(
                    onClick = {
                        viewModel.dismiss()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.intention_not_now))
                }
            }
        }
    }
}

fun SessionPurpose.labelRes(): Int = when (this) {
    SessionPurpose.COMMUNICATE -> R.string.purpose_communicate
    SessionPurpose.LEARN -> R.string.purpose_learn
    SessionPurpose.ENTERTAIN -> R.string.purpose_entertain
    SessionPurpose.CREATE -> R.string.purpose_create
    SessionPurpose.OTHER -> R.string.purpose_other
}
