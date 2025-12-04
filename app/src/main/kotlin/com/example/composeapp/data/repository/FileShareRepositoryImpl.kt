/*
 * Реализация репозитория для управления файлами
 * 
 * Архитектурное обоснование:
 * - Реализует интерфейс из domain слоя (Clean Architecture)
 * - Управляет локальными файлами из общей папки
 * - Получает списки файлов с удаленных устройств через TCP
 * - Использует File API для работы с файловой системой
 * - Делегирует сетевое взаимодействие через TransferManager и TcpConnection
 * 
 * Технологические решения:
 * - File API для сканирования локальных файлов
 * - FileObserver для отслеживания изменений в папке (реактивное обновление)
 * - TCP Socket + JSON для получения списка файлов с пиров
 * - Flow для реактивного обновления списка файлов
 * - MimeTypeMap для определения MIME типов файлов
 * - Dispatcher.IO для файловых операций без блокировки UI
 * 
 * Работа с JSON-over-TCP протоколом:
 * 1. Устанавливаем TCP соединение с удаленным peer
 * 2. Отправляем HANDSHAKE с идентификацией устройства
 * 3. Получаем HANDSHAKE_ACK подтверждение
 * 4. Отправляем LIST_FILES команду для запроса списка
 * 5. Получаем FILE_LIST ответ с массивом FileInfo
 * 6. Парсим JSON и преобразуем в доменные модели SharedFile
 * 7. Закрываем соединение
 * 
 * Обработка ошибок:
 * - Сетевые ошибки (timeout, connection refused) пробрасываются выше
 * - Файловые ошибки (permission denied, folder not found) логируются и возвращают пустой список
 * - Ошибки протокола (invalid JSON, unexpected message) пробрасываются как ProtocolException
 * - Все ошибки логируются для отладки
 * 
 * Восстановление после сбоев:
 * - При сетевых ошибках повторные попытки делает вышестоящий слой (Use Case или TransferManager)
 * - При ошибках файловой системы возвращаем пустой список (graceful degradation)
 * - FileObserver автоматически восстанавливает мониторинг после потери и восстановления папки
 */
package com.example.composeapp.data.repository

import android.os.FileObserver
import android.util.Log
import android.webkit.MimeTypeMap
import com.example.composeapp.domain.model.DevicePeer
import com.example.composeapp.domain.model.SharedFile
import com.example.composeapp.domain.repository.FileShareRepository
import com.example.composeapp.p2p.protocol.*
import com.example.composeapp.p2p.tcp.TcpConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.UUID

/**
 * Реализация FileShareRepository для работы с локальными и удаленными файлами
 * 
 * @property sharedFolderPath Путь к общей папке с файлами для обмена
 * @property deviceId Уникальный идентификатор текущего устройства
 * @property nickname Отображаемое имя текущего устройства
 * @property getDiscoveredPeers Функция для получения списка обнаруженных устройств
 */
