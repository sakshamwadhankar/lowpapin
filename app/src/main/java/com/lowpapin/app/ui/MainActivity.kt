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
package com.lowpapin.app.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.lowpapin.app.accessibility.LowpapinBlockAccessibilityService
import com.lowpapin.app.core.model.ThemeMode
import com.lowpapin.app.core.repository.UserSettingsStore
import com.lowpapin.app.debug.DebugOverlayConfig
import com.lowpapin.app.designsystem.theme.LocalSharedTransitionScope
import com.lowpapin.app.designsystem.theme.LowpapinTheme
import com.lowpapin.app.feature.home.HomeScreen
import com.lowpapin.app.feature.settings.SettingsScreen
import com.lowpapin.app.outcomes.OutcomesScreen
import com.lowpapin.app.util.requestAppReview
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import android.Manifest

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userSettingsStore: UserSettingsStore

    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()

        setContent {

            val appState: LowpapinAppState = rememberLowpapinAppState()
            val forceLegacyOverlay by DebugOverlayConfig.forceLegacyOverlay.collectAsStateWithLifecycle()
            val themeMode by userSettingsStore.getThemeMode().collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            val dynamicColorEnabled by userSettingsStore.getDynamicColorEnabled().collectAsStateWithLifecycle(true)

            LowpapinTheme(
                darkTheme = themeMode.resolveDarkTheme(),
                dynamicColor = dynamicColorEnabled,
            ) {
                SharedTransitionLayout {
                    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                        NavDisplay(
                            appState.backStack,
                            onBack = { appState.navigateBack() },
                            entryProvider = entryProvider {
                                entry<LowpapinRoute.Home> {
                                    HomeScreen(
                                        onNavigateToSettings = appState::navigateToSettings,
                                        onNavigateToOutcomes = appState::navigateToOutcomes,
                                        accessibilityServiceClass = LowpapinBlockAccessibilityService::class.java,
                                        onRequestAppReview = ::requestAppReview,
                                        forceLegacyOverlay = forceLegacyOverlay,
                                        onForceLegacyOverlayChanged = {
                                            DebugOverlayConfig.forceLegacyOverlay.value = it
                                        },
                                    )
                                }
                                entry<LowpapinRoute.Settings> {
                                    SettingsScreen(
                                        onNavigateBack = appState::navigateBack,
                                    )
                                }
                                entry<LowpapinRoute.Outcomes> {
                                    OutcomesScreen(
                                        onNavigateBack = appState::navigateBack,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    /** Interventions are delivered as notifications; ask once so heads-up prompts can appear. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_POST_NOTIFICATIONS,
            )
        }
    }

    private companion object {
        const val REQUEST_POST_NOTIFICATIONS = 4001
    }
}

/**
 * Resolves the persisted preference against the device setting.
 *
 * Must be composable because [ThemeMode.SYSTEM] reads the current configuration.
 */
@Composable
private fun ThemeMode.resolveDarkTheme(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
