/*
 * Use Case для начала скачивания файла
 * 
 * Архитектурное обоснование:
 * - Координирует процесс инициации передачи файла
 * - Может включать дополнительную валидацию (проверка свободного места, дубликатов)
 * - Обеспечивает разделение бизнес-логики и деталей передачи
 * - Следует принципам Clean Architecture
 * 
 * Технологические решения:
 * - TCP используется для надежной передачи файлов
 * - JSON-over-TCP для управляющих команд
 * - Корутины позволяют неблокирующую работу
 * 
 * Бизнес-логика:
 * - Валидация запроса на передачу
 * - Проверка доступности устройства
 * - Инициация TCP соединения
 * - Отправка TRANSFER_REQUEST команды
 * - Возврат идентификатора передачи для отслеживания
 */
package com.example.composeapp.domain.usecase

import com.example.composeapp.domain.model.TransferRequest
import com.example.composeapp.domain.repository.TransferRepository

/**
 * Начинает скачивание файла с удаленного устройства
 * 
 * @property transferRepository Репозиторий для управления передачами
 */
class StartDownloadUseCase(
    private val transferRepository: TransferRepository
) {
    /**
     * Выполняет запуск скачивания
     * 
     * @param request Запрос на передачу файла
     * @return Идентификатор созданной передачи
     * @throws IllegalArgumentException если запрос невалиден
     * @throws Exception если не удалось начать передачу
     */
    suspend operator fun invoke(request: TransferRequest): String {
        require(request.deviceId.isNotBlank()) { "ID устройства не может быть пустым" }
        require(request.fileId.isNotBlank()) { "ID файла не может быть пустым" }
        require(request.destinationPath.isNotBlank()) { "Путь назначения не может быть пустым" }
        
        return transferRepository.startDownload(request)
    }
}
