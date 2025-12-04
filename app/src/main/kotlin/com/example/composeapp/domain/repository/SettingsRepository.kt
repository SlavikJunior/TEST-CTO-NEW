/*
 * Интерфейс репозитория для управления настройками приложения
 * 
 * Архитектурное обоснование:
 * - Абстрагирует детали хранения настроек от бизнес-логики
 * - Использует Flow для реактивного получения изменений настроек
 * - Следует принципу инверсии зависимостей (Dependency Inversion Principle)
 * 
 * Технологические решения:
 * - DataStore Preferences используется для хранения настроек (современная альтернатива SharedPreferences)
 * - DataStore обеспечивает типобезопасность и асинхронный API
 * - Flow позволяет UI автоматически реагировать на изменения настроек
 * - Корутины обеспечивают неблокирующее чтение/запись
 * 
 * Преимущества DataStore перед SharedPreferences:
 * - Асинхронный API (без блокировки UI потока)
 * - Типобезопасность при использовании Proto DataStore
 * - Поддержка Flow для реактивного программирования
 * - Обработка ошибок через исключения
 * - Транзакционные обновления
 */
package com.example.composeapp.domain.repository

import com.example.composeapp.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий для управления настройками приложения
 */
interface SettingsRepository {
    /**
     * Получает текущие настройки приложения как реактивный поток
     * 
     * @return Flow с настройками, обновляется при любом изменении
     */
    fun getSettings(): Flow<AppSettings>
    
    /**
     * Сохраняет настройки приложения
     * 
     * @param settings Новые настройки для сохранения
     */
    suspend fun saveSettings(settings: AppSettings)
    
    /**
     * Обновляет никнейм устройства
     * 
     * @param nickname Новое имя устройства
     */
    suspend fun updateNickname(nickname: String)
    
    /**
     * Обновляет путь к общей папке
     * 
     * @param path Новый путь к папке
     */
    suspend fun updateSharedFolderPath(path: String)
}
