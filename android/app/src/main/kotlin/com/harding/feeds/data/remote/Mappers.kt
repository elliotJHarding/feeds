package com.harding.feeds.data.remote

import com.harding.feeds.client.models.AppUserDto
import com.harding.feeds.client.models.BabyDto
import com.harding.feeds.client.models.FeedDto
import com.harding.feeds.data.local.SyncState
import com.harding.feeds.data.local.entity.BabyEntity
import com.harding.feeds.data.local.entity.FeedEntity
import com.harding.feeds.data.local.entity.UserEntity
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

private fun Instant.toOffset(): OffsetDateTime = atOffset(ZoneOffset.UTC)

fun FeedEntity.toDto(): FeedDto = FeedDto(
    id = UUID.fromString(id),
    babyId = babyId,
    type = type,
    startTime = startTime.toOffset(),
    side = side,
    amountMl = amountMl,
    endTime = endTime?.toOffset(),
)

fun FeedDto.toEntity(): FeedEntity = FeedEntity(
    id = id.toString(),
    babyId = babyId,
    type = type,
    side = side,
    amountMl = amountMl,
    startTime = startTime.toInstant(),
    endTime = endTime?.toInstant(),
    createdBy = createdBy,
    createdAt = createdAt?.toInstant(),
    updatedAt = updatedAt?.toInstant(),
    syncState = SyncState.SYNCED,
)

fun BabyDto.toEntity(): BabyEntity = BabyEntity(
    id = checkNotNull(id) { "Server returned a baby without an id" },
    name = name,
    dateOfBirth = dateOfBirth,
)

fun AppUserDto.toEntity(isSelf: Boolean): UserEntity = UserEntity(
    id = checkNotNull(id) { "Server returned a user without an id" },
    name = name,
    email = email,
    pictureUrl = pictureUrl,
    isSelf = isSelf,
)
