/*
 * Сервис передачи файлов через TCP
 * 
 * Архитектурное обоснование:
 * - Foreground Service для надежной передачи файлов в фоновом режиме
 * - TCP обеспечивает надежную доставку бинарных данных файлов
 * - Серверная часть принимает входящие запросы на скачивание (uploads)
 * - Клиентская часть инициирует скачивание файлов с других устройств (downloads)
 * - StateFlow для отслеживания прогресса всех активных передач
 * 
 * Технологические решения:
 * - TCP выбран для гарантированной доставки файлов без потерь
 * - JSON-over-TCP для управляющих команд (HANDSHAKE, TRANSFER_REQUEST, etc.)
 * - Бинарная передача данных блоками для эффективности
 * - Корутины для параллельных передач множества файлов
 * - Буферизованные потоки для оптимизации производительности
 * - Structured concurrency для правильной отмены передач
 * - Уведомления для отображения прогресса передачи
 * 
 * Протокол передачи (согласно PROTOCOL.md):
 * 1. HANDSHAKE - идентификация клиента
 * 2. LIST_FILES или TRANSFER_REQUEST
 * 3. TRANSFER_START - начало передачи
 * 4. Бинарные данные + периодические TRANSFER_PROGRESS
 * 5. TRANSFER_COMPLETE или TRANSFER_ERROR
 * 
 * Почему Foreground Service:
 * - Передача файлов может занимать длительное время
 * - Система не должна убивать процесс во время передачи
 * - Пользователь должен видеть прогресс через уведомления
 */
package com.example.composeapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.composeapp.domain.model.SharedFile
import com.example.composeapp.domain.model.TransferProgress
import com.example.composeapp.domain.model.TransferState
import com.example.composeapp.p2p.protocol.*
import com.example.composeapp.p2p.tcp.TcpConnection
import com.example.composeapp.p2p.tcp.TcpServer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.Socket
import java.util.UUID

/**
 * Foreground сервис для передачи файлов через TCP
 */
class FileTransferService : Service() {
    
    companion object {
        private const val TAG = "FileTransferService"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "file_transfer_channel"
        private const val CHUNK_SIZE = 8192
        private const val PROGRESS_UPDATE_INTERVAL = 10 // обновлять прогресс каждые 10 блоков
        
        /**
         * Запускает сервис передачи файлов
         */
        fun start(
            context: Context,
            deviceId: String,
            nickname: String,
            sharedFolderPath: String,
            port: Int = DEFAULT_PORT
        ) {
            val intent = Intent(context, FileTransferService::class.java).apply {
                putExtra("deviceId", deviceId)
                putExtra("nickname", nickname)
                putExtra("sharedFolderPath", sharedFolderPath)
                putExtra("port", port)
            }
            context.startForegroundService(intent)
        }
        
        /**
         * Останавливает сервис передачи файлов
         */
        fun stop(context: Context) {
            val intent = Intent(context, FileTransferService::class.java)
            context.stopService(intent)
        }
    }
    
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private var tcpServer: TcpServer? = null
    private var deviceId: String? = null
    private var nickname: String? = null
    private var sharedFolderPath: String? = null
    private var port: Int = DEFAULT_PORT
    
    private val _transfers = MutableStateFlow<List<TransferProgress>>(emptyList())
    val transfers: StateFlow<List<TransferProgress>> = _transfers.asStateFlow()
    
    private val activeTransferJobs = mutableMapOf<String, Job>()
    
    /**
     * Binder для локального доступа к сервису
     */
    inner class LocalBinder : Binder() {
        fun getService(): FileTransferService = this@FileTransferService
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Сервис передачи файлов создан")
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Сервис передачи файлов запущен")
        
        deviceId = intent?.getStringExtra("deviceId")
        nickname = intent?.getStringExtra("nickname")
        sharedFolderPath = intent?.getStringExtra("sharedFolderPath")
        port = intent?.getIntExtra("port", DEFAULT_PORT) ?: DEFAULT_PORT
        
        startForeground(NOTIFICATION_ID, createNotification())
        
        startTcpServer()
        
        return START_STICKY
    }
    
    /**
     * Запускает TCP сервер для приема входящих соединений
     */
    private fun startTcpServer() {
        try {
            tcpServer = TcpServer(port, object : TcpServer.ConnectionHandler {
                override suspend fun onConnection(connection: TcpConnection) {
                    handleIncomingConnection(connection)
                }
            })
            
            tcpServer?.start(serviceScope)
            Log.i(TAG, "TCP сервер запущен на порту $port")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка запуска TCP сервера: ${e.message}", e)
        }
    }
    
