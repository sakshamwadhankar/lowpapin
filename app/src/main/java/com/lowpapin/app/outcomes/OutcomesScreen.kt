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

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lowpapin.app.R
import com.lowpapin.app.core.model.AppUsagePattern
import com.lowpapin.app.core.model.SessionIntent
import com.lowpapin.app.core.model.usage.SessionClassification
import com.lowpapin.app.designsystem.icon.LowpapinIcons
import com.lowpapin.app.intention.IntentCaptureActivity
import com.lowpapin.app.intention.labelRes
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * The outcomes dashboard: the user's outcome goal, adherence of sessions to their declared
 * intentions, the weekly usage trend, the intentional/compulsive behaviour mix, the
 * exit-prompt experiment comparison, and app-level usage patterns from UsageStats.
 */
@Composable
fun OutcomesScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: OutcomesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val usageAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.refreshUsagePatterns() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    painter = painterResource(id = LowpapinIcons.ArrowBack),
                    contentDescription = stringResource(R.string.outcomes_back),
                )
            }
            Text(
                text = stringResource(R.string.outcomes_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        ActiveIntentionCard(
            activeIntent = state.activeIntent,
            onDone = viewModel::onSessionDone,
            onExtend = viewModel::onExtendSession,
        )

        Button(
            onClick = {
                context.startActivity(Intent(context, IntentCaptureActivity::class.java))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.outcomes_plan_session))
        }

        OutcomeGoalCard(
            goalMillis = state.report.goalMillis,
            onGoalSelected = viewModel::setDailyGoal,
        )

        OutcomeReportCard(report = state.report)

        UsagePatternsCard(
            patterns = state.patterns,
            usageAccessGranted = state.usageAccessGranted,
            onGrantAccess = {
                usageAccessLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            },
        )
    }
}

@Composable
private fun ActiveIntentionCard(
    activeIntent: SessionIntent?,
    onDone: () -> Unit,
    onExtend: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.outcomes_active_intention),
                style = MaterialTheme.typography.titleMedium,
            )
            if (activeIntent == null) {
                Text(
                    text = stringResource(R.string.outcomes_no_active_intention),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val progress = if (activeIntent.intendedDurationMillis > 0L) {
                    (activeIntent.actualTrackedMillis.toFloat() / activeIntent.intendedDurationMillis).coerceIn(0f, 1f)
                } else {
                    0f
                }
                Text(
                    text = stringResource(activeIntent.purpose.labelRes()),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(
                        R.string.outcomes_intention_progress,
                        formatMinutes(activeIntent.actualTrackedMillis),
                        formatMinutes(activeIntent.intendedDurationMillis),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onDone) {
                        Text(stringResource(R.string.action_done))
                    }
                    OutlinedButton(onClick = onExtend) {
                        Text(stringResource(R.string.action_extend))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OutcomeGoalCard(
    goalMillis: Long,
    onGoalSelected: (Long) -> Unit,
) {
    val goalOptionsMinutes = listOf(0L, 15L, 30L, 45L, 60L, 90L, 120L)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.outcomes_goal_heading),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (goalMillis > 0L) {
                    stringResource(R.string.outcomes_goal_current, formatMinutes(goalMillis))
                } else {
                    stringResource(R.string.outcomes_goal_unset)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                goalOptionsMinutes.forEach { minutes ->
                    FilterChip(
                        selected = goalMillis == TimeUnit.MINUTES.toMillis(minutes),
                        onClick = { onGoalSelected(TimeUnit.MINUTES.toMillis(minutes)) },
                        label = {
                            Text(
                                text = if (minutes == 0L) {
                                    stringResource(R.string.outcomes_goal_none)
                                } else {
                                    stringResource(R.string.duration_minutes_short, minutes)
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OutcomeReportCard(report: com.lowpapin.app.core.model.usage.OutcomeReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.outcomes_report_heading),
                style = MaterialTheme.typography.titleMedium,
            )

            if (report.goalMillis > 0L) {
                ReportRow(
                    label = stringResource(R.string.outcomes_goal_attainment),
                    value = stringResource(
                        R.string.outcomes_fraction,
                        report.daysMeetingGoal,
                        report.daysTracked,
                        percent(report.goalAttainmentFraction),
                    ),
                )
            }

            ReportRow(
                label = stringResource(R.string.outcomes_adherence),
                value = stringResource(
                    R.string.outcomes_fraction,
                    report.intentsCompletedWithinIntended,
                    report.intentsTotal,
                    percent(report.intentAdherenceFraction),
                ),
            )

            ReportRow(
                label = stringResource(R.string.outcomes_trend),
                value = stringResource(
                    R.string.outcomes_trend_value,
                    formatMinutes(abs(report.weeklyTrendMillis)),
                    if (report.weeklyTrendMillis <= 0L) {
                        stringResource(R.string.outcomes_trend_down)
                    } else {
                        stringResource(R.string.outcomes_trend_up)
                    },
                ),
            )

            ReportRow(
                label = stringResource(R.string.outcomes_behaviour_mix),
                value = stringResource(
                    R.string.outcomes_mix_value,
                    percent(report.classification.fractionOf(SessionClassification.INTENTIONAL)),
                    percent(report.classification.fractionOf(SessionClassification.UNCERTAIN)),
                    percent(report.classification.fractionOf(SessionClassification.COMPULSIVE)),
                ),
            )

            ReportRow(
                label = stringResource(R.string.outcomes_interventions),
                value = stringResource(
                    R.string.outcomes_interventions_value,
                    report.promptsDelivered,
                    report.promptsDismissed,
                ),
            )

            val simple = report.variantOutcomes.firstOrNull {
                it.variant == com.lowpapin.app.core.model.InterventionVariant.SIMPLE_PROMPT
            }
            val stats = report.variantOutcomes.firstOrNull {
                it.variant == com.lowpapin.app.core.model.InterventionVariant.STATS_PROMPT
            }
            if (simple != null && stats != null && (simple.sessionsStarted + stats.sessionsStarted) > 0) {
                ReportRow(
                    label = stringResource(R.string.outcomes_experiment),
                    value = stringResource(
                        R.string.outcomes_experiment_value,
                        percent(simple.completionRate),
                        simple.sessionsStarted,
                        percent(stats.completionRate),
                        stats.sessionsStarted,
                    ),
                )
            }
        }
    }
}

@Composable
private fun UsagePatternsCard(
    patterns: List<AppUsagePattern>,
    usageAccessGranted: Boolean,
    onGrantAccess: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.outcomes_patterns_heading),
                style = MaterialTheme.typography.titleMedium,
            )
            if (!usageAccessGranted) {
                Text(
                    text = stringResource(R.string.outcomes_patterns_needs_access),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onGrantAccess) {
                    Text(stringResource(R.string.outcomes_grant_usage_access))
                }
            } else if (patterns.isEmpty()) {
                Text(
                    text = stringResource(R.string.outcomes_patterns_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                patterns.forEach { pattern ->
                    ReportRow(
                        label = pattern.app.name.lowercase().replaceFirstChar { it.uppercase() },
                        value = stringResource(
                            R.string.outcomes_pattern_value,
                            formatMinutes(pattern.usageMillis),
                            pattern.openCount,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun percent(fraction: Float): Int = (fraction * 100).toInt()

private fun formatMinutes(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    return if (minutes >= 60) {
        "${minutes / 60}h ${minutes % 60}m"
    } else {
        "${minutes}m"
    }
}
