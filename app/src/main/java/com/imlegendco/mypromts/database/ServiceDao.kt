package com.imlegendco.mypromts.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceDao {
    @Query("SELECT * FROM services ORDER BY orderIndex ASC, name ASC")
    fun getAllServices(): Flow<List<ServiceEntity>>

    @Query("SELECT MAX(orderIndex) FROM services")
    suspend fun getMaxOrderIndex(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertService(service: ServiceEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServiceReturnId(service: ServiceEntity): Long

    @Delete
    suspend fun deleteService(service: ServiceEntity)
}