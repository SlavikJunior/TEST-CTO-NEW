/*
 * Fake реализация TransferRepository для тестирования и разработки UI
 * 
 * Архитектурное обоснование:
 * - Предоставляет тестовые данные без реальных передач файлов
 * - Позволяет разрабатывать UI для отображения прогресса
 * - Документирует ожидаемое поведение репозитория
 * - Упрощает unit тестирование Use Cases
 * - Эмулирует различные состояния передач (прогресс, завершение, ошибки)
 * 
 * Использование:
 * - В UI тестах для изоляции от сетевого слоя
 * - В preview Compose для отображения прогресса передач
 * - В unit тестах для проверки обработки различных состояний
 * - Для демонстрации UI без реальных передач файлов
 */
package com.example.composeapp.data.repository.fake

import com.example.composeapp.domain.model.TransferProgress
import com.example.composeapp.domain.model.TransferRequest
import com.example.composeapp.domain.model.TransferState
import com.example.composeapp.domain.repository.TransferRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Fake реализация TransferRepository с тестовыми данными
 * 
 * Эмулирует:
 * - Запуск передач с генерацией transferId
 * - Прогресс передачи с автоматическим обновлением
 * - Завершение передач (успешное и с ошибками)
 * - Отмену передач
 * - Множественные одновременные передачи
 */
class FakeTransferRepository : TransferRepository {
    
    private val _transfers = MutableStateFlow<List<TransferProgress>>(emptyList())
    
    /**
     * Запускает скачивание файла с имитацией прогресса
     */
    override suspend fun startDownload(request: TransferRequest): String {
        val transferId = UUID.randomUUID().toString()
        val fileSize = 5_242_880L // 5 MB для теста
        
        // Создаем новую передачу в состоянии Pending
        val transfer = TransferProgress(
            transferId = transferId,
            fileId = request.fileId,
            fileName = "test_file.dat",
            fileSize = fileSize,
            bytesTransferred = 0L,
            state = TransferState.Pending,
            remoteDeviceId = request.deviceId,
            startTime = System.currentTimeMillis()
        )
        
        // Добавляем в список активных передач
        _transfers.value = _transfers.value + transfer
        
        // Запускаем эмуляцию прогресса в фоне
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            simulateTransferProgress(transferId, fileSize)
        }
        
        return transferId
    }
    
    /**
     * Эмулирует прогресс передачи файла
     */
    private suspend fun simulateTransferProgress(transferId: String, fileSize: Long) {
        delay(500) // Задержка перед стартом
        
        // Переводим в состояние InProgress
        updateTransferState(transferId) { transfer ->
            transfer.copy(state = TransferState.InProgress(0.0))
        }
        
        // Эмулируем прогресс передачи
        var bytesTransferred = 0L
        val chunkSize = fileSize / 20 // 20 обновлений
        
        while (bytesTransferred < fileSize) {
            delay(200) // Задержка между обновлениями
            
            bytesTransferred = (bytesTransferred + chunkSize).coerceAtMost(fileSize)
            val percentage = (bytesTransferred.toDouble() / fileSize.toDouble()) * 100
            
            updateTransferState(transferId) { transfer ->
                transfer.copy(
                    bytesTransferred = bytesTransferred,
                    state = TransferState.InProgress(percentage)
                )
            }
        }
        
        // Завершаем передачу
        delay(500)
        updateTransferState(transferId) { transfer ->
            transfer.copy(
                bytesTransferred = fileSize,
                state = TransferState.Completed(
                    filePath = "/storage/emulated/0/Download/${transfer.fileName}"
                )
            )
        }
    }
    
    /**
     * Обновляет состояние передачи
     */
    private fun updateTransferState(
        transferId: String,
        update: (TransferProgress) -> TransferProgress
    ) {
        _transfers.value = _transfers.value.map { transfer ->
            if (transfer.transferId == transferId) {
                update(transfer)
            } else {
                transfer
            }
        }
    }
    
    /**
     * Отменяет передачу
     */
    override suspend fun cancelTransfer(transferId: String) {
        updateTransferState(transferId) { transfer ->
            transfer.copy(state = TransferState.Cancelled("Отменено пользователем"))
        }
        
        // Удаляем из списка через некоторое время
        delay(1000)
        _transfers.value = _transfers.value.filterNot { it.transferId == transferId }
    }
    
    /**
     * Наблюдает за всеми передачами
     */
    override fun observeTransfers(): Flow<List<TransferProgress>> {
        return _transfers.asStateFlow()
    }
    
    /**
     * Наблюдает за конкретной передачей
     */
    override fun observeTransfer(transferId: String): Flow<TransferProgress> {
        return _transfers.map { transfers ->
            transfers.firstOrNull { it.transferId == transferId }
                ?: throw IllegalArgumentException("Transfer not found: $transferId")
        }
    }
    
    /**
     * Получает все передачи
     */
    override suspend fun getAllTransfers(): List<TransferProgress> {
        return _transfers.value
    }
}
