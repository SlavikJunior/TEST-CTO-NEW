/*
 * Use Case для сохранения настроек приложения
 * 
 * Архитектурное обоснование:
 * - Инкапсулирует бизнес-логику сохранения настроек
 * - Следует Single Responsibility Principle - одна задача
 * - Позволяет добавить валидацию и дополнительную логику при необходимости
 * - Часть Clean Architecture - слой domain не зависит от деталей реализации
 * 
 * Технологические решения:
 * - Использует корутины для асинхронного выполнения
 * - Делегирует хранение данных репозиторию (DataStore)
 * - Может включать валидацию nickname и пути к папке
 * 
 * Clean Architecture:
 * - UseCase координирует работу между presentation и data слоями
 * - Независим от UI фреймворка и способа хранения данных
 * - Легко тестируется с mock репозиториями
 */
package com.example.composeapp.domain.usecase

import com.example.composeapp.domain.model.AppSettings
import com.example.composeapp.domain.repository.SettingsRepository

/**
 * Сохраняет настройки приложения
 * 
 * @property settingsRepository Репозиторий для работы с настройками
 */
class SaveSettingsUseCase(
    private val settingsRepository: SettingsRepository
) {
    /**
     * Выполняет сохранение настроек
     * 
     * @param settings Настройки для сохранения
     * @throws IllegalArgumentException если настройки невалидны
     */
    suspend operator fun invoke(settings: AppSettings) {
        require(settings.nickname.isNotBlank()) { "Никнейм не может быть пустым" }
        require(settings.sharedFolderPath.isNotBlank()) { "Путь к папке не может быть пустым" }
        
        settingsRepository.saveSettings(settings)
    }
}
