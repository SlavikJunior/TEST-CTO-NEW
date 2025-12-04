/*
 * Реализация репозитория для управления передачей файлов
 * 
 * Архитектурное обоснование:
 * - Реализует интерфейс из domain слоя (Clean Architecture)
 * - Делегирует работу с TCP передачей сервису FileTransferService через TransferManager
 * - Отслеживает прогресс нескольких одновременных передач
 * - Обеспечивает отмену передач и обработку ошибок с автоматическими повторами
 * 
 * Технологические решения:
 * - FileTransferService - foreground сервис для TCP передачи файлов
 * - TransferManager - координатор с поддержкой повторов и WorkManager
 * - TCP Socket для передачи бинарных данных файлов
 * - JSON-over-TCP для управляющих команд (TRANSFER_REQUEST, PROGRESS, COMPLETE)
 * - Корутины для параллельных передач
 * - Flow для реактивного отслеживания прогресса
 * 
 * Протокол передачи:
 * 1. Отправка TRANSFER_REQUEST
 * 2. Получение TRANSFER_START
 * 3. Получение бинарных данных блоками
 * 4. Периодические обновления TRANSFER_PROGRESS
 * 5. Получение TRANSFER_COMPLETE
 * 
 * Обработка ошибок:
 * - Автоматические повторы через TransferManager (до 3 попыток)
 * - Экспоненциальная задержка между попытками
 * - Обработка таймаутов, потери связи, ошибок файловой системы
 */
package com.example.composeapp.data.repository

import android.util.Log
import com.example.composeapp.domain.model.DevicePeer
import com.example.composeapp.domain.model.TransferProgress
import com.example.composeapp.domain.model.TransferRequest
import com.example.composeapp.domain.repository.TransferRepository
import com.example.composeapp.service.TransferManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Реализация TransferRepository с использованием FileTransferService
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
    }
    
    private val _transfers = MutableStateFlow<List<TransferProgress>>(emptyList())
    
    override suspend fun startDownload(request: TransferRequest): String {
        try {
            // Находим peer по deviceId
            val peer = discoveredPeers().find { it.deviceId == request.deviceId }
                ?: throw IllegalArgumentException("Устройство не найдено: ${request.deviceId}")
            
            if (!peer.isOnline) {
                throw IllegalStateException("Устройство оффлайн: ${peer.nickname}")
            }
            
            // Запускаем передачу с автоматическими повторами
            val transferId = transferManager.startDownloadWithRetry(request, peer)
            Log.i(TAG, "Передача запущена: $transferId")
            return transferId
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка запуска передачи: ${e.message}", e)
            throw e
        }
    }
    
    override suspend fun cancelTransfer(transferId: String) {
        try {
            transferManager.cancelTransfer(transferId)
            Log.i(TAG, "Передача отменена: $transferId")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка отмены передачи: ${e.message}", e)
            throw e
        }
    }
    
    override fun observeTransfers(): Flow<List<TransferProgress>> {
        return transferManager.getActiveTransfers()
    }
    
    override fun observeTransfer(transferId: String): Flow<TransferProgress> {
        return transferManager.getActiveTransfers().map { transfers ->
            transfers.firstOrNull { it.transferId == transferId }
                ?: throw IllegalArgumentException("Transfer not found: $transferId")
        }
    }
    
    override suspend fun getAllTransfers(): List<TransferProgress> {
        // Получаем текущее состояние из flow
        return _transfers.value
    }
}
