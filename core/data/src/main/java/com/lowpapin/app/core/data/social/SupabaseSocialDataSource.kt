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
package com.lowpapin.app.core.data.social

import com.lowpapin.app.core.model.AccountabilityPermissionStatus
import com.lowpapin.app.core.model.AccountabilityRequest
import com.lowpapin.app.core.model.FriendRequest
import com.lowpapin.app.core.model.RemoteInstagramLimit
import com.lowpapin.app.core.model.SocialFriend
import com.lowpapin.app.core.model.SocialProfile
import com.lowpapin.app.core.model.SocialSnapshot
import com.lowpapin.app.core.repository.AccountabilityLimitStore
import com.lowpapin.app.core.repository.SocialRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class SupabaseConfig(
    val url: String,
    val publishableKey: String,
) {
    val isConfigured: Boolean
        get() = url.startsWith("https://") && publishableKey.isNotBlank()
}

@Singleton
class SupabaseClientProvider @Inject constructor(
    private val config: SupabaseConfig,
) {
    val client: SupabaseClient? by lazy {
        if (!config.isConfigured) {
            null
        } else {
            createSupabaseClient(
                supabaseUrl = config.url,
                supabaseKey = config.publishableKey,
            ) {
                install(Auth)
                install(Postgrest)
            }
        }
    }
}

@Singleton
class SupabaseSocialDataSource @Inject constructor(
    private val clientProvider: SupabaseClientProvider,
    private val accountabilityLimitStore: AccountabilityLimitStore,
) : SocialRepository {

    private val client: SupabaseClient?
        get() = clientProvider.client

    override val isConfigured: Boolean
        get() = clientProvider.client != null

    override val isAuthenticated: Flow<Boolean> = clientProvider.client?.auth?.sessionStatus
        ?.map { it is SessionStatus.Authenticated }
        ?: flowOf(false)

    override suspend fun signUp(email: String, password: String, username: String): Result<Unit> = runCatching {
        require(username.matches(USERNAME_REGEX)) { "Use 3–24 letters, numbers, or underscores." }
        require(password.length >= 8) { "Password must be at least 8 characters." }
        val supabase = requireClient()
        supabase.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            data = buildJsonObject { put("username", username.trim().lowercase()) }
        }
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        requireClient().auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        requireClient().auth.signOut()
        accountabilityLimitStore.clearActiveLimit()
    }

    override suspend fun loadSnapshot(): Result<SocialSnapshot> = runCatching {
        val supabase = requireClient()
        val profile = currentUserId()?.let { userId ->
            supabase.from("profiles").select {
                filter { eq("id", userId) }
            }.decodeSingle<ProfileRow>().toDomain()
        }
        SocialSnapshot(
            profile = profile,
            friends = supabase.from("my_friend_streaks").select().decodeList<FriendStreakRow>().map { it.toDomain() },
            friendRequests = supabase.from("my_friend_requests").select().decodeList<FriendRequestRow>().map { it.toDomain() },
            accountabilityRequests = supabase.from("my_accountability_requests")
                .select()
                .decodeList<AccountabilityRequestRow>()
                .map { it.toDomain() },
        )
    }

    override suspend fun sendFriendRequest(username: String): Result<Unit> = runCatching {
        requireClient().postgrest.rpc(
            "send_friend_request",
            buildJsonObject { put("p_username", username.trim().lowercase()) },
        )
    }

    override suspend fun respondToFriendRequest(friendshipId: String, accept: Boolean): Result<Unit> = runCatching {
        requireClient().postgrest.rpc(
            "respond_friend_request",
            buildJsonObject {
                put("p_friendship_id", friendshipId)
                put("p_accept", accept)
            },
        )
    }

    override suspend fun requestAccountabilityPermission(friendId: String): Result<Unit> = runCatching {
        requireClient().postgrest.rpc(
            "request_accountability_permission",
            buildJsonObject { put("p_friend_id", friendId) },
        )
    }

    override suspend fun respondToAccountabilityPermission(permissionId: String, accept: Boolean): Result<Unit> = runCatching {
        requireClient().postgrest.rpc(
            "respond_accountability_permission",
            buildJsonObject {
                put("p_permission_id", permissionId)
                put("p_accept", accept)
            },
        )
    }

    override suspend fun setInstagramLimit(permissionId: String, dailyLimitMillis: Long): Result<Unit> = runCatching {
        requireClient().postgrest.rpc(
            "set_accountability_limit",
            buildJsonObject {
                put("p_permission_id", permissionId)
                put("p_daily_limit_seconds", (dailyLimitMillis / 1_000L).coerceIn(60L, 86_400L))
            },
        )
    }

    override suspend fun revokeAccountabilityPermission(permissionId: String): Result<Unit> = runCatching {
        requireClient().postgrest.rpc(
            "revoke_accountability_permission",
            buildJsonObject { put("p_permission_id", permissionId) },
        )
    }

    override suspend fun recordDailyCheckIn(underLimit: Boolean): Result<Unit> = runCatching {
        requireClient().postgrest.rpc(
            "record_daily_check_in",
            buildJsonObject { put("p_under_limit", underLimit) },
        )
    }

    override suspend fun refreshRemoteLimit(): Result<Unit> = runCatching {
        val rows = requireClient().postgrest.rpc("get_my_active_accountability_limit")
            .decodeList<RemoteLimitRow>()
        val limit = rows.firstOrNull()?.toDomain()
        if (limit == null) {
            accountabilityLimitStore.clearActiveLimit()
        } else {
            accountabilityLimitStore.setActiveLimit(limit)
        }
    }

    private fun currentUserId(): String? = client?.auth?.currentUserOrNull()?.id

    private fun requireClient(): SupabaseClient = client
        ?: error("Supabase is not configured. Add -PsupabaseUrl and -PsupabasePublishableKey when building.")

    private companion object {
        val USERNAME_REGEX = Regex("[a-z0-9_]{3,24}")
    }
}

