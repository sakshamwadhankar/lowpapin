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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Handles the exit-prompt notification actions (Done / +5 min / Keep watching) and forwards
 * them to [SessionIntentionManager]. Runs in the same process as the accessibility service, so
 * the singleton manager state stays consistent.
 */
@AndroidEntryPoint
class InterventionActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var sessionIntentionManager: SessionIntentionManager

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SESSION_DONE -> sessionIntentionManager.onUserDone()
            ACTION_EXTEND -> sessionIntentionManager.onExtendRequested(EXTEND_STEP_MILLIS)
            ACTION_KEEP_WATCHING -> sessionIntentionManager.onKeepWatching()
        }
    }

    companion object {
        const val ACTION_SESSION_DONE = "com.lowpapin.app.intention.ACTION_SESSION_DONE"
        const val ACTION_EXTEND = "com.lowpapin.app.intention.ACTION_EXTEND"
        const val ACTION_KEEP_WATCHING = "com.lowpapin.app.intention.ACTION_KEEP_WATCHING"
        const val EXTRA_INTENT_ID = "extra_intent_id"
        val EXTEND_STEP_MILLIS: Long = TimeUnit.MINUTES.toMillis(5)
    }
}
