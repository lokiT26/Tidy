package com.lokesh.tidy.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

data class AppSettings(
    val name: String = "",
    /** 0 = system, 1 = light, 2 = dark */
    val themeMode: Int = 0,
    val amoled: Boolean = false,
    val accent: Int = 0,
    val confetti: Boolean = true,
    val haptics: Boolean = true,
    val lastPriority: Int = 0,
    val lastCategoryId: Long? = null,
    /** 0 = Auto (masonry), 1 = Small, 2 = Medium, 3 = Large */
    val noteSizeMode: Int = 0,
    val separatePinnedNoteSize: Boolean = false,
    /** 0 = Auto (masonry), 1 = Small, 2 = Medium, 3 = Large */
    val pinnedNoteSizeMode: Int = 0,
    /** 0 = Custom order, 1 = Newest edited, 2 = Newest created, 3 = Oldest edited, 4 = Title A-Z */
    val notesSortOrder: Int = 0,
)

class SettingsStore(private val context: Context) {
    private object K {
        val name = stringPreferencesKey("name")
        val theme = intPreferencesKey("theme")
        val amoled = booleanPreferencesKey("amoled")
        val accent = intPreferencesKey("accent")
        val seeded = booleanPreferencesKey("seeded")
        val confetti = booleanPreferencesKey("confetti")
        val haptics = booleanPreferencesKey("haptics")
        val lastPriority = intPreferencesKey("last_priority")
        val lastCategoryId = longPreferencesKey("last_category_id")
        val noteSizeMode = intPreferencesKey("note_size_mode")
        val separatePinnedNoteSize = booleanPreferencesKey("separate_pinned_note_size")
        val pinnedNoteSizeMode = intPreferencesKey("pinned_note_size_mode")
        val notesSortOrder = intPreferencesKey("notes_sort_order")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map {
        val rawCat = it[K.lastCategoryId]
        AppSettings(
            name = it[K.name] ?: "",
            themeMode = it[K.theme] ?: 0,
            amoled = it[K.amoled] ?: false,
            accent = it[K.accent] ?: 0,
            confetti = it[K.confetti] ?: true,
            haptics = it[K.haptics] ?: true,
            lastPriority = it[K.lastPriority] ?: 0,
            lastCategoryId = if (rawCat == null || rawCat < 0L) null else rawCat,
            noteSizeMode = it[K.noteSizeMode] ?: 0,
            separatePinnedNoteSize = it[K.separatePinnedNoteSize] ?: false,
            pinnedNoteSizeMode = it[K.pinnedNoteSizeMode] ?: 0,
            notesSortOrder = it[K.notesSortOrder] ?: 0,
        )
    }

    suspend fun current() = flow.first()
    suspend fun setName(v: String) = context.dataStore.edit { it[K.name] = v.take(30) }
    suspend fun setTheme(v: Int) = context.dataStore.edit { it[K.theme] = v }
    suspend fun setAmoled(v: Boolean) = context.dataStore.edit { it[K.amoled] = v }
    suspend fun setAccent(v: Int) = context.dataStore.edit { it[K.accent] = v }
    suspend fun setConfetti(v: Boolean) = context.dataStore.edit { it[K.confetti] = v }
    suspend fun setHaptics(v: Boolean) = context.dataStore.edit { it[K.haptics] = v }
    suspend fun setNoteSizeMode(v: Int) = context.dataStore.edit { it[K.noteSizeMode] = v }
    suspend fun setSeparatePinnedNoteSize(v: Boolean) = context.dataStore.edit { it[K.separatePinnedNoteSize] = v }
    suspend fun setPinnedNoteSizeMode(v: Int) = context.dataStore.edit { it[K.pinnedNoteSizeMode] = v }
    suspend fun setNotesSortOrder(v: Int) = context.dataStore.edit { it[K.notesSortOrder] = v }
    suspend fun setLastEditorDefaults(priority: Int, categoryId: Long?) = context.dataStore.edit {
        it[K.lastPriority] = priority
        it[K.lastCategoryId] = categoryId ?: -1L
    }
    suspend fun seeded() = context.dataStore.data.first()[K.seeded] ?: false
    suspend fun setSeeded() = context.dataStore.edit { it[K.seeded] = true }
}
