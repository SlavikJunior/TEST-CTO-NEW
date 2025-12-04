/*
 * Реализация репозитория настроек с использованием DataStore
 * 
 * Архитектурное обоснование:
 * - Реализует интерфейс из domain слоя (Dependency Inversion Principle)
 * - Использует DataStore как современную замену SharedPreferences
 * - Обеспечивает персистентное хранение настроек
 * - Часть data слоя в Clean Architecture
 * 
 * Технологические решения:
 * - DataStore Preferences API для key-value хранилища
 * - Flow для реактивного получения изменений
 * - Корутины для асинхронного доступа
 * - Транзакционные обновления через edit()
 * 
 * Преимущества DataStore:
 * - Типобезопасность через Preferences.Key
 * - Асинхронный API без блокировки UI
 * - Поддержка Flow из коробки
 * - Обработка ошибок через исключения
 * - Атомарные операции обновления
 */
package com.example.composeapp.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.composeapp.domain.model.AppSettings
import com.example.composeapp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Расширение для создания DataStore instance
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/**
 * Реализация SettingsRepository с использованием DataStore
 * 
 * @property context Android контекст для доступа к DataStore
 */
class SettingsRepositoryImpl(
    private val context: Context
) : SettingsRepository {
    
    companion object {
        private val NICKNAME_KEY = stringPreferencesKey("nickname")
        private val SHARED_FOLDER_PATH_KEY = stringPreferencesKey("shared_folder_path")
        
        private const val DEFAULT_NICKNAME = "Мое устройство"
        private const val DEFAULT_SHARED_FOLDER = "/storage/emulated/0/SharedFiles"
    }
    
    override fun getSettings(): Flow<AppSettings> {
        return context.dataStore.data.map { preferences ->
            AppSettings(
                nickname = preferences[NICKNAME_KEY] ?: DEFAULT_NICKNAME,
                sharedFolderPath = preferences[SHARED_FOLDER_PATH_KEY] ?: DEFAULT_SHARED_FOLDER
            )
        }
    }
    
    override suspend fun saveSettings(settings: AppSettings) {
        context.dataStore.edit { preferences ->
            preferences[NICKNAME_KEY] = settings.nickname
            preferences[SHARED_FOLDER_PATH_KEY] = settings.sharedFolderPath
        }
    }
    
    override suspend fun updateNickname(nickname: String) {
        context.dataStore.edit { preferences ->
            preferences[NICKNAME_KEY] = nickname
        }
    }
    
    override suspend fun updateSharedFolderPath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[SHARED_FOLDER_PATH_KEY] = path
        }
    }
}