@Serializable
private data class ProfileRow(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("current_streak") val currentStreak: Int = 0,
    @SerialName("best_streak") val bestStreak: Int = 0,
    @SerialName("last_check_in_date") val lastCheckInDate: String? = null,
    @SerialName("completed_today") val completedToday: Boolean = false,
)

@Serializable
private data class FriendStreakRow(
    @SerialName("friendship_id") val friendshipId: String,
    @SerialName("friend_id") val friendId: String,
    val username: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("current_streak") val currentStreak: Int = 0,
    @SerialName("best_streak") val bestStreak: Int = 0,
    @SerialName("completed_today") val completedToday: Boolean = false,
    @SerialName("permission_id") val permissionId: String? = null,
    @SerialName("permission_status") val permissionStatus: String? = null,
    @SerialName("can_set_limit") val canSetLimit: Boolean = false,
)

@Serializable
private data class FriendRequestRow(
    val id: String,
    @SerialName("requester_id") val requesterId: String,
    @SerialName("requester_username") val requesterUsername: String,
    @SerialName("addressee_id") val addresseeId: String,
    val status: String,
)

@Serializable
private data class AccountabilityRequestRow(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("owner_username") val ownerUsername: String,
    @SerialName("partner_id") val partnerId: String,
    @SerialName("partner_username") val partnerUsername: String,
    @SerialName("requested_by") val requestedBy: String,
    val status: String,
)

@Serializable
private data class RemoteLimitRow(
    @SerialName("permission_id") val permissionId: String,
    @SerialName("daily_limit_seconds") val dailyLimitSeconds: Long,
    @SerialName("sponsor_id") val sponsorId: String,
    @SerialName("updated_at_epoch_millis") val updatedAtEpochMillis: Long,
)

private fun String?.toLocalDateOrNull() = this?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }

private fun ProfileRow.toDomain() = SocialProfile(
    id = id,
    username = username,
    displayName = displayName.ifBlank { username },
    currentStreak = currentStreak,
    bestStreak = bestStreak,
    lastCheckInDate = lastCheckInDate.toLocalDateOrNull(),
    completedToday = completedToday,
)

private fun FriendStreakRow.toDomain() = SocialFriend(
    friendshipId = friendshipId,
    id = friendId,
    username = username,
    displayName = displayName.ifBlank { username },
    currentStreak = currentStreak,
    bestStreak = bestStreak,
    completedToday = completedToday,
    permissionId = permissionId,
    permissionStatus = permissionStatus?.let { runCatching { AccountabilityPermissionStatus.valueOf(it) }.getOrNull() },
    canSetLimit = canSetLimit,
)

private fun FriendRequestRow.toDomain() = FriendRequest(
    id = id,
    requesterId = requesterId,
    requesterUsername = requesterUsername,
    addresseeId = addresseeId,
    status = status,
)

private fun AccountabilityRequestRow.toDomain() = AccountabilityRequest(
    id = id,
    ownerId = ownerId,
    ownerUsername = ownerUsername,
    partnerId = partnerId,
    partnerUsername = partnerUsername,
    requestedBy = requestedBy,
    status = AccountabilityPermissionStatus.valueOf(status),
)

private fun RemoteLimitRow.toDomain() = RemoteInstagramLimit(
    permissionId = permissionId,
    dailyLimitMillis = dailyLimitSeconds * 1_000L,
    sponsorId = sponsorId,
    updatedAtMillis = updatedAtEpochMillis,
)