    /**
     * Обрабатывает входящее соединение от клиента
     */
    private suspend fun handleIncomingConnection(connection: TcpConnection) = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Новое входящее соединение от ${connection.remoteAddress}")
            
            // Ждем HANDSHAKE
            val handshake = connection.receiveMessage<HandshakeMessage>()
            if (handshake == null || handshake.first != MessageType.HANDSHAKE) {
                Log.w(TAG, "Не получен HANDSHAKE, закрытие соединения")
                return@withContext
            }
            
            val handshakeData = handshake.second
            Log.i(TAG, "HANDSHAKE от ${handshakeData.nickname} (${handshakeData.deviceId})")
            
            // Отправляем HANDSHAKE_ACK
            connection.sendMessage(
                MessageType.HANDSHAKE_ACK,
                HandshakeAckMessage(
                    deviceId = deviceId ?: "unknown",
                    nickname = nickname ?: "Unknown Device"
                )
            )
            
            // Обрабатываем запросы клиента
            while (connection.isConnected) {
                val message = connection.receiveMessage<String>() ?: break
                val messageType = MessageSerializer.parseType(message.second)
                
                when (messageType) {
                    MessageType.LIST_FILES -> handleListFilesRequest(connection)
                    MessageType.TRANSFER_REQUEST -> handleTransferRequest(connection, message.second)
                    MessageType.PING -> handlePing(connection, message.second)
                    MessageType.CANCEL_TRANSFER -> handleCancelTransfer(connection, message.second)
                    else -> Log.w(TAG, "Неизвестный тип сообщения: $messageType")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обработки соединения: ${e.message}", e)
        } finally {
            connection.close()
        }
    }
    
    /**
     * Обрабатывает запрос списка файлов
     */
    private suspend fun handleListFilesRequest(connection: TcpConnection) {
        try {
            val files = getSharedFiles()
            val fileInfos = files.map { file ->
                FileInfo(
                    fileId = file.fileId,
                    name = file.name,
                    size = file.size,
                    mimeType = file.mimeType,
                    relativePath = file.relativePath,
                    lastModified = file.lastModified
                )
            }
            
            connection.sendMessage(
                MessageType.FILE_LIST,
                FileListMessage(files = fileInfos)
            )
            
            Log.i(TAG, "Отправлен список файлов: ${files.size} файлов")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка отправки списка файлов: ${e.message}", e)
        }
    }
    
    /**
     * Обрабатывает запрос на передачу файла
     */
    private suspend fun handleTransferRequest(connection: TcpConnection, messageJson: String) {
        try {
            val (_, request) = MessageSerializer.deserialize<TransferRequestMessage>(messageJson)
            Log.i(TAG, "Запрос на передачу файла: ${request.fileId}")
            
            val file = findFileById(request.fileId)
            if (file == null) {
                connection.sendMessage(
                    MessageType.TRANSFER_ERROR,
                    TransferErrorMessage(
                        transferId = request.transferId,
                        errorCode = ErrorCodes.FILE_NOT_FOUND,
                        message = "Файл не найден"
                    )
                )
                return
            }
            
            val fileHandle = File(sharedFolderPath, file.relativePath)
            if (!fileHandle.exists() || !fileHandle.canRead()) {
                connection.sendMessage(
                    MessageType.TRANSFER_ERROR,
                    TransferErrorMessage(
                        transferId = request.transferId,
                        errorCode = ErrorCodes.PERMISSION_DENIED,
                        message = "Нет доступа к файлу"
                    )
                )
                return
            }
            
            // Отправляем TRANSFER_START
            connection.sendMessage(
                MessageType.TRANSFER_START,
                TransferStartMessage(
                    transferId = request.transferId,
                    fileId = file.fileId,
                    fileName = file.name,
                    fileSize = file.size,
                    chunkSize = CHUNK_SIZE
                )
            )
            
            // Передаем файл
            transferFileToClient(connection, fileHandle, request.transferId, file.size)
            
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обработки запроса передачи: ${e.message}", e)
        }
    }
    