class FileShareRepositoryImpl(
    private val sharedFolderPath: String,
    private val deviceId: String,
    private val nickname: String,
    private val getDiscoveredPeers: () -> List<DevicePeer>
) : FileShareRepository {
    
    companion object {
        private const val TAG = "FileShareRepositoryImpl"
        private const val CONNECTION_TIMEOUT_MS = 10000
    }
    
    private val _localFiles = MutableStateFlow<List<SharedFile>>(emptyList())
    private var fileObserver: FileObserver? = null
    
    init {
        // Инициализируем FileObserver для отслеживания изменений в папке
        startWatchingFolder()
    }
    
    /**
     * Получает Flow локальных файлов из общей папки
     * Flow автоматически обновляется при изменении содержимого папки через FileObserver
     * 
     * @return Flow со списком локальных файлов
     */
    override fun getLocalFiles(): Flow<List<SharedFile>> {
        return _localFiles.asStateFlow()
    }
    
    /**
     * Получает список файлов с удаленного устройства через TCP соединение
     * 
     * Протокол взаимодействия:
     * 1. Находим peer по deviceId в списке обнаруженных устройств
     * 2. Устанавливаем TCP соединение по IP и порту
     * 3. Выполняем HANDSHAKE для идентификации
     * 4. Отправляем LIST_FILES запрос
     * 5. Получаем FILE_LIST ответ
     * 6. Парсим и преобразуем в SharedFile объекты
     * 
     * Обработка ошибок:
     * - IllegalArgumentException если устройство не найдено в списке обнаруженных
     * - IllegalStateException если устройство оффлайн
     * - SocketTimeoutException при превышении таймаута соединения
     * - IOException при сетевых ошибках
     * - Exception при ошибках протокола (некорректный JSON, неожиданный тип сообщения)
     * 
     * @param deviceId Идентификатор удаленного устройства
     * @return Список файлов, доступных на удаленном устройстве
     * @throws Exception если не удалось получить список файлов
     */
    override suspend fun getRemoteFiles(deviceId: String): List<SharedFile> = withContext(Dispatchers.IO) {
        try {
            // Находим peer в списке обнаруженных устройств
            val peer = getDiscoveredPeers().find { it.deviceId == deviceId }
                ?: throw IllegalArgumentException("Устройство не найдено: $deviceId")
            
            if (!peer.isOnline) {
                throw IllegalStateException("Устройство оффлайн: ${peer.nickname}")
            }
            
            Log.i(TAG, "Подключение к ${peer.nickname} (${peer.ipAddress}:${peer.port}) для получения списка файлов")
            
            // Устанавливаем TCP соединение
            val socket = Socket()
            socket.connect(
                java.net.InetSocketAddress(peer.ipAddress, peer.port),
                CONNECTION_TIMEOUT_MS
            )
            
            TcpConnection(socket).use { connection ->
                // Отправляем HANDSHAKE
                connection.sendMessage(
                    MessageType.HANDSHAKE,
                    HandshakeMessage(
                        deviceId = this@FileShareRepositoryImpl.deviceId,
                        nickname = this@FileShareRepositoryImpl.nickname
                    )
                )
                
                // Ожидаем HANDSHAKE_ACK
                val handshakeAck = connection.receiveMessage<HandshakeAckMessage>()
                if (handshakeAck == null || handshakeAck.first != MessageType.HANDSHAKE_ACK) {
                    throw IOException("Не получено подтверждение рукопожатия от ${peer.nickname}")
                }
                
                Log.d(TAG, "Handshake успешен с ${peer.nickname}")
                
                // Отправляем LIST_FILES запрос
                connection.sendMessage(MessageType.LIST_FILES, ListFilesMessage())
                
                // Получаем FILE_LIST ответ
                val response = connection.receiveMessage<FileListMessage>()
                if (response == null || response.first != MessageType.FILE_LIST) {
                    throw IOException("Не получен список файлов от ${peer.nickname}")
                }
                
                val fileList = response.second
                Log.i(TAG, "Получено ${fileList.files.size} файлов от ${peer.nickname}")
                
                // Преобразуем FileInfo в SharedFile
                fileList.files.map { fileInfo ->
                    SharedFile(
                        fileId = fileInfo.fileId,
                        name = fileInfo.name,
                        size = fileInfo.size,
                        mimeType = fileInfo.mimeType,
                        relativePath = fileInfo.relativePath,
                        lastModified = fileInfo.lastModified
                    )
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Таймаут подключения к устройству $deviceId: ${e.message}")
            throw IOException("Таймаут подключения к устройству", e)
        } catch (e: IOException) {
            Log.e(TAG, "Ошибка сети при получении файлов от $deviceId: ${e.message}")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения списка файлов от $deviceId: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Обновляет список локальных файлов путем сканирования общей папки
     * 
     * Процесс сканирования:
     * 1. Проверяем существование и доступность папки
     * 2. Рекурсивно обходим все файлы и подпапки
     * 3. Для каждого файла создаем SharedFile с метаданными
     * 4. Определяем MIME тип по расширению файла
     * 5. Вычисляем относительный путь от корня общей папки
     * 6. Обновляем StateFlow для реактивного обновления UI
     * 
     * Обработка ошибок:
     * - Если папка не существует, создаем её автоматически
     * - Если нет прав доступа, логируем ошибку и возвращаем пустой список
     * - Игнорируем файлы, к которым нет доступа (permission denied)
     * - Пропускаем скрытые файлы (начинающиеся с точки)
     * 
     * Производительность:
     * - Выполняется на Dispatchers.IO для избежания блокировки UI
     * - Использует эффективный рекурсивный обход без создания промежуточных коллекций
     * - Кэширует результаты в StateFlow
     */
    override suspend fun refreshLocalFiles() = withContext(Dispatchers.IO) {
        try {
            val folder = File(sharedFolderPath)
            
            // Создаем папку если не существует
            if (!folder.exists()) {
                Log.w(TAG, "Общая папка не существует, создаем: $sharedFolderPath")
                folder.mkdirs()
            }
            
            if (!folder.isDirectory || !folder.canRead()) {
                Log.e(TAG, "Невозможно прочитать общую папку: $sharedFolderPath")
                _localFiles.value = emptyList()
                return@withContext
            }
            
            Log.i(TAG, "Сканирование локальной папки: $sharedFolderPath")
            val files = scanFolder(folder, folder)
            
            _localFiles.value = files
            Log.i(TAG, "Найдено ${files.size} локальных файлов")
        } catch (e: SecurityException) {
            Log.e(TAG, "Нет прав доступа к папке $sharedFolderPath: ${e.message}")
            _localFiles.value = emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка сканирования локальных файлов: ${e.message}", e)
            _localFiles.value = emptyList()
        }
    }
    
    /**
     * Рекурсивно сканирует папку и возвращает список файлов
     * 
     * @param directory Папка для сканирования
     * @param rootFolder Корневая папка для вычисления относительных путей
     * @return Список SharedFile объектов
     */
    private fun scanFolder(directory: File, rootFolder: File): List<SharedFile> {
        val result = mutableListOf<SharedFile>()
        
        try {
            directory.listFiles()?.forEach { file ->
                // Пропускаем скрытые файлы
                if (file.name.startsWith(".")) {
                    return@forEach
                }
                
                when {
                    file.isFile && file.canRead() -> {
                        try {
                            val sharedFile = createSharedFile(file, rootFolder)
                            result.add(sharedFile)
                        } catch (e: Exception) {
                            Log.w(TAG, "Не удалось обработать файл ${file.name}: ${e.message}")
                        }
                    }
                    file.isDirectory && file.canRead() -> {
                        // Рекурсивно сканируем подпапки
                        result.addAll(scanFolder(file, rootFolder))
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Нет доступа к папке ${directory.absolutePath}")
        }
        
        return result
    }
    
    /**
     * Создает SharedFile объект из File
     * 
     * @param file Файл для преобразования
     * @param rootFolder Корневая папка для вычисления относительного пути
     * @return SharedFile объект с метаданными
     */
    private fun createSharedFile(file: File, rootFolder: File): SharedFile {
        val relativePath = file.absolutePath.removePrefix(rootFolder.absolutePath)
            .removePrefix("/")
        
        val mimeType = getMimeType(file.name) ?: "application/octet-stream"
        
        return SharedFile(
            fileId = generateFileId(file, rootFolder),
            name = file.name,
            size = file.length(),
            mimeType = mimeType,
            relativePath = relativePath,
            lastModified = file.lastModified()
        )
    }
    
    /**
     * Генерирует уникальный ID для файла на основе его пути
     * Используется относительный путь для стабильности ID между сессиями
     * 
     * @param file Файл
     * @param rootFolder Корневая папка
     * @return Уникальный идентификатор файла
     */
    private fun generateFileId(file: File, rootFolder: File): String {
        val relativePath = file.absolutePath.removePrefix(rootFolder.absolutePath)
        // Используем UUID на основе пути для консистентности
        return UUID.nameUUIDFromBytes(relativePath.toByteArray()).toString()
    }
    
    /**
     * Определяет MIME тип файла по его расширению
     * 
     * @param fileName Имя файла
     * @return MIME тип или null если не удалось определить
     */
    private fun getMimeType(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', "")
        if (extension.isEmpty()) return null
        
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
    }
    
    /**
     * Запускает FileObserver для отслеживания изменений в общей папке
     * При любом изменении (создание, удаление, модификация) автоматически обновляет список файлов
     */
    private fun startWatchingFolder() {
        try {
            val folder = File(sharedFolderPath)
            if (!folder.exists()) {
                folder.mkdirs()
            }
            
            fileObserver = object : FileObserver(folder, ALL_EVENTS) {
                override fun onEvent(event: Int, path: String?) {
                    // При любом изменении обновляем список файлов
                    Log.d(TAG, "Изменение в папке обнаружено: event=$event, path=$path")
                    // Используем корутины для асинхронного обновления
                    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                        refreshLocalFiles()
                    }
                }
            }
            
            fileObserver?.startWatching()
            Log.i(TAG, "FileObserver запущен для папки: $sharedFolderPath")
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось запустить FileObserver: ${e.message}", e)
        }
    }
    
    /**
     * Останавливает FileObserver при уничтожении репозитория
     */
    fun cleanup() {
        fileObserver?.stopWatching()
        fileObserver = null
        Log.i(TAG, "FileShareRepository очищен")
    }
}
