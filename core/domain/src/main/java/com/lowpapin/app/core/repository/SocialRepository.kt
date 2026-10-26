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

import com.lowpapin.app.core.model.SocialSnapshot
import kotlinx.coroutines.flow.Flow

/** Cloud-backed account, friendship, streak, and accountability operations. */
interface SocialRepository {
    val isConfigured: Boolean
    val isAuthenticated: Flow<Boolean>

    suspend fun signUp(email: String, password: String, username: String): Result<Unit>
    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun signOut(): Result<Unit>

    suspend fun loadSnapshot(): Result<SocialSnapshot>
    suspend fun sendFriendRequest(username: String): Result<Unit>
    suspend fun respondToFriendRequest(friendshipId: String, accept: Boolean): Result<Unit>
    suspend fun requestAccountabilityPermission(friendId: String): Result<Unit>
    suspend fun respondToAccountabilityPermission(permissionId: String, accept: Boolean): Result<Unit>
    suspend fun setInstagramLimit(permissionId: String, dailyLimitMillis: Long): Result<Unit>
    suspend fun revokeAccountabilityPermission(permissionId: String): Result<Unit>

    /** Records today's under-limit result and updates the server-authoritative streak. */
    suspend fun recordDailyCheckIn(underLimit: Boolean): Result<Unit>

    /** Refreshes the approved incoming limit into the local blocker cache. */
    suspend fun refreshRemoteLimit(): Result<Unit>
}
