/*
 * Модели сообщений протокола P2P передачи файлов
 * 
 * Архитектурное обоснование:
 * - Определяет структуру JSON сообщений для управления передачей файлов
 * - Использует kotlinx.serialization для автоматической (де)сериализации
 * - Sealed class обеспечивает типобезопасность сообщений
 * 
 * Технологические решения:
 * - JSON выбран для читаемости, отладки и расширяемости протокола
 * - Sealed class позволяет исчерпывающую обработку всех типов сообщений (when expression)
 * - @Serializable генерирует эффективный код сериализации/десериализации
 * - Протокол версионируется для обеспечения совместимости между версиями приложения
 * 
 * Формат сообщений:
 * Все сообщения передаются как JSON с завершающим символом новой строки (\n)
 * Структура: {"type": "MESSAGE_TYPE", "data": {...}}
 */
package com.example.composeapp.p2p.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Базовая обертка для всех протокольных сообщений
 */
@Serializable
data class MessageEnvelope(
    val type: String,
    val data: String
)

/**
 * Версия протокола для проверки совместимости
 */
const val PROTOCOL_VERSION = "1.0"

/**
 * Порт по умолчанию для TCP соединений
 */
const val DEFAULT_PORT = 8888

/**
 * Имя mDNS сервиса для обнаружения устройств
 */
const val MDNS_SERVICE_TYPE = "_p2p-file-share._tcp."

/**
 * Сообщение рукопожатия при установлении соединения
 */
@Serializable
data class HandshakeMessage(
    val deviceId: String,
    val nickname: String,
    val protocolVersion: String = PROTOCOL_VERSION
)

/**
 * Подтверждение рукопожатия
 */
@Serializable
data class HandshakeAckMessage(
    val deviceId: String,
    val nickname: String,
    val status: String = "accepted"
)

/**
 * Запрос списка файлов (пустое тело)
 */
@Serializable
class ListFilesMessage

/**
 * Информация о файле в списке
 */
@Serializable
data class FileInfo(
    val fileId: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val relativePath: String,
    val lastModified: Long
)

/**
 * Ответ со списком файлов
 */
@Serializable
data class FileListMessage(
    val files: List<FileInfo>
)

/**
 * Запрос на передачу файла
 */
@Serializable
data class TransferRequestMessage(
    val fileId: String,
    val transferId: String
)

/**
 * Начало передачи файла
 */
@Serializable
data class TransferStartMessage(
    val transferId: String,
    val fileId: String,
    val fileName: String,
    val fileSize: Long,
    val chunkSize: Int = 8192
)

/**
 * Обновление прогресса передачи
 */
@Serializable
data class TransferProgressMessage(
    val transferId: String,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val percentage: Double
)

/**
 * Завершение передачи файла
 */
@Serializable
data class TransferCompleteMessage(
    val transferId: String,
    val fileId: String,
    val checksum: String? = null
)

/**
 * Подтверждение получения файла
 */
@Serializable
data class TransferAckMessage(
    val transferId: String,
    val status: String = "completed"
)

/**
 * Ошибка при передаче
 */
@Serializable
data class TransferErrorMessage(
    val transferId: String,
    val errorCode: String,
    val message: String
)

/**
 * Коды ошибок протокола
 */
object ErrorCodes {
    const val FILE_NOT_FOUND = "FILE_NOT_FOUND"
    const val PERMISSION_DENIED = "PERMISSION_DENIED"
    const val STORAGE_FULL = "STORAGE_FULL"
    const val CONNECTION_LOST = "CONNECTION_LOST"
    const val INVALID_REQUEST = "INVALID_REQUEST"
    const val TRANSFER_CANCELLED = "TRANSFER_CANCELLED"
}

/**
 * Отмена передачи
 */
@Serializable
data class CancelTransferMessage(
    val transferId: String
)

/**
 * Подтверждение отмены
 */
@Serializable
data class TransferCancelledMessage(
    val transferId: String
)

/**
 * Ping для проверки соединения
 */
@Serializable
data class PingMessage(
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Pong ответ на ping
 */
@Serializable
data class PongMessage(
    val timestamp: Long
)

/**
 * Типы сообщений протокола
 */
object MessageType {
    const val HANDSHAKE = "HANDSHAKE"
    const val HANDSHAKE_ACK = "HANDSHAKE_ACK"
    const val LIST_FILES = "LIST_FILES"
    const val FILE_LIST = "FILE_LIST"
    const val TRANSFER_REQUEST = "TRANSFER_REQUEST"
    const val TRANSFER_START = "TRANSFER_START"
    const val TRANSFER_PROGRESS = "TRANSFER_PROGRESS"
    const val TRANSFER_COMPLETE = "TRANSFER_COMPLETE"
    const val TRANSFER_ACK = "TRANSFER_ACK"
    const val TRANSFER_ERROR = "TRANSFER_ERROR"
    const val CANCEL_TRANSFER = "CANCEL_TRANSFER"
    const val TRANSFER_CANCELLED = "TRANSFER_CANCELLED"
    const val PING = "PING"
    const val PONG = "PONG"
}

/**
 * Утилита для сериализации/десериализации сообщений
 */
object MessageSerializer {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }
    
    /**
     * Сериализует сообщение в JSON строку
     */
    inline fun <reified T> serialize(type: String, data: T): String {
        val dataJson = json.encodeToString(kotlinx.serialization.serializer(), data)
        val envelope = MessageEnvelope(type, dataJson)
        return json.encodeToString(MessageEnvelope.serializer(), envelope)
    }
    
    /**
     * Десериализует JSON строку в сообщение
     */
    inline fun <reified T> deserialize(message: String): Pair<String, T> {
        val envelope = json.decodeFromString(MessageEnvelope.serializer(), message)
        val data = json.decodeFromString<T>(envelope.data)
        return Pair(envelope.type, data)
    }
    
    /**
     * Парсит тип сообщения без полной десериализации
     */
    fun parseType(message: String): String {
        val envelope = json.decodeFromString(MessageEnvelope.serializer(), message)
        return envelope.type
    }
}
