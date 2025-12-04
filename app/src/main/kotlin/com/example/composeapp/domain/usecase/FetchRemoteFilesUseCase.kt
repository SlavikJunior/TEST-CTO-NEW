/*
 * Use Case для получения списка файлов с удаленного устройства
 * 
 * Архитектурное обоснование:
 * - Инкапсулирует логику запроса файлов через JSON-over-TCP протокол
 * - Может добавлять дополнительную обработку, фильтрацию или кэширование
 * - Следует Single Responsibility Principle - отвечает только за получение файлов
 * - Независим от деталей сетевого протокола (Clean Architecture)
 * - Оркестрирует выполнение на правильном диспетчере (Dispatchers.IO)
 * 
 * Технологические решения:
 * - TCP обеспечивает надежную передачу данных
 * - JSON используется для простой сериализации списка файлов
 * - Корутины позволяют выполнять сетевые запросы без блокировки UI
 * - Dispatchers.IO оптимизирован для сетевых и файловых операций
 * - withContext обеспечивает cancellation и правильное переключение потоков
 * 
 * Бизнес-логика:
 * 1. Валидация входных параметров (deviceId не пустой)
 * 2. Переключение на IO диспетчер для сетевых операций
 * 3. Устанавливает TCP соединение с удаленным устройством
 * 4. Отправляет запрос списка файлов (LIST_FILES команда)
 * 5. Получает и парсит JSON ответ (FILE_LIST)
 * 6. Возвращает типизированный список SharedFile
 * 
 * Обработка ошибок:
 * - IllegalArgumentException при невалидном deviceId
 * - IOException при сетевых ошибках (timeout, connection refused)
 * - IllegalStateException если устройство оффлайн
 * - CancellationException при отмене корутины
 * - Все ошибки пробрасываются выше для обработки в UI
 * 
 * Восстановление после сбоев:
 * - Повторные попытки можно реализовать в вызывающем коде
 * - При отмене корутины (cancellation) операции автоматически прерываются
 * - TCP соединение автоматически закрывается через use {}
 */
package com.example.composeapp.domain.usecase

import com.example.composeapp.domain.model.SharedFile
import com.example.composeapp.domain.repository.FileShareRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Получает список файлов с удаленного устройства
 * 
 * Use Case инкапсулирует бизнес-логику получения файлов,
 * обеспечивает правильную оркестровку корутин и обработку ошибок.
 * 
 * @property fileShareRepository Репозиторий для работы с файлами
 */
class FetchRemoteFilesUseCase(
    private val fileShareRepository: FileShareRepository
) {
    /**
     * Выполняет запрос списка файлов с удаленного устройства
     * 
     * Процесс выполнения:
     * 1. Валидация deviceId
     * 2. Переключение на IO диспетчер для сетевых операций
     * 3. Делегирование запроса репозиторию
     * 4. Возврат списка файлов
     * 
     * Диспетчеры корутин:
     * - Dispatchers.IO используется для сетевых операций
     * - Оптимизирован для блокирующих IO операций
     * - Автоматически управляет пулом потоков
     * - withContext обеспечивает cancellation support
     * 
     * Обработка отмены (cancellation):
     * - При отмене корутины withContext автоматически прерывает операции
     * - TCP соединение закрывается корректно
     * - CancellationException пробрасывается выше
     * 
     * Примеры использования:
     * ```
     * val files = fetchRemoteFilesUseCase(deviceId = "device-123")
     * // или с обработкой ошибок
     * try {
     *     val files = fetchRemoteFilesUseCase(deviceId)
     * } catch (e: IOException) {
     *     // Обработка сетевых ошибок
     * }
     * ```
     * 
     * @param deviceId Идентификатор удаленного устройства
     * @return Список файлов, доступных на удаленном устройстве
     * @throws IllegalArgumentException если deviceId пустой или некорректный
     * @throws IllegalStateException если устройство оффлайн
     * @throws IOException при сетевых ошибках
     * @throws Exception при других ошибках протокола или файловой системы
     */
    suspend operator fun invoke(deviceId: String): List<SharedFile> = withContext(Dispatchers.IO) {
        // Валидация входных параметров
        require(deviceId.isNotBlank()) {
            "Device ID не может быть пустым"
        }
        
        // Делегируем выполнение репозиторию
        // Репозиторий обработает подключение, протокол и парсинг
        fileShareRepository.getRemoteFiles(deviceId)
    }
}
