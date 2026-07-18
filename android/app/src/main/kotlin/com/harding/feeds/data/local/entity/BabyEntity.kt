package com.harding.feeds.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "babies")
data class BabyEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val dateOfBirth: LocalDate?,
)
