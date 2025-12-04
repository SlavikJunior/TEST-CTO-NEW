/*
 * Use Case для начала скачивания файла
 * 
 * Архитектурное обоснование:
 * - Координирует процесс инициации передачи файла
 * - Выполняет бизнес-валидацию перед запуском передачи
 * - Может включать дополнительную логику (проверка свободного места, дубликатов)
 * - Обеспечивает разделение бизнес-логики и деталей передачи
 * - Следует принципам Clean Architecture и Single Responsibility
 * - Оркестрирует выполнение на правильном диспетчере
 * 
 * Технологические решения:
 * - TCP используется для надежной передачи файлов
 * - JSON-over-TCP для управляющих команд (HANDSHAKE, TRANSFER_REQUEST)
 * - Корутины позволяют неблокирующую работу
 * - Dispatchers.IO для сетевых операций
 * - withContext обеспечивает cancellation support
 * 
 * Бизнес-логика:
 * 1. Валидация входных параметров (deviceId, fileId, destinationPath)
 * 2. Проверка доступности устройства (через репозиторий)
 * 3. Переключение на IO диспетчер
 * 4. Инициация TCP соединения
 * 5. Отправка HANDSHAKE для идентификации
 * 6. Отправка TRANSFER_REQUEST команды
 * 7. Возврат transferId для отслеживания прогресса
 * 
 * Обработка ошибок:
 * - IllegalArgumentException при невалидных параметрах
 * - IllegalStateException если устройство оффлайн
 * - IOException при сетевых ошибках
 * - CancellationException при отмене корутины
 * 
 * Восстановление после сбоев:
 * - Автоматические повторы через TransferRepository (до 3 попыток)
 * - Экспоненциальная задержка между попытками
 * - При отмене корутины все операции прерываются корректно
 */
package com.example.composeapp.domain.usecase

import com.example.composeapp.domain.model.TransferRequest
import com.example.composeapp.domain.repository.TransferRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Начинает скачивание файла с удаленного устройства
 * 
 * Use Case инкапсулирует бизнес-логику запуска передачи файла,
 * выполняет валидацию и обеспечивает правильную оркестровку корутин.
 * 
 * @property transferRepository Репозиторий для управления передачами
 */
class StartDownloadUseCase(
    private val transferRepository: TransferRepository
) {
    /**
     * Выполняет запуск скачивания файла с удаленного устройства
     * 
     * Процесс выполнения:
     * 1. Валидация всех параметров запроса
     * 2. Переключение на IO диспетчер для сетевых операций
     * 3. Делегирование запуска передачи репозиторию
     * 4. Возврат transferId для отслеживания прогресса
     * 
     * Валидация:
     * - deviceId не должен быть пустым
     * - fileId не должен быть пустым
     * - destinationPath не должен быть пустым
     * - destinationPath должен быть абсолютным путем
     * 
     * Диспетчеры корутин:
     * - Dispatchers.IO для сетевых операций
     * - Оптимизирован для блокирующих операций
     * - withContext обеспечивает cancellation support
     * 
     * Обработка отмены (cancellation):
     * - При отмене корутины все операции прерываются
     * - TCP соединение закрывается корректно
     * - Временные файлы удаляются
     * - CancellationException пробрасывается выше
     * 
     * Автоматические повторы:
     * - Репозиторий автоматически повторяет до 3 раз при сетевых ошибках
     * - Экспоненциальная задержка между попытками (1s, 2s, 4s)
     * - При FILE_NOT_FOUND или PERMISSION_DENIED повторы не выполняются
     * 
     * Примеры использования:
     * ```
     * val transferId = startDownloadUseCase(
     *     TransferRequest(
     *         deviceId = "device-123",
     *         fileId = "file-456",
     *         destinationPath = "/storage/emulated/0/Download/file.txt"
     *     )
     * )
     * ```
     * 
     * @param request Запрос на передачу файла (deviceId, fileId, destinationPath)
     * @return Идентификатор созданной передачи для отслеживания прогресса
     * @throws IllegalArgumentException если запрос невалиден (пустые параметры)
     * @throws IllegalStateException если устройство оффлайн или недоступно
     * @throws IOException при сетевых ошибках
     * @throws Exception при других ошибках протокола или файловой системы
     */
    suspend operator fun invoke(request: TransferRequest): String = withContext(Dispatchers.IO) {
        // Валидация входных параметров
        require(request.deviceId.isNotBlank()) { 
            "ID устройства не может быть пустым. Укажите корректный deviceId."
        }
        require(request.fileId.isNotBlank()) { 
            "ID файла не может быть пустым. Укажите корректный fileId."
        }
        require(request.destinationPath.isNotBlank()) { 
            "Путь назначения не может быть пустым. Укажите абсолютный путь для сохранения файла."
        }
        
        // Дополнительная валидация пути назначения
        require(request.destinationPath.startsWith("/")) {
            "Путь назначения должен быть абсолютным (начинаться с /). Получено: ${request.destinationPath}"
        }
        
        // Делегируем выполнение репозиторию
        // Репозиторий обработает:
        // - Поиск устройства в списке обнаруженных
        // - Проверку что устройство онлайн
        // - Установку TCP соединения
        // - Отправку HANDSHAKE и TRANSFER_REQUEST
        // - Автоматические повторы при ошибках
        transferRepository.startDownload(request)
    }
}