    /**
     * Передает файл клиенту
     */
    private suspend fun transferFileToClient(
        connection: TcpConnection,
        file: File,
        transferId: String,
        fileSize: Long
    ) = withContext(Dispatchers.IO) {
        try {
            val buffer = ByteArray(CHUNK_SIZE)
            var bytesTransferred = 0L
            var chunkCount = 0
            
            FileInputStream(file).use { input ->
                while (true) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break
                    
                    connection.sendBinaryData(buffer, 0, bytesRead)
                    bytesTransferred += bytesRead
                    chunkCount++
                    
                    // Отправляем прогресс каждые N блоков
                    if (chunkCount % PROGRESS_UPDATE_INTERVAL == 0) {
                        val percentage = (bytesTransferred.toDouble() / fileSize) * 100
                        connection.sendMessage(
                            MessageType.TRANSFER_PROGRESS,
                            TransferProgressMessage(
                                transferId = transferId,
                                bytesTransferred = bytesTransferred,
                                totalBytes = fileSize,
                                percentage = percentage
                            )
                        )
                    }
                }
            }
            
            // Отправляем TRANSFER_COMPLETE
            connection.sendMessage(
                MessageType.TRANSFER_COMPLETE,
                TransferCompleteMessage(
                    transferId = transferId,
                    fileId = file.name
                )
            )
            
            Log.i(TAG, "Файл передан успешно: ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка передачи файла: ${e.message}", e)
            connection.sendMessage(
                MessageType.TRANSFER_ERROR,
                TransferErrorMessage(
                    transferId = transferId,
                    errorCode = ErrorCodes.CONNECTION_LOST,
                    message = "Ошибка передачи: ${e.message}"
                )
            )
        }
    }
    
    /**
     * Обрабатывает ping
     */
    private suspend fun handlePing(connection: TcpConnection, messageJson: String) {
        try {
            val (_, ping) = MessageSerializer.deserialize<PingMessage>(messageJson)
            connection.sendMessage(MessageType.PONG, PongMessage(ping.timestamp))
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обработки ping: ${e.message}", e)
        }
    }
    
    /**
     * Обрабатывает отмену передачи
     */
    private suspend fun handleCancelTransfer(connection: TcpConnection, messageJson: String) {
        try {
            val (_, cancel) = MessageSerializer.deserialize<CancelTransferMessage>(messageJson)
            activeTransferJobs[cancel.transferId]?.cancel()
            
            connection.sendMessage(
                MessageType.TRANSFER_CANCELLED,
                TransferCancelledMessage(transferId = cancel.transferId)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка отмены передачи: ${e.message}", e)
        }
    }
    
    /**
     * Начинает скачивание файла с удаленного устройства
     */
    fun startDownload(
        remoteAddress: String,
        remotePort: Int,
        fileId: String,
        fileName: String,
        destinationPath: String
    ): String {
        val transferId = UUID.randomUUID().toString()
        
        val transfer = TransferProgress(
            transferId = transferId,
            fileId = fileId,
            fileName = fileName,
            state = TransferState.Pending
        )
        
        updateTransfers { it + transfer }
        
        val job = serviceScope.launch {
            try {
                downloadFile(remoteAddress, remotePort, fileId, transferId, fileName, destinationPath)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка скачивания файла: ${e.message}", e)
                updateTransfer(transferId) { it.copy(state = TransferState.Error(e.message ?: "Unknown error")) }
            }
        }
        
        activeTransferJobs[transferId] = job
        
        return transferId
    }
    
    /**
     * Скачивает файл с удаленного устройства
     */
    private suspend fun downloadFile(
        remoteAddress: String,
        remotePort: Int,
        fileId: String,
        transferId: String,
        fileName: String,
        destinationPath: String
    ) = withContext(Dispatchers.IO) {
        val socket = Socket(remoteAddress, remotePort)
        val connection = TcpConnection(socket)
        
        try {
            // Отправляем HANDSHAKE
            connection.sendMessage(
                MessageType.HANDSHAKE,
                HandshakeMessage(
                    deviceId = deviceId ?: "unknown",
                    nickname = nickname ?: "Unknown Device"
                )
            )
            
            // Ждем HANDSHAKE_ACK
            val ack = connection.receiveMessage<HandshakeAckMessage>()
            if (ack == null || ack.first != MessageType.HANDSHAKE_ACK) {
                throw Exception("Не получен HANDSHAKE_ACK")
            }
            
            // Отправляем TRANSFER_REQUEST
            connection.sendMessage(
                MessageType.TRANSFER_REQUEST,
                TransferRequestMessage(fileId = fileId, transferId = transferId)
            )
            
            // Ждем TRANSFER_START
            val startMsg = connection.receiveMessage<TransferStartMessage>()
            if (startMsg == null || startMsg.first != MessageType.TRANSFER_START) {
                throw Exception("Не получен TRANSFER_START")
            }
            
            val transferStart = startMsg.second
            val fileSize = transferStart.fileSize
            
            // Получаем файл
            receiveFileFromServer(connection, destinationPath, transferId, fileName, fileSize)
            
        } finally {
            connection.close()
            activeTransferJobs.remove(transferId)
        }
    }
    
    /**
     * Получает файл от сервера
     */
    private suspend fun receiveFileFromServer(
        connection: TcpConnection,
        destinationPath: String,
        transferId: String,
        fileName: String,
        fileSize: Long
    ) = withContext(Dispatchers.IO) {
        val outputFile = File(destinationPath, fileName)
        val buffer = ByteArray(CHUNK_SIZE)
        var bytesReceived = 0L
        val startTime = System.currentTimeMillis()
        
        FileOutputStream(outputFile).use { output ->
            while (bytesReceived < fileSize) {
                val bytesRead = connection.receiveBinaryData(buffer)
                if (bytesRead == -1) break
                
                output.write(buffer, 0, bytesRead)
                bytesReceived += bytesRead
                
                // Обновляем прогресс
                val elapsed = System.currentTimeMillis() - startTime
                val speed = if (elapsed > 0) (bytesReceived * 1000) / elapsed else 0
                
                updateTransfer(transferId) { transfer ->
                    transfer.copy(
                        state = TransferState.InProgress(
                            bytesTransferred = bytesReceived,
                            totalBytes = fileSize,
                            speedBytesPerSecond = speed
                        )
                    )
                }
            }
        }
        
        // Ждем TRANSFER_COMPLETE
        val completeMsg = connection.receiveMessage<TransferCompleteMessage>()
        if (completeMsg != null && completeMsg.first == MessageType.TRANSFER_COMPLETE) {
            updateTransfer(transferId) { it.copy(state = TransferState.Completed(outputFile.absolutePath)) }
            
            // Отправляем ACK
            connection.sendMessage(MessageType.TRANSFER_ACK, TransferAckMessage(transferId))
            
            Log.i(TAG, "Файл скачан успешно: $fileName")
        } else {
            throw Exception("Не получен TRANSFER_COMPLETE")
        }
    }
    
    /**
     * Отменяет передачу файла
     */
    fun cancelTransfer(transferId: String) {
        activeTransferJobs[transferId]?.cancel()
        activeTransferJobs.remove(transferId)
        updateTransfer(transferId) { it.copy(state = TransferState.Cancelled) }
    }
    
    /**
     * Получает список файлов из общей папки
     */
    private fun getSharedFiles(): List<SharedFile> {
        val folder = File(sharedFolderPath ?: return emptyList())
        if (!folder.exists() || !folder.isDirectory) return emptyList()
        
        val files = mutableListOf<SharedFile>()
        folder.walkTopDown().forEach { file ->
            if (file.isFile) {
                val relativePath = file.relativeTo(folder).path
                files.add(
                    SharedFile(
                        fileId = UUID.nameUUIDFromBytes(file.absolutePath.toByteArray()).toString(),
                        name = file.name,
                        size = file.length(),
                        mimeType = getMimeType(file),
                        relativePath = relativePath,
                        lastModified = file.lastModified()
                    )
                )
            }
        }
        return files
    }
    
    /**
     * Находит файл по ID
     */
    private fun findFileById(fileId: String): SharedFile? {
        return getSharedFiles().find { it.fileId == fileId }
    }
    
    /**
     * Определяет MIME тип файла
     */
    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "jpg", "jpeg", "png", "gif" -> "image/${file.extension}"
            "mp4", "avi", "mkv" -> "video/${file.extension}"
            "mp3", "wav", "flac" -> "audio/${file.extension}"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
    
    /**
     * Обновляет список передач
     */
    private fun updateTransfers(transform: (List<TransferProgress>) -> List<TransferProgress>) {
        _transfers.value = transform(_transfers.value)
    }
    
    /**
     * Обновляет конкретную передачу
     */
    private fun updateTransfer(transferId: String, transform: (TransferProgress) -> TransferProgress) {
        updateTransfers { transfers ->
            transfers.map { if (it.transferId == transferId) transform(it) else it }
        }
    }
    
    /**
     * Создает notification channel
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "File Transfers",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Передача файлов между устройствами"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Создает уведомление
     */
    private fun createNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("File Transfer Service")
                .setContentText("Готов к передаче файлов")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("File Transfer Service")
                .setContentText("Готов к передаче файлов")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .build()
        }
    }
    
    override fun onDestroy() {
        Log.i(TAG, "Остановка сервиса передачи файлов")
        
        // Отменяем все активные передачи
        activeTransferJobs.values.forEach { it.cancel() }
        activeTransferJobs.clear()
        
        // Останавливаем TCP сервер
        tcpServer?.close()
        tcpServer = null
        
        // Отменяем scope
        serviceScope.cancel()
        
        super.onDestroy()
    }
}
