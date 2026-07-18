package com.harding.feeds.data.repository

import com.harding.feeds.client.apis.BabiesApi
import com.harding.feeds.client.models.BabyDto
import com.harding.feeds.data.local.dao.BabyDao
import com.harding.feeds.data.local.entity.BabyEntity
import com.harding.feeds.data.remote.bodyOrThrow
import com.harding.feeds.data.remote.toEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Babies read from the local cache (kept fresh by the sync engine). Creating a baby is a
 * one-off online setup step, not an offline-first write like feeds.
 */
class BabyRepository(
    private val babyDao: BabyDao,
    private val babiesApi: BabiesApi,
) {

    fun babies(): Flow<List<BabyEntity>> = babyDao.babies()

    suspend fun createBaby(name: String, dateOfBirth: LocalDate?): BabyEntity {
        val created = babiesApi.createBaby(BabyDto(name = name, dateOfBirth = dateOfBirth))
            .bodyOrThrow()
            .toEntity()
        babyDao.upsertAll(listOf(created))
        return created
    }
}
