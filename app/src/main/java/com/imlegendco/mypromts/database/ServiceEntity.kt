package com.imlegendco.mypromts.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "services")
data class ServiceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val iconIdentifier: String, // "banana_icon", "blue_circle", etc.
    val orderIndex: Int = 0
)