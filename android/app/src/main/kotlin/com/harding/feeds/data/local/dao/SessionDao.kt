package com.harding.feeds.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.harding.feeds.data.local.entity.FamilyGroupEntity
import com.harding.feeds.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Query("SELECT * FROM users WHERE isSelf = 1 LIMIT 1")
    fun selfUser(): Flow<UserEntity?>

    @Query("SELECT * FROM users ORDER BY id")
    fun users(): Flow<List<UserEntity>>

    @Upsert
    suspend fun upsertUser(user: UserEntity)

    @Upsert
    suspend fun upsertUsers(users: List<UserEntity>)

    @Query("SELECT * FROM family_group LIMIT 1")
    fun group(): Flow<FamilyGroupEntity?>

    @Upsert
    suspend fun upsertGroup(group: FamilyGroupEntity)

    @Query("DELETE FROM users")
    suspend fun clearUsers()

    @Query("DELETE FROM family_group")
    suspend fun clearGroup()
}
