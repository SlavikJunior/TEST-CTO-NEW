/*
 * Use Case для запуска обнаружения устройств в сети
 * 
 * Архитектурное обоснование:
 * - Координирует процесс обнаружения устройств через mDNS
 * - Обеспечивает регистрацию текущего устройства перед началом поиска
 * - Интегрирует настройки пользователя (nickname) с процессом обнаружения
 * - Следует принципам Clean Architecture - независимость от деталей реализации
 * 
 * Технологические решения:
 * - mDNS обеспечивает автоматическое обнаружение без конфигурации
 * - Flow позволяет реактивно получать обновления списка устройств
 * - Корутины обеспечивают неблокирующее выполнение
 * 
 * Бизнес-логика:
 * - Получает настройки пользователя (nickname)
 * - Регистрирует устройство в сети с выбранным именем
 * - Начинает процесс обнаружения других устройств
 */
package com.example.composeapp.domain.usecase

import com.example.composeapp.domain.model.DevicePeer
import com.example.composeapp.domain.repository.DiscoveryRepository
import com.example.composeapp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Запускает процесс обнаружения устройств в локальной сети
 * 
 * @property discoveryRepository Репозиторий для обнаружения устройств
 * @property settingsRepository Репозиторий для получения настроек
 */
class StartDiscoveryUseCase(
    private val discoveryRepository: DiscoveryRepository,
    private val settingsRepository: SettingsRepository
) {
    /**
     * Выполняет запуск обнаружения
     * 
     * @param port TCP порт для входящих соединений
     * @return Flow со списком обнаруженных устройств
     */
    suspend operator fun invoke(port: Int): Flow<List<DevicePeer>> {
        val settings = settingsRepository.getSettings().first()
        
        discoveryRepository.registerDevice(
            nickname = settings.nickname,
            port = port
        )
        
        return discoveryRepository.startDiscovery()
    }
}
