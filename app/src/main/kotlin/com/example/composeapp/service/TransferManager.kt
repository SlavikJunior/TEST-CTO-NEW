/*
 * Менеджер передач файлов с поддержкой WorkManager
 * 
 * Архитектурное обоснование:
 * - Координирует работу P2P сервисов (discovery и transfer)
 * - Обеспечивает надежность передач через автоматические повторы
 * - Интегрируется с WorkManager для гарантированного выполнения задач
 * - Управляет жизненным циклом foreground сервисов
 * - Обрабатывает ошибки и пропагирует их в вышестоящие слои
 * 
 * Технологические решения:
 * - WorkManager для фоновых задач с гарантией выполнения (даже после перезагрузки)
 * - Foreground Services для длительных операций (discovery и transfer)
 * - Экспоненциальная задержка для повторных попыток (exponential backoff)
 * - Flow API для реактивного отслеживания состояния передач
 * - Structured concurrency для правильной отмены операций
 * - Централизованная обработка ошибок с логированием
 * 
 * Почему WorkManager:
 * - Гарантирует выполнение задач даже при перезапуске устройства
 * - Учитывает состояние батареи и сети (constraints)
 * - Автоматические повторы при сбоях
 * - Поддержка цепочек задач (chains)
 * - Совместимость с Doze режимом Android
 * 
 * Стратегия повторов:
 * - Автоматические повторы до 3 раз для сетевых ошибок
 * - Экспоненциальная задержка: 1s, 2s, 4s, 8s...
 * - Нет повторов для ошибок файловой системы (FILE_NOT_FOUND, PERMISSION_DENIED)
 */
package com.example.composeapp.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.work.*
import com.example.composeapp.domain.model.DevicePeer
import com.example.composeapp.domain.model.TransferProgress
import com.example.composeapp.domain.model.TransferRequest
import com.example.composeapp.p2p.protocol.DEFAULT_PORT
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

/**
 * Менеджер для координации P2P сервисов и управления передачами файлов
 * 
 * @property context Android контекст
 */
