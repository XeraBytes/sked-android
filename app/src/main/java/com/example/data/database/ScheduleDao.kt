package com.example.data.database

import androidx.room.*
import com.example.data.model.ClassBlock
import com.example.data.model.ScheduleSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    // Class Blocks
    @Query("SELECT * FROM class_blocks")
    fun getAllClassBlocks(): Flow<List<ClassBlock>>

    @Query("SELECT * FROM class_blocks WHERE id = :id")
    suspend fun getClassBlock(id: Int): ClassBlock?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClassBlock(classBlock: ClassBlock): Long

    @Update
    suspend fun updateClassBlock(classBlock: ClassBlock)

    @Delete
    suspend fun deleteClassBlock(classBlock: ClassBlock)

    @Query("DELETE FROM class_blocks WHERE id = :id")
    suspend fun deleteClassBlockById(id: Int)

    @Query("DELETE FROM class_blocks")
    suspend fun clearAllClassBlocks()

    // Settings
    @Query("SELECT * FROM schedule_settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<ScheduleSettings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: ScheduleSettings)
}
