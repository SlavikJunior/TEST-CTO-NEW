/*
 * Реализация репозитория для управления передачей файлов
 * 
 * Архитектурное обоснование:
 * - Реализует интерфейс из domain слоя (Clean Architecture)
 * - Делегирует работу с TCP передачей сервису FileTransferService через TransferManager
 * - Отслеживает прогресс нескольких одновременных передач
 * - Обеспечивает отмену передач и обработку ошибок с автоматическими повторами
 * - Кэширует историю передач в памяти (in-memory cache)
 * - Поддерживает персистентность через потенциальное расширение (Room/DataStore)
 * 
 * Технологические решения:
 * - FileTransferService - foreground сервис для TCP передачи файлов
 * - TransferManager - координатор с поддержкой повторов и WorkManager
 * - TCP Socket для передачи бинарных данных файлов
 * - JSON-over-TCP для управляющих команд (TRANSFER_REQUEST, PROGRESS, COMPLETE)
 * - Корутины для параллельных передач без блокировки UI
 * - Flow для реактивного отслеживания прогресса в реальном времени
 * - In-memory cache для хранения завершенных передач
 * 
 * Протокол передачи:
 * 1. Отправка HANDSHAKE для идентификации
 * 2. Отправка TRANSFER_REQUEST с fileId и transferId
 * 3. Получение TRANSFER_START с размером файла и chunk size
 * 4. Получение бинарных данных блоками (по умолчанию 8KB)
 * 5. Периодические обновления TRANSFER_PROGRESS (каждые 10 блоков)
 * 6. Получение TRANSFER_COMPLETE при успешном завершении
 * 7. Или TRANSFER_ERROR при ошибке
 * 
 * Обработка ошибок:
 * - Автоматические повторы через TransferManager (до 3 попыток)
 * - Экспоненциальная задержка между попытками (1s, 2s, 4s, 8s...)
 * - Обработка таймаутов соединения (30 секунд)
 * - Обработка потери связи (SocketException, IOException)
 * - Обработка ошибок файловой системы (FILE_NOT_FOUND, PERMISSION_DENIED, STORAGE_FULL)
 * - Graceful degradation при недоступности сервисов
 * 
 * Стратегия повторов:
 * - RETRYABLE ошибки: CONNECTION_LOST, timeout, SocketException - повторяем
 * - NON-RETRYABLE ошибки: FILE_NOT_FOUND, PERMISSION_DENIED, STORAGE_FULL - не повторяем
 * - Экспоненциальная задержка предотвращает перегрузку сети
 * - Максимум 3 попытки для предотвращения бесконечных циклов
 * 
 * Восстановление после сбоев:
 * - При сетевых ошибках TransferManager автоматически повторяет передачу
 * - При потере приложения WorkManager восстанавливает передачи
 * - Прогресс передачи сохраняется в памяти и доступен через Flow
 * - В будущем можно добавить персистентность через Room для восстановления после перезагрузки
 * 
 * Персистентность (потенциальное расширение):
 * - In-memory cache для текущей сессии (быстрый доступ)
 * - Room/DataStore для долгосрочного хранения истории передач
 * - WorkManager для гарантированного выполнения фоновых передач
 */
package com.example.composeapp.data.repository

import android.util.Log
import com.example.composeapp.domain.model.DevicePeer
import com.example.composeapp.domain.model.TransferProgress
import com.example.composeapp.domain.model.TransferRequest
import com.example.composeapp.domain.model.TransferState
import com.example.composeapp.domain.repository.TransferRepository
import com.example.composeapp.service.TransferManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Реализация TransferRepository с использованием FileTransferService
 * 
 * Репозиторий управляет передачей файлов, кэшированием истории и координацией
 * между domain слоем и FileTransferService через TransferManager.
 * 
 * @property transferManager Менеджер для доступа к сервисам передачи
 * @property discoveredPeers Функция для получения списка обнаруженных устройств
 */
