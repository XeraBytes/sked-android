package com.example.data.repository

import com.example.data.database.ScheduleDao
import com.example.data.model.ClassBlock
import com.example.data.model.ScheduleSettings
import kotlinx.coroutines.flow.Flow

class ScheduleRepository(private val scheduleDao: ScheduleDao) {
    val allClassBlocks: Flow<List<ClassBlock>> = scheduleDao.getAllClassBlocks()
    val settings: Flow<ScheduleSettings?> = scheduleDao.getSettings()

    suspend fun insertClassBlock(classBlock: ClassBlock): Long {
        return scheduleDao.insertClassBlock(classBlock)
    }

    suspend fun updateClassBlock(classBlock: ClassBlock) {
        scheduleDao.updateClassBlock(classBlock)
    }

    suspend fun deleteClassBlock(classBlock: ClassBlock) {
        scheduleDao.deleteClassBlock(classBlock)
    }

    suspend fun deleteClassBlockById(id: Int) {
        scheduleDao.deleteClassBlockById(id)
    }

    suspend fun clearAllClassBlocks() {
        scheduleDao.clearAllClassBlocks()
    }

    suspend fun saveSettings(settings: ScheduleSettings) {
        scheduleDao.saveSettings(settings)
    }
}
