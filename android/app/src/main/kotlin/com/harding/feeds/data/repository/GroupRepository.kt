package com.harding.feeds.data.repository

import com.harding.feeds.client.apis.FamilyGroupApi
import com.harding.feeds.client.models.FamilyGroupDto
import com.harding.feeds.client.models.JoinGroupRequest
import com.harding.feeds.data.local.dao.SessionDao
import com.harding.feeds.data.local.entity.FamilyGroupEntity
import com.harding.feeds.data.local.entity.UserEntity
import com.harding.feeds.data.remote.ApiException
import com.harding.feeds.data.remote.bodyOrThrow
import com.harding.feeds.data.remote.toEntity
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

/**
 * Family group membership - create/join/invite are online one-off setup operations; the
 * resulting group and members are cached for offline display.
 */
class GroupRepository(
    private val familyGroupApi: FamilyGroupApi,
    private val sessionDao: SessionDao,
) {

    fun group(): Flow<FamilyGroupEntity?> = sessionDao.group()

    fun members(): Flow<List<UserEntity>> = sessionDao.users()

    /** Pulls the caller's group from the server; returns null when not in a group yet (404). */
    suspend fun refreshGroup(): FamilyGroupEntity? {
        val response = familyGroupApi.getFamilyGroup()
        if (response.code() == HTTP_NOT_FOUND) return null
        return cache(response.bodyOrThrow())
    }

    suspend fun createGroup(): FamilyGroupEntity =
        cache(familyGroupApi.createFamilyGroup().bodyOrThrow())

    suspend fun joinGroup(code: String): FamilyGroupEntity =
        cache(familyGroupApi.joinFamilyGroup(JoinGroupRequest(code)).bodyOrThrow())

    /** The group's stable current code (server mints one on first request). */
    suspend fun inviteCode(): String =
        familyGroupApi.getInviteCode().bodyOrThrow().code

    /** Mints a new code, invalidating the previous one. */
    suspend fun regenerateInviteCode(): String =
        familyGroupApi.regenerateInviteCode().bodyOrThrow().code

    private suspend fun cache(group: FamilyGroupDto): FamilyGroupEntity {
        val entity = FamilyGroupEntity(
            uuid = group.uuid?.toString() ?: throw ApiException(0, "Group without uuid")
        )
        sessionDao.upsertGroup(entity)

        val selfId = sessionDao.selfUser().firstOrNull()?.id
        group.users?.forEach { user ->
            sessionDao.upsertUser(user.toEntity(isSelf = user.id == selfId))
        }
        return entity
    }
}