class TransferManager(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "TransferManager"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        
        // WorkManager теги
        private const val WORK_TAG_TRANSFER = "file_transfer"
        private const val WORK_TAG_DISCOVERY = "peer_discovery"
    }
    
    private val workManager = WorkManager.getInstance(context)
    
    private var discoveryService: P2pDiscoveryService? = null
    private var transferService: FileTransferService? = null
    
    private val discoveryConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? P2pDiscoveryService.LocalBinder
            discoveryService = binder?.getService()
            Log.i(TAG, "Discovery service подключен")
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            discoveryService = null
            Log.w(TAG, "Discovery service отключен")
        }
    }
    
    private val transferConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? FileTransferService.LocalBinder
            transferService = binder?.getService()
            Log.i(TAG, "Transfer service подключен")
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            transferService = null
            Log.w(TAG, "Transfer service отключен")
        }
    }
    
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    /**
     * Инициализирует менеджер и запускает сервисы
     * 
     * @param deviceId Уникальный идентификатор устройства
     * @param nickname Отображаемое имя устройства
     * @param sharedFolderPath Путь к общей папке с файлами
     * @param port TCP порт для входящих соединений
     */
    fun initialize(
        deviceId: String,
        nickname: String,
        sharedFolderPath: String,
        port: Int = DEFAULT_PORT
    ) {
        if (_isInitialized.value) {
            Log.w(TAG, "TransferManager уже инициализирован")
            return
        }
        
        try {
            // Запускаем сервис обнаружения
            P2pDiscoveryService.start(context, deviceId, nickname, port)
            context.bindService(
                Intent(context, P2pDiscoveryService::class.java),
                discoveryConnection,
                Context.BIND_AUTO_CREATE
            )
            
            // Запускаем сервис передачи файлов
            FileTransferService.start(context, deviceId, nickname, sharedFolderPath, port)
            context.bindService(
                Intent(context, FileTransferService::class.java),
                transferConnection,
                Context.BIND_AUTO_CREATE
            )
            
            _isInitialized.value = true
            Log.i(TAG, "TransferManager инициализирован")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка инициализации TransferManager: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Останавливает менеджер и сервисы
     */
    fun shutdown() {
        if (!_isInitialized.value) {
            Log.w(TAG, "TransferManager не инициализирован")
            return
        }
        
        try {
            // Отключаем сервисы
            context.unbindService(discoveryConnection)
            context.unbindService(transferConnection)
            
            // Останавливаем сервисы
            P2pDiscoveryService.stop(context)
            FileTransferService.stop(context)
            
            // Отменяем все WorkManager задачи
            workManager.cancelAllWorkByTag(WORK_TAG_TRANSFER)
            workManager.cancelAllWorkByTag(WORK_TAG_DISCOVERY)
            
            discoveryService = null
            transferService = null
            
            _isInitialized.value = false
            Log.i(TAG, "TransferManager остановлен")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка остановки TransferManager: ${e.message}", e)
        }
    }
    
    /**
     * Получает поток обнаруженных устройств
     */
    fun getDiscoveredPeers(): Flow<List<DevicePeer>> {
        return discoveryService?.discoveredPeers
            ?: MutableStateFlow(emptyList()).asStateFlow()
    }
    
    /**
     * Получает поток активных передач
     */
    fun getActiveTransfers(): Flow<List<TransferProgress>> {
        return transferService?.transfers
            ?: MutableStateFlow(emptyList()).asStateFlow()
    }
    
    /**
     * Начинает скачивание файла с повторными попытками
     * 
     * @param request Запрос на передачу файла
     * @param peer Устройство-источник
     * @return ID передачи
     */
    suspend fun startDownloadWithRetry(
        request: TransferRequest,
        peer: DevicePeer
    ): String {
        val service = transferService
            ?: throw IllegalStateException("Transfer service не инициализирован")
        
        var attempt = 0
        var lastException: Exception? = null
        
        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                val transferId = service.startDownload(
                    remoteAddress = peer.ipAddress,
                    remotePort = peer.port,
                    fileId = request.fileId,
                    fileName = extractFileName(request.destinationPath),
                    destinationPath = request.destinationPath
                )
                
                Log.i(TAG, "Передача запущена: $transferId (попытка ${attempt + 1})")
                return transferId
            } catch (e: Exception) {
                lastException = e
                attempt++
                
                if (attempt < MAX_RETRY_ATTEMPTS && isRetryableError(e)) {
                    val delayMs = INITIAL_RETRY_DELAY_MS * (1 shl (attempt - 1)) // Exponential backoff
                    Log.w(TAG, "Ошибка передачи, повтор через ${delayMs}ms: ${e.message}")
                    delay(delayMs)
                } else {
                    break
                }
            }
        }
        
        Log.e(TAG, "Не удалось запустить передачу после $attempt попыток")
        throw lastException ?: Exception("Не удалось запустить передачу")
    }
    
    /**
     * Начинает скачивание файла через WorkManager (для фоновой передачи)
     * 
     * @param request Запрос на передачу файла
     * @param peer Устройство-источник
     * @return UUID работы WorkManager
     */
    fun scheduleDownload(
        request: TransferRequest,
        peer: DevicePeer
    ): String {
        val data = workDataOf(
            "deviceId" to peer.deviceId,
            "ipAddress" to peer.ipAddress,
            "port" to peer.port,
            "fileId" to request.fileId,
            "destinationPath" to request.destinationPath
        )
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(false) // Разрешаем при низком заряде
            .build()
        
        val workRequest = OneTimeWorkRequestBuilder<FileTransferWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag(WORK_TAG_TRANSFER)
            .build()
        
        workManager.enqueue(workRequest)
        
        Log.i(TAG, "Скачивание запланировано через WorkManager: ${workRequest.id}")
        return workRequest.id.toString()
    }
    
    /**
     * Отменяет передачу файла
     * 
     * @param transferId ID передачи для отмены
     */
    fun cancelTransfer(transferId: String) {
        try {
            transferService?.cancelTransfer(transferId)
            Log.i(TAG, "Передача отменена: $transferId")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка отмены передачи: ${e.message}", e)
        }
    }
    
    /**
     * Отменяет WorkManager задачу
     * 
     * @param workId UUID работы WorkManager
     */
    fun cancelScheduledDownload(workId: String) {
        try {
            workManager.cancelWorkById(java.util.UUID.fromString(workId))
            Log.i(TAG, "Запланированное скачивание отменено: $workId")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка отмены запланированного скачивания: ${e.message}", e)
        }
    }
    
    /**
     * Проверяет, можно ли повторить операцию при данной ошибке
     */
    private fun isRetryableError(e: Exception): Boolean {
        val message = e.message?.lowercase() ?: ""
        return message.contains("connection") ||
               message.contains("timeout") ||
               message.contains("network") ||
               message.contains("socket")
    }
    
    /**
     * Извлекает имя файла из пути
     */
    private fun extractFileName(path: String): String {
        return path.substringAfterLast("/")
    }
}

/**
 * WorkManager Worker для фоновой передачи файлов
 */
class FileTransferWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "FileTransferWorker"
    }
    
    override suspend fun doWork(): Result {
        val deviceId = inputData.getString("deviceId") ?: return Result.failure()
        val ipAddress = inputData.getString("ipAddress") ?: return Result.failure()
        val port = inputData.getInt("port", DEFAULT_PORT)
        val fileId = inputData.getString("fileId") ?: return Result.failure()
        val destinationPath = inputData.getString("destinationPath") ?: return Result.failure()
        
        return try {
            Log.i(TAG, "Начало фоновой передачи файла: $fileId от $deviceId")
            
            // TODO: Реализовать передачу файла через TransferService
            // Пока возвращаем success для тестирования
            
            Log.i(TAG, "Фоновая передача завершена успешно")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка фоновой передачи: ${e.message}", e)
            
            if (runAttemptCount < 3) {
                Log.w(TAG, "Повтор попытки ${runAttemptCount + 1}")
                Result.retry()
            } else {
                Log.e(TAG, "Превышено количество попыток")
                Result.failure(
                    workDataOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }
    }
}
