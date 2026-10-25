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
package com.lowpapin.app.core.data.repository

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import androidx.core.content.ContextCompat.getSystemService
import com.lowpapin.app.core.blocking.time.TimeProvider
import com.lowpapin.app.core.model.AppUsagePattern
import com.lowpapin.app.core.model.BlockableApp
import com.lowpapin.app.core.repository.UsagePatternsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads per-app usage and open counts from Android's [UsageStatsManager], gated behind the
 * PACKAGE_USAGE_STATS special access the user grants in system settings.
 */
class UsagePatternsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
) : UsagePatternsRepository {

    private val usageStatsManager: UsageStatsManager?
        get() = getSystemService(context, UsageStatsManager::class.java)

    override fun isUsageAccessGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override suspend fun getTodayPatterns(): List<AppUsagePattern> = withContext(Dispatchers.IO) {
        val manager = usageStatsManager ?: return@withContext emptyList()
        if (!isUsageAccessGranted()) return@withContext emptyList()

        val dayStart = timeProvider.localDateNow().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = timeProvider.currentTimeInMillis()

        // Aggregate foreground time from the stats table and count opens from ACTIVITY_RESUMED
        // events, which works on every level that exposes the special access.
        val foregroundByPackage = manager.queryAndAggregateUsageStats(dayStart, now)
        val opensByPackage = HashMap<String, Int>()
        val events = manager.queryEvents(dayStart, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                val packageName = event.packageName ?: continue
                opensByPackage[packageName] = (opensByPackage[packageName] ?: 0) + 1
            }
        }

        BlockableApp.entries.mapNotNull { app ->
            val packages = app.getPackageIds()
            val usageMillis = foregroundByPackage.values
                .filter { it.packageName in packages }
                .sumOf { it.totalTimeInForeground }
            val openCount = packages.sumOf { opensByPackage[it] ?: 0 }
            if (usageMillis > 0L || openCount > 0) {
                AppUsagePattern(app = app, usageMillis = usageMillis, openCount = openCount)
            } else {
                null
            }
        }
    }
}
