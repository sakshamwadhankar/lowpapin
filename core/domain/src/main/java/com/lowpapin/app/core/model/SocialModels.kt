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
package com.lowpapin.app.core.model

import androidx.compose.runtime.Immutable
import java.time.LocalDate

enum class AccountabilityPermissionStatus {
    PENDING,
    APPROVED,
    DECLINED,
    REVOKED,
}

@Immutable
data class SocialProfile(
    val id: String,
    val username: String,
    val displayName: String,
    val currentStreak: Int,
    val bestStreak: Int,
    val lastCheckInDate: LocalDate?,
    val completedToday: Boolean,
)

@Immutable
data class SocialFriend(
    val friendshipId: String,
    val id: String,
    val username: String,
    val displayName: String,
    val currentStreak: Int,
    val bestStreak: Int,
    val completedToday: Boolean,
    val permissionId: String?,
    val permissionStatus: AccountabilityPermissionStatus?,
    val canSetLimit: Boolean,
)

@Immutable
data class FriendRequest(
    val id: String,
    val requesterId: String,
    val requesterUsername: String,
    val addresseeId: String,
    val status: String,
)

@Immutable
data class AccountabilityRequest(
    val id: String,
    val ownerId: String,
    val ownerUsername: String,
    val partnerId: String,
    val partnerUsername: String,
    val requestedBy: String,
    val status: AccountabilityPermissionStatus,
)

@Immutable
data class RemoteInstagramLimit(
    val permissionId: String,
    val dailyLimitMillis: Long,
    val sponsorId: String,
    val updatedAtMillis: Long,
)

@Immutable
data class SocialSnapshot(
    val profile: SocialProfile? = null,
    val friends: List<SocialFriend> = emptyList(),
    val friendRequests: List<FriendRequest> = emptyList(),
    val accountabilityRequests: List<AccountabilityRequest> = emptyList(),
)
