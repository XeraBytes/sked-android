package com.example.util

import android.content.Context
import com.example.data.model.ClassBlock
import com.example.data.model.ScheduleSettings
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object LocalStorageHelper {
    private const val PREFS_NAME = "schedule_local_storage"
    private const val KEY_CLASSES = "saved_classes"
    private const val KEY_SETTINGS = "saved_settings"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val classListType = Types.newParameterizedType(List::class.java, ClassBlock::class.java)
    private val classesAdapter = moshi.adapter<List<ClassBlock>>(classListType)
    private val settingsAdapter = moshi.adapter(ScheduleSettings::class.java)

    /**
     * Persists the class blocks to SharedPreferences local storage.
     */
    fun saveClasses(context: Context, classes: List<ClassBlock>) {
        val json = classesAdapter.toJson(classes)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CLASSES, json)
            .apply()
    }

    /**
     * Retrieves the persisted class blocks from SharedPreferences local storage.
     */
    fun getClasses(context: Context): List<ClassBlock>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CLASSES, null) ?: return null
        return try {
            classesAdapter.fromJson(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Persists the schedule settings to SharedPreferences local storage.
     */
    fun saveSettings(context: Context, settings: ScheduleSettings) {
        val json = settingsAdapter.toJson(settings)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SETTINGS, json)
            .apply()
    }

    /**
     * Retrieves the persisted schedule settings from SharedPreferences local storage.
     */
    fun getSettings(context: Context): ScheduleSettings? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SETTINGS, null) ?: return null
        return try {
            settingsAdapter.fromJson(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Clears all saved data in the SharedPreferences local storage.
     */
    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
