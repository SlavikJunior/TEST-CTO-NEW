/*
 * Use Case для получения списка файлов с удаленного устройства
 * 
 * Архитектурное обоснование:
 * - Инкапсулирует логику запроса файлов через JSON-over-TCP протокол
 * - Может добавлять дополнительную обработку, фильтрацию или кэширование
 * - Следует Single Responsibility Principle
 * - Независим от деталей сетевого протокола (Clean Architecture)
 * 
 * Технологические решения:
 * - TCP обеспечивает надежную передачу данных
 * - JSON используется для простой сериализации списка файлов
 * - Корутины позволяют выполнять сетевые запросы без блокировки UI
 * 
 * Бизнес-логика:
 * - Устанавливает TCP соединение с удаленным устройством
 * - Отправляет запрос списка файлов (LIST_FILES команда)
 * - Получает и парсит JSON ответ
 * - Возвращает типизированный список SharedFile
 */
package com.example.composeapp.domain.usecase

import com.example.composeapp.domain.model.SharedFile
import com.example.composeapp.domain.repository.FileShareRepository

/**
 * Получает список файлов с удаленного устройства
 * 
 * @property fileShareRepository Репозиторий для работы с файлами
 */
class FetchRemoteFilesUseCase(
    private val fileShareRepository: FileShareRepository
) {
    /**
     * Выполняет запрос списка файлов
     * 
     * @param deviceId Идентификатор устройства
     * @return Список файлов, доступных на удаленном устройстве
     * @throws Exception если не удалось получить список файлов
     */
    suspend operator fun invoke(deviceId: String): List<SharedFile> {
        return fileShareRepository.getRemoteFiles(deviceId)
    }
}