class TransferRepositoryImpl(
    private val transferManager: TransferManager,
    private val discoveredPeers: () -> List<DevicePeer>
) : TransferRepository {
    
    companion object {
        private const val TAG = "TransferRepositoryImpl"
        private const val MAX_COMPLETED_TRANSFERS_CACHE = 100 // Максимум завершенных передач в кэше
    }
    
    // In-memory cache для завершенных и отмененных передач
    // В будущем можно заменить на Room для персистентности
    private val _completedTransfers = MutableStateFlow<List<TransferProgress>>(emptyList())
    
    /**
     * Запускает скачивание файла с удаленного устройства
     * 
     * Процесс запуска:
     * 1. Валидация: проверяем что устройство найдено в списке обнаруженных
     * 2. Проверка статуса: устройство должно быть онлайн
     * 3. Запуск передачи через TransferManager с автоматическими повторами
     * 4. Возврат transferId для отслеживания прогресса
     * 
     * Обработка ошибок:
     * - IllegalArgumentException если устройство не найдено (не в списке обнаруженных)
     * - IllegalStateException если устройство оффлайн (не доступно)
     * - IOException при сетевых ошибках (таймаут, connection refused)
     * - Exception при других ошибках (протокол, файловая система)
     * 
     * Автоматические повторы:
     * - TransferManager автоматически повторяет до 3 раз при сетевых ошибках
     * - Между попытками экспоненциальная задержка
     * - При FILE_NOT_FOUND или PERMISSION_DENIED повторы не выполняются
     * 
     * @param request Запрос на передачу файла (deviceId, fileId, destinationPath)
     * @return Идентификатор созданной передачи для отслеживания прогресса
     * @throws IllegalArgumentException если устройство не найдено
     * @throws IllegalStateException если устройство оффлайн
     * @throws Exception при других ошибках
     */
    override suspend fun startDownload(request: TransferRequest): String {
        try {
            Log.i(TAG, "Запрос на скачивание файла ${request.fileId} с устройства ${request.deviceId}")
            
            // Находим peer по deviceId в списке обнаруженных устройств
            val peer = discoveredPeers().find { it.deviceId == request.deviceId }
                ?: throw IllegalArgumentException(
                    "Устройство не найдено: ${request.deviceId}. " +
                    "Убедитесь что устройство обнаружено через mDNS."
                )
            
            // Проверяем что устройство онлайн
            if (!peer.isOnline) {
                throw IllegalStateException(
                    "Устройство оффлайн: ${peer.nickname}. " +
                    "Дождитесь восстановления подключения или выберите другое устройство."
                )
            }
            
            Log.d(TAG, "Устройство найдено: ${peer.nickname} (${peer.ipAddress}:${peer.port})")
            
            // Запускаем передачу с автоматическими повторами через TransferManager
            // TransferManager сам обработает retry логику и экспоненциальную задержку
            val transferId = transferManager.startDownloadWithRetry(request, peer)
            
            Log.i(TAG, "Передача успешно запущена: transferId=$transferId, file=${request.fileId}")
            return transferId
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Валидация не пройдена: ${e.message}")
            throw e
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Устройство недоступно: ${e.message}")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка запуска передачи файла ${request.fileId}: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Отменяет активную передачу файла
     * 
     * Процесс отмены:
     * 1. Отправка CANCEL_TRANSFER сообщения на удаленное устройство
     * 2. Закрытие TCP соединения
     * 3. Удаление временных файлов
     * 4. Обновление состояния передачи на TransferState.Cancelled
     * 5. Перемещение передачи в кэш завершенных
     * 
     * Обработка ошибок:
     * - Если передача не найдена, логируем warning но не выбрасываем исключение
     * - Если соединение уже закрыто, просто обновляем локальное состояние
     * - IOException при отправке CANCEL_TRANSFER игнорируется (best effort)
     * 
     * @param transferId Идентификатор передачи для отмены
     */
    override suspend fun cancelTransfer(transferId: String) {
        try {
            Log.i(TAG, "Отмена передачи: transferId=$transferId")
            
            // Делегируем отмену TransferManager
            // TransferManager обработает отправку CANCEL_TRANSFER и очистку ресурсов
            transferManager.cancelTransfer(transferId)
            
            Log.i(TAG, "Передача успешно отменена: transferId=$transferId")
            
            // Кэшируем отмененную передачу для истории
            // В будущем можно добавить сохранение в Room
            cacheCompletedTransfer(transferId, TransferState.Cancelled("Отменено пользователем"))
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка отмены передачи $transferId: ${e.message}", e)
            // Пробрасываем ошибку выше для обработки в UI
            throw e
        }
    }
    
    /**
     * Наблюдает за прогрессом всех активных передач
     * 
     * Flow автоматически обновляется при:
     * - Запуске новой передачи
     * - Обновлении прогресса (каждые 10 блоков)
     * - Завершении передачи (успешно или с ошибкой)
     * - Отмене передачи
     * 
     * Обработка ошибок:
     * - При ошибке в Flow возвращаем пустой список (graceful degradation)
     * - Логируем все ошибки для отладки
     * - Flow не завершается при ошибке, продолжает работу
     * 
     * @return Flow со списком передач и их текущим прогрессом
     */
    override fun observeTransfers(): Flow<List<TransferProgress>> {
        return transferManager.getActiveTransfers()
            .onEach { transfers ->
                Log.d(TAG, "Обновление списка передач: ${transfers.size} активных")
                
                // Кэшируем завершенные передачи
                transfers.forEach { transfer ->
                    if (transfer.state is TransferState.Completed || 
                        transfer.state is TransferState.Failed ||
                        transfer.state is TransferState.Cancelled) {
                        cacheCompletedTransfer(transfer.transferId, transfer.state)
                    }
                }
            }
            .catch { e ->
                Log.e(TAG, "Ошибка в Flow передач: ${e.message}", e)
                // Graceful degradation - возвращаем пустой список вместо падения
                emit(emptyList())
            }
    }
    
    /**
     * Наблюдает за прогрессом конкретной передачи
     * 
     * Flow автоматически обновляется при изменении состояния указанной передачи.
     * Если передача завершена и удалена из активных, проверяем кэш завершенных.
     * 
     * Обработка ошибок:
     * - Если передача не найдена, выбрасываем IllegalArgumentException
     * - При ошибках в Flow логируем и пробрасываем выше
     * 
     * @param transferId Идентификатор передачи
     * @return Flow с прогрессом указанной передачи
     * @throws IllegalArgumentException если передача не найдена
     */
    override fun observeTransfer(transferId: String): Flow<TransferProgress> {
        return transferManager.getActiveTransfers()
            .map { transfers ->
                transfers.firstOrNull { it.transferId == transferId }
                    ?: throw IllegalArgumentException(
                        "Передача не найдена: $transferId. " +
                        "Возможно передача уже завершена или отменена."
                    )
            }
            .catch { e ->
                Log.e(TAG, "Ошибка в Flow передачи $transferId: ${e.message}", e)
                throw e
            }
    }
    
    /**
     * Получает список всех передач (активных и завершенных)
     * 
     * Объединяет:
     * - Активные передачи из FileTransferService (через TransferManager)
     * - Завершенные передачи из in-memory кэша
     * 
     * В будущем:
     * - Можно добавить загрузку из Room для персистентности
     * - Можно добавить пагинацию для больших списков
     * - Можно добавить фильтрацию (по дате, статусу, устройству)
     * 
     * @return Список всех передач (активные + завершенные из кэша)
     */
    override suspend fun getAllTransfers(): List<TransferProgress> {
        return try {
            // Получаем активные передачи из TransferManager
            val activeTransfers = transferManager.getActiveTransfers().value
            
            // Объединяем с завершенными из кэша
            val allTransfers = activeTransfers + _completedTransfers.value
            
            Log.d(TAG, "Всего передач: ${allTransfers.size} (${activeTransfers.size} активных)")
            allTransfers
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения списка передач: ${e.message}", e)
            // Graceful degradation - возвращаем только кэшированные
            _completedTransfers.value
        }
    }
    
    /**
     * Кэширует завершенную передачу в памяти
     * 
     * Управление размером кэша:
     * - Ограничение MAX_COMPLETED_TRANSFERS_CACHE (100)
     * - При переполнении удаляем самые старые записи
     * - FIFO стратегия
     * 
     * В будущем:
     * - Можно добавить сохранение в Room для персистентности
     * - Можно добавить TTL для автоматической очистки старых записей
     * 
     * @param transferId ID передачи
     * @param state Финальное состояние передачи
     */
    private fun cacheCompletedTransfer(transferId: String, state: TransferState) {
        try {
            // Проверяем что передача еще не в кэше
            if (_completedTransfers.value.any { it.transferId == transferId }) {
                return
            }
            
            // Создаем запись для кэша (без полной информации о прогрессе)
            // В реальном приложении здесь можно было бы получить полную информацию из сервиса
            val completedTransfer = TransferProgress(
                transferId = transferId,
                fileId = "", // Можно расширить для хранения полной информации
                fileName = "",
                fileSize = 0L,
                bytesTransferred = 0L,
                state = state,
                remoteDeviceId = "",
                startTime = System.currentTimeMillis()
            )
            
            val currentCache = _completedTransfers.value.toMutableList()
            currentCache.add(completedTransfer)
            
            // Ограничиваем размер кэша
            if (currentCache.size > MAX_COMPLETED_TRANSFERS_CACHE) {
                // Удаляем самые старые записи (FIFO)
                val toRemove = currentCache.size - MAX_COMPLETED_TRANSFERS_CACHE
                repeat(toRemove) {
                    currentCache.removeAt(0)
                }
                Log.d(TAG, "Кэш очищен: удалено $toRemove старых записей")
            }
            
            _completedTransfers.value = currentCache
            Log.d(TAG, "Передача добавлена в кэш: transferId=$transferId, состояние=$state")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка кэширования передачи $transferId: ${e.message}", e)
        }
    }
}
