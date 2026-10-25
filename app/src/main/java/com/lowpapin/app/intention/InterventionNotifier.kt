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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.lowpapin.app.R
import com.lowpapin.app.core.model.BlockableApp
import com.lowpapin.app.core.model.SessionIntent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Delivers the non-blocking interventions: the intention-capture request when a tracked app
 * opens, and the exit prompt (with Done / Extend / Keep-watching actions) when a session
 * overruns its declared duration.
 */
@Singleton
class InterventionNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    init {
        notificationManager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_INTENTIONS,
                context.getString(R.string.channel_intentions_name),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    /** Heads-up prompt asking the user to declare a purpose and duration for this session. */
    fun notifyIntentRequest(app: BlockableApp) {
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_INTENT_CAPTURE,
            Intent(context, IntentCaptureActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = builder(CHANNEL_INTENTIONS, contentIntent)
            .setContentTitle(context.getString(R.string.intention_request_title))
            .setContentText(context.getString(R.string.intention_request_body, app.name))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()

        notificationManager?.notify(NOTIFICATION_INTENT_REQUEST, notification)
    }

    /**
     * Exit prompt at a session overrun. Offers real choices instead of blocking: finish, extend
     * the declared duration, or keep watching (logged as a relapse signal).
     */
    fun notifyExitPrompt(intent: SessionIntent, app: BlockableApp) {
        val contentText = when (intent.variant) {
            // Stats variant shows the plan-vs-actual numbers so the experiment can compare
            // which phrasing users act on.
            com.lowpapin.app.core.model.InterventionVariant.STATS_PROMPT -> context.getString(
                R.string.exit_prompt_body_stats,
                formatDuration(intent.intendedDurationMillis),
                formatDuration(intent.overrunMillis),
            )
            com.lowpapin.app.core.model.InterventionVariant.SIMPLE_PROMPT -> context.getString(
                R.string.exit_prompt_body_simple,
                formatDuration(intent.intendedDurationMillis),
            )
        }

        val fullIntent = PendingIntent.getActivity(
            context,
            REQUEST_EXIT_PROMPT,
            Intent(context, IntentCaptureActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = builder(CHANNEL_INTENTIONS, fullIntent)
            .setContentTitle(context.getString(R.string.exit_prompt_title, app.name))
            .setContentText(contentText)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.action_done), actionIntent(InterventionActionReceiver.ACTION_SESSION_DONE, intent.id, 1))
            .addAction(0, context.getString(R.string.action_extend), actionIntent(InterventionActionReceiver.ACTION_EXTEND, intent.id, 2))
            .addAction(0, context.getString(R.string.action_keep_watching), actionIntent(InterventionActionReceiver.ACTION_KEEP_WATCHING, intent.id, 3))
            .build()

        notificationManager?.notify(NOTIFICATION_EXIT_PROMPT, notification)
    }

    fun cancelAll() {
        notificationManager?.cancel(NOTIFICATION_INTENT_REQUEST)
        notificationManager?.cancel(NOTIFICATION_EXIT_PROMPT)
    }

    private fun builder(channelId: String, fullScreenIntent: PendingIntent): Notification.Builder =
        Notification.Builder(context, channelId)
            .setSmallIcon(com.lowpapin.app.core.designsystem.R.drawable.ic_timer)
            .setContentIntent(fullScreenIntent)
            .setShowWhen(true)

    private fun actionIntent(action: String, intentId: Long, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, InterventionActionReceiver::class.java)
                .setAction(action)
                .putExtra(InterventionActionReceiver.EXTRA_INTENT_ID, intentId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun formatDuration(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        return context.getString(R.string.duration_minutes_short, minutes)
    }

    private companion object {
        const val CHANNEL_INTENTIONS = "session_intentions"
        const val NOTIFICATION_INTENT_REQUEST = 1001
        const val NOTIFICATION_EXIT_PROMPT = 1002
        const val REQUEST_INTENT_CAPTURE = 2001
        const val REQUEST_EXIT_PROMPT = 2002
    }
}
